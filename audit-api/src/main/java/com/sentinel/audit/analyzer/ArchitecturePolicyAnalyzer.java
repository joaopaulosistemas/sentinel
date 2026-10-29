package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.ArchitecturePolicy;
import com.sentinel.audit.pack.ArchitecturePolicy.Convention;
import com.sentinel.audit.pack.ArchitecturePolicy.Layer;
import com.sentinel.audit.pack.ArchitecturePolicy.LayerRule;
import com.sentinel.audit.pack.AuditPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aplica a política declarativa dos pacotes ({@code architecture.yml}) ao projeto auditado:
 * dependências proibidas entre camadas (imports Java/TypeScript), convenções por arquivo (regex exigida/proibida),
 * arquivos obrigatórios e política de bibliotecas (proibidas, versão mínima, obrigatórias, só aprovadas).
 * Não executa nada do projeto: só lê arquivos.
 */
@ApplicationScoped
public class ArchitecturePolicyAnalyzer implements CodeAnalyzer {

    private static final int SNIPPET_MAX_CHARS = 200;
    private static final String ELLIPSIS = "...";
    private static final int TITLE_MAX_CHARS = 500;
    /** Quantas vezes o {@code /**}{@code /} de um glob pode ser expandido (uma ou mais pastas / nenhuma). */
    private static final int MAX_GLOB_EXPANSION_DEPTH = 3;
    private static final String ANY_DIRS = "/**/";


    private static final Logger LOG = Logger.getLogger(ArchitecturePolicyAnalyzer.class);
    private static final String ANALYZER_NAME = "Arquitetura (pacotes)";
    private static final long MAX_FILE_BYTES = 1_000_000;
    private static final int MAX_FILES = 20_000;
    private static final int MAX_FINDINGS = 500;
    private static final int MAX_MATCHES_PER_CONVENTION_FILE = 20;
    private static final long REGEX_BUDGET_NANOS = 2_000_000_000L;

    private static final Pattern JAVA_IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+?)(?:\\.\\*)?\\s*;");
    private static final Pattern TS_IMPORT = Pattern.compile(
            "(?:\\b(?:import|export)\\s+(?:type\\s+)?(?:[^'\"\\n;]*?\\s+from\\s+)?|\\brequire\\(\\s*|\\bimport\\(\\s*)['\"]([^'\"\\n]+)['\"]");
    private static final Pattern PACKAGE_DECL = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final String[] TS_SUFFIXES = {"", ".ts", ".tsx", ".js", ".jsx", ".mjs", "/index.ts", "/index.tsx", "/index.js"};

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        ArchitecturePolicy arch = policy == null ? ArchitecturePolicy.EMPTY : policy.architecture();
        if (!arch.isEmpty()) {
            try {
                findings = check(projectPath, auditRun, arch);
            } catch (Exception e) {
                LOG.warnf("Política de arquitetura não pôde ser aplicada: %s", e.getMessage());
            }
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    @Override
    public boolean supports(Stack stack) {
        return true;
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    // ------------------------------------------------------------------ orquestração

    List<Finding> check(Path root, AuditRun run, ArchitecturePolicy pol) throws IOException {
        List<Finding> out = new ArrayList<>();
        Compiled c = new Compiled(pol);
        List<Path> files = collect(root);
        Set<String> relPaths = new java.util.HashSet<>();
        for (Path f : files) {
            relPaths.add(rel(root, f));
        }
        boolean needContent = !pol.layers().isEmpty() || !pol.conventions().isEmpty();
        if (needContent) {
            for (Path f : files) {
                if (out.size() >= MAX_FINDINGS) {
                    break;
                }
                String rel = rel(root, f);
                String content;
                try {
                    content = Files.readString(f, StandardCharsets.UTF_8);
                } catch (IOException | RuntimeException e) {
                    continue;                                  // binário ou codificação inválida
                }
                checkLayers(rel, content, c, run, out);
                checkConventions(rel, content, c, run, out);
            }
        }
        checkRequiredFiles(root, relPaths, pol, run, out);
        ComponentPolicyChecks.check(root, pol, run, out);
        return out.size() > MAX_FINDINGS ? new ArrayList<>(out.subList(0, MAX_FINDINGS)) : out;
    }

    // ------------------------------------------------------------------ camadas

    private void checkLayers(String rel, String content, Compiled c, AuditRun run, List<Finding> out) {
        boolean java = rel.endsWith(".java");
        if (!layersApply(rel, java, c)) {
            return;
        }
        String own = layerOfFile(rel, java ? declaredPackage(content) : null, c);
        if (own == null) {
            return;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String stripped = lines[i].strip();
            if (isCommentLine(stripped)) {
                continue;
            }
            for (String to : importTargets(lines[i], java, rel, c)) {
                if (to != null && !to.equals(own)) {
                    judge(new Edge(own, to), new ArchPlace(rel, i + 1, stripped), c, run, out);
                }
            }
        }
    }

    /** Só Java e TS/JS têm imports que as camadas conseguem ler, e só há o que julgar com camadas e regras definidas. */
    private static boolean layersApply(String rel, boolean java, Compiled c) {
        boolean ts = rel.matches(".*\\.(ts|tsx|js|jsx|mjs)$");
        return (java || ts) && !c.layers.isEmpty() && !(c.pol.forbid().isEmpty() && c.pol.allow().isEmpty());
    }

    private static boolean isCommentLine(String stripped) {
        return stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*");
    }

    /** Camadas dos módulos importados na linha (nulo quando o import não cai em nenhuma camada). */
    private List<String> importTargets(String line, boolean java, String rel, Compiled c) {
        List<String> targets = new ArrayList<>();
        if (java) {
            Matcher m = JAVA_IMPORT.matcher(line);
            if (m.find()) {
                targets.add(layerOfJavaImport(m.group(1), c));
            }
        } else {
            Matcher m = TS_IMPORT.matcher(line);
            while (m.find()) {
                targets.add(layerOfTsImport(rel, m.group(1), c));
            }
        }
        return targets;
    }

    /** Dependência de uma camada em outra. */
    private record Edge(String from, String to) { }

    private void judge(Edge edge, ArchPlace place, Compiled c, AuditRun run, List<Finding> out) {
        String from = edge.from();
        String to = edge.to();
        String ruleId = "sentinel-arch-layer-" + from + "-" + to;
        for (LayerRule r : c.pol.forbid()) {
            if (r.from().equals(from) && r.to().equals(to)) {
                out.add(archFinding(run, place, new ArchRule(ruleId,
                        r.severity() == null ? Severity.HIGH : r.severity(), Category.ARCHITECTURE,
                        "Camada '" + from + "' não deve depender de '" + to + "'",
                        r.message() == null ? "A política de arquitetura proíbe a dependência " + from + " -> " + to + "." : r.message())));
                return;
            }
        }
        if (c.pol.strict()) {
            for (LayerRule r : c.pol.allow()) {
                if (r.from().equals(from) && r.to().equals(to)) {
                    return;
                }
            }
            out.add(archFinding(run, place, new ArchRule(ruleId, Severity.MEDIUM, Category.ARCHITECTURE,
                    "Dependência não permitida: '" + from + "' -> '" + to + "'",
                    "A política é estrita e não lista a dependência " + from + " -> " + to + " em 'allow'.")));
        }
    }

    private static String declaredPackage(String content) {
        Matcher m = PACKAGE_DECL.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private String layerOfFile(String rel, String pkg, Compiled c) {
        for (Layer l : c.pol.layers()) {
            if (ArchGlobs.matchesAny(c.pathMatchers.get(l.name()), rel)) {
                return l.name();
            }
        }
        if (pkg != null) {
            for (Layer l : c.pol.layers()) {
                if (ArchGlobs.matchesAnyPackage(c.packagePatterns.get(l.name()), pkg + ".X") || ArchGlobs.matchesAnyPackage(c.packagePatterns.get(l.name()), pkg)) {
                    return l.name();
                }
            }
        }
        return null;
    }

    private String layerOfJavaImport(String fqn, Compiled c) {
        for (Layer l : c.pol.layers()) {
            if (ArchGlobs.matchesAnyPackage(c.packagePatterns.get(l.name()), fqn)) {
                return l.name();
            }
        }
        String asPath = fqn.replace('.', '/') + ".java";
        for (Layer l : c.pol.layers()) {
            if (ArchGlobs.matchesAny(c.pathMatchers.get(l.name()), asPath)) {
                return l.name();
            }
        }
        return null;
    }

    private String layerOfTsImport(String fromFile, String spec, Compiled c) {
        if (spec.startsWith(".")) {
            Path base = Path.of(fromFile).getParent();
            Path resolved = (base == null ? Path.of(spec) : base.resolve(spec)).normalize();
            String r = resolved.toString().replace('\\', '/');
            if (r.startsWith("..")) {
                return null;
            }
            for (Layer l : c.pol.layers()) {
                for (String suf : TS_SUFFIXES) {
                    if (ArchGlobs.matchesAny(c.pathMatchers.get(l.name()), r + suf)) {
                        return l.name();
                    }
                }
            }
            return null;
        }
        for (Layer l : c.pol.layers()) {
            if (ArchGlobs.matchesAnyPackage(c.packagePatterns.get(l.name()), spec)) {
                return l.name();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ convenções

    private void checkConventions(String rel, String content, Compiled c, AuditRun run, List<Finding> out) {
        for (Convention cv : c.pol.conventions()) {
            if (!ArchGlobs.matchesAny(c.convFiles.get(cv.id()), rel) || ArchGlobs.matchesAny(c.convExclude.get(cv.id()), rel)) {
                continue;
            }
            ConvCtx ctx = new ConvCtx(cv, rel, run);
            try {
                requireCheck(ctx, content, out);
                forbidCheck(ctx, content, out);
            } catch (RegexBudgetExceeded e) {
                LOG.warnf("Convenção '%s' abortada em %s: expressão regular muito lenta", cv.id(), rel);
            }
        }
    }

    /** Uma convenção aplicada a um arquivo: de onde vêm o id, a gravidade, a categoria e o título dos findings. */
    private record ConvCtx(Convention cv, String rel, AuditRun run) {
        ArchRule rule(String message) {
            return new ArchRule("sentinel-arch-conv-" + cv.id(),
                    cv.severity() == null ? Severity.MEDIUM : cv.severity(),
                    cv.category() == null ? Category.ARCHITECTURE : cv.category(),
                    cv.title() != null ? cv.title() : cv.id(), message);
        }
    }

    /** `require`: o arquivo precisa conter o padrão. */
    private void requireCheck(ConvCtx x, String content, List<Finding> out) {
        Convention cv = x.cv();
        if (cv.require() != null && !Pattern.compile(cv.require(), Pattern.MULTILINE).matcher(limited(content)).find()) {
            out.add(archFinding(x.run(), new ArchPlace(x.rel(), 1, ""),
                    x.rule(cv.message() == null ? "O arquivo deveria conter /" + cv.require() + "/." : cv.message())));
        }
    }

    /** `forbid`: cada trecho que casa com o padrão vira um finding (até o limite por arquivo). */
    private void forbidCheck(ConvCtx x, String content, List<Finding> out) {
        Convention cv = x.cv();
        if (cv.forbid() == null) {
            return;
        }
        Matcher m = Pattern.compile(cv.forbid(), Pattern.MULTILINE).matcher(limited(content));
        int n = 0;
        while (m.find() && n < MAX_MATCHES_PER_CONVENTION_FILE) {
            n++;
            String text = m.group().strip();
            String snippet = text.length() > SNIPPET_MAX_CHARS ? text.substring(0, SNIPPET_MAX_CHARS) : text;
            out.add(archFinding(x.run(), new ArchPlace(x.rel(), lineOf(content, m.start()), snippet),
                    x.rule(cv.message() == null ? "Trecho proibido pela convenção: /" + cv.forbid() + "/." : cv.message())));
        }
    }

    /** Aborta a busca se a regex consumir tempo demais (proteção contra backtracking catastrófico em pacote de terceiros). */
    private static CharSequence limited(CharSequence s) {
        long deadline = System.nanoTime() + REGEX_BUDGET_NANOS;
        return new CharSequence() {
            @Override public int length() { return s.length(); }
            @Override public char charAt(int i) {
                if (System.nanoTime() > deadline) {
                    throw new RegexBudgetExceeded();
                }
                return s.charAt(i);
            }
            @Override public CharSequence subSequence(int a, int b) { return limited(s.subSequence(a, b)); }
            @Override public String toString() { return s.toString(); }
        };
    }

    private static final class RegexBudgetExceeded extends RuntimeException {
        RegexBudgetExceeded() { super(null, null, false, false); }
    }

    // ------------------------------------------------------------------ arquivos obrigatórios

    private void checkRequiredFiles(Path root, Set<String> relPaths, ArchitecturePolicy pol, AuditRun run, List<Finding> out) {
        for (ArchitecturePolicy.RequiredFile rf : pol.requiredFiles()) {
            boolean exists;
            if (rf.path().contains("*")) {
                PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + rf.path());
                exists = relPaths.stream().anyMatch(p -> m.matches(Path.of(p)));
            } else {
                exists = relPaths.contains(rf.path()) || Files.exists(root.resolve(rf.path()), LinkOption.NOFOLLOW_LINKS);
            }
            if (!exists) {
                out.add(archFinding(run, new ArchPlace(rf.path(), 0, ""), new ArchRule("sentinel-arch-required-file",
                        rf.severity() == null ? Severity.LOW : rf.severity(), Category.ARCHITECTURE,
                        "Arquivo obrigatório ausente: " + rf.path(),
                        rf.message() == null ? "A política exige o arquivo " + rf.path() + "." : rf.message())));
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    /** Onde o problema está. */
    record ArchPlace(String file, int line, String snippet) { }

    /** Qual regra foi violada e como descrevê-la. */
    record ArchRule(String ruleId, Severity severity, Category category, String title, String description) { }

    static Finding archFinding(AuditRun run, ArchPlace place, ArchRule rule) {
        String title = rule.title();
        Finding f = new Finding();
        f.setAuditRun(run);
        f.setToolSource(ANALYZER_NAME);
        f.setCategory(rule.category());
        f.setSeverity(rule.severity());
        f.setRuleId(rule.ruleId());
        f.setTitle(title.length() > TITLE_MAX_CHARS ? title.substring(0, TITLE_MAX_CHARS - ELLIPSIS.length()) + ELLIPSIS : title);
        f.setDescription(rule.description());
        f.setFilePath(place.file());
        f.setLineNumber(place.line());
        if (place.snippet() != null && !place.snippet().isBlank()) {
            f.setCodeSnippet(place.snippet());
        }
        return f;
    }

    private static int lineOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String rel(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private List<Path> collect(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && (CleanCodeAnalyzer.IGNORED_DIRS.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))
                        || dir.getFileName().toString().equals(".git"))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !attrs.isSymbolicLink() && attrs.size() <= MAX_FILE_BYTES && files.size() < MAX_FILES) {
                    files.add(file);
                }
                return files.size() >= MAX_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    /** Globs já compilados da política. */
    private static final class Compiled {
        final ArchitecturePolicy pol;
        final List<Layer> layers;
        final Map<String, List<PathMatcher>> pathMatchers = new HashMap<>();
        final Map<String, List<Pattern>> packagePatterns = new HashMap<>();
        final Map<String, List<PathMatcher>> convFiles = new HashMap<>();
        final Map<String, List<PathMatcher>> convExclude = new HashMap<>();

        Compiled(ArchitecturePolicy pol) {
            this.pol = pol;
            this.layers = pol.layers();
            for (Layer l : pol.layers()) {
                pathMatchers.put(l.name(), matchers(l.paths()));
                List<Pattern> ps = new ArrayList<>();
                for (String p : l.packages() == null ? List.<String>of() : l.packages()) {
                    ps.add(ArchGlobs.toPattern(p, false));
                }
                packagePatterns.put(l.name(), ps);
            }
            for (Convention cv : pol.conventions()) {
                convFiles.put(cv.id(), matchers(cv.files()));
                convExclude.put(cv.id(), matchers(cv.exclude()));
            }
        }

        private static List<PathMatcher> matchers(List<String> globs) {
            List<PathMatcher> out = new ArrayList<>();
            for (String g : globs == null ? List.<String>of() : globs) {
                for (String variant : expand(g, 0)) {
                    out.add(FileSystems.getDefault().getPathMatcher("glob:" + variant));
                }
            }
            return out;
        }

        /** No glob do Java, {@code a/**}{@code /b} exige ao menos uma pasta no meio; aqui {@code **}{@code /} também vale para zero pastas. */
        private static List<String> expand(String glob, int depth) {
            int i = glob.indexOf(ANY_DIRS);
            if (i < 0 || depth >= MAX_GLOB_EXPANSION_DEPTH) {
                return List.of(glob);
            }
            List<String> out = new ArrayList<>();
            String with = glob.substring(0, i + ANY_DIRS.length() - 1) + glob.substring(i + ANY_DIRS.length() - 1);
            String without = glob.substring(0, i) + "/" + glob.substring(i + ANY_DIRS.length());
            for (String v : expand(without, depth + 1)) {
                out.add(v);
            }
            // mantém o original (uma ou mais pastas); procura o próximo "/**/" depois deste
            String head = with.substring(0, i + ANY_DIRS.length());
            for (String tail : expand(with.substring(i + ANY_DIRS.length()), depth + 1)) {
                out.add(head + tail);
            }
            return out;
        }
    }
}
