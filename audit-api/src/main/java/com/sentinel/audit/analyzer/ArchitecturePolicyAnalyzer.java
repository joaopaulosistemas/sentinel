package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.deps.Dependency;
import com.sentinel.audit.analyzer.deps.DependencyExtractor;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.ArchitecturePolicy;
import com.sentinel.audit.pack.ArchitecturePolicy.Convention;
import com.sentinel.audit.pack.ArchitecturePolicy.DepRule;
import com.sentinel.audit.pack.ArchitecturePolicy.Layer;
import com.sentinel.audit.pack.ArchitecturePolicy.LayerRule;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.Versions;
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
        checkComponents(root, pol, run, out);
        return out.size() > MAX_FINDINGS ? new ArrayList<>(out.subList(0, MAX_FINDINGS)) : out;
    }

    // ------------------------------------------------------------------ camadas

    private void checkLayers(String rel, String content, Compiled c, AuditRun run, List<Finding> out) {
        boolean java = rel.endsWith(".java");
        boolean ts = rel.matches(".*\\.(ts|tsx|js|jsx|mjs)$");
        if (!(java || ts) || c.layers.isEmpty() || (c.pol.forbid().isEmpty() && c.pol.allow().isEmpty())) {
            return;
        }
        String own = layerOfFile(rel, java ? declaredPackage(content) : null, c);
        if (own == null) {
            return;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String stripped = line.strip();
            if (stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*")) {
                continue;
            }
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
            for (String to : targets) {
                if (to != null && !to.equals(own)) {
                    judge(own, to, rel, i + 1, stripped, c, run, out);
                }
            }
        }
    }

    private void judge(String from, String to, String rel, int line, String snippet, Compiled c, AuditRun run, List<Finding> out) {
        for (LayerRule r : c.pol.forbid()) {
            if (r.from().equals(from) && r.to().equals(to)) {
                out.add(archFinding(run, rel, line, snippet, "sentinel-arch-layer-" + from + "-" + to,
                        r.severity() == null ? Severity.HIGH : r.severity(), Category.ARCHITECTURE,
                        "Camada '" + from + "' não deve depender de '" + to + "'",
                        r.message() == null ? "A política de arquitetura proíbe a dependência " + from + " -> " + to + "." : r.message()));
                return;
            }
        }
        if (c.pol.strict()) {
            for (LayerRule r : c.pol.allow()) {
                if (r.from().equals(from) && r.to().equals(to)) {
                    return;
                }
            }
            out.add(archFinding(run, rel, line, snippet, "sentinel-arch-layer-" + from + "-" + to, Severity.MEDIUM, Category.ARCHITECTURE,
                    "Dependência não permitida: '" + from + "' -> '" + to + "'",
                    "A política é estrita e não lista a dependência " + from + " -> " + to + " em 'allow'."));
        }
    }

    private static String declaredPackage(String content) {
        Matcher m = PACKAGE_DECL.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private String layerOfFile(String rel, String pkg, Compiled c) {
        for (Layer l : c.pol.layers()) {
            if (matchesAny(c.pathMatchers.get(l.name()), rel)) {
                return l.name();
            }
        }
        if (pkg != null) {
            for (Layer l : c.pol.layers()) {
                if (matchesAnyPackage(c.packagePatterns.get(l.name()), pkg + ".X") || matchesAnyPackage(c.packagePatterns.get(l.name()), pkg)) {
                    return l.name();
                }
            }
        }
        return null;
    }

    private String layerOfJavaImport(String fqn, Compiled c) {
        for (Layer l : c.pol.layers()) {
            if (matchesAnyPackage(c.packagePatterns.get(l.name()), fqn)) {
                return l.name();
            }
        }
        String asPath = fqn.replace('.', '/') + ".java";
        for (Layer l : c.pol.layers()) {
            if (matchesAny(c.pathMatchers.get(l.name()), asPath)) {
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
                    if (matchesAny(c.pathMatchers.get(l.name()), r + suf)) {
                        return l.name();
                    }
                }
            }
            return null;
        }
        for (Layer l : c.pol.layers()) {
            if (matchesAnyPackage(c.packagePatterns.get(l.name()), spec)) {
                return l.name();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ convenções

    private void checkConventions(String rel, String content, Compiled c, AuditRun run, List<Finding> out) {
        for (Convention cv : c.pol.conventions()) {
            if (!matchesAny(c.convFiles.get(cv.id()), rel) || matchesAny(c.convExclude.get(cv.id()), rel)) {
                continue;
            }
            Severity sev = cv.severity() == null ? Severity.MEDIUM : cv.severity();
            Category cat = cv.category() == null ? Category.ARCHITECTURE : cv.category();
            String title = cv.title() != null ? cv.title() : cv.id();
            String ruleId = "sentinel-arch-conv-" + cv.id();
            try {
                if (cv.require() != null && !Pattern.compile(cv.require(), Pattern.MULTILINE).matcher(limited(content)).find()) {
                    out.add(archFinding(run, rel, 1, "", ruleId, sev, cat, title,
                            cv.message() == null ? "O arquivo deveria conter /" + cv.require() + "/." : cv.message()));
                }
                if (cv.forbid() != null) {
                    Matcher m = Pattern.compile(cv.forbid(), Pattern.MULTILINE).matcher(limited(content));
                    int n = 0;
                    while (m.find() && n < MAX_MATCHES_PER_CONVENTION_FILE) {
                        n++;
                        int line = lineOf(content, m.start());
                        String text = m.group().strip();
                        out.add(archFinding(run, rel, line, text.length() > 200 ? text.substring(0, 200) : text, ruleId, sev, cat, title,
                                cv.message() == null ? "Trecho proibido pela convenção: /" + cv.forbid() + "/." : cv.message()));
                    }
                }
            } catch (RegexBudgetExceeded e) {
                LOG.warnf("Convenção '%s' abortada em %s: expressão regular muito lenta", cv.id(), rel);
            }
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
                out.add(archFinding(run, rf.path(), 0, "", "sentinel-arch-required-file",
                        rf.severity() == null ? Severity.LOW : rf.severity(), Category.ARCHITECTURE,
                        "Arquivo obrigatório ausente: " + rf.path(),
                        rf.message() == null ? "A política exige o arquivo " + rf.path() + "." : rf.message()));
            }
        }
    }

    // ------------------------------------------------------------------ componentes

    private void checkComponents(Path root, ArchitecturePolicy pol, AuditRun run, List<Finding> out) {
        ArchitecturePolicy.Components cp = pol.components();
        if (cp.forbidden().isEmpty() && cp.minVersion().isEmpty() && cp.required().isEmpty() && cp.approvedOnly().isEmpty()) {
            return;
        }
        List<Dependency> deps = DependencyExtractor.extract(root);
        for (Dependency d : deps) {
            checkForbidden(cp, d, run, out);
            checkMinVersion(cp, d, run, out);
        }
        checkRequired(cp, deps, run, out);
        checkApprovedOnly(cp, deps, run, out);
    }

    private static Severity severityOr(DepRule r, Severity fallback) {
        return r.severity() == null ? fallback : r.severity();
    }

    private void checkForbidden(ArchitecturePolicy.Components cp, Dependency d, AuditRun run, List<Finding> out) {
        for (DepRule r : cp.forbidden()) {
            if (globMatches(r.dep(), d.name())) {
                out.add(archFinding(run, d.filePath(), 0, d.name() + ":" + d.version(), "sentinel-comp-forbidden",
                        severityOr(r, Severity.HIGH), Category.DEPENDENCY,
                        "Biblioteca proibida: " + d.name(),
                        reason(r, "A política proíbe " + d.name() + ".") + " (versão " + d.version() + ")"));
            }
        }
    }

    private void checkMinVersion(ArchitecturePolicy.Components cp, Dependency d, AuditRun run, List<Finding> out) {
        for (DepRule r : cp.minVersion()) {
            if (globMatches(r.dep(), d.name()) && Boolean.FALSE.equals(Versions.satisfies(d.version(), r.version()))) {
                out.add(archFinding(run, d.filePath(), 0, d.name() + ":" + d.version(), "sentinel-comp-min-version",
                        severityOr(r, Severity.HIGH), Category.DEPENDENCY,
                        "Versão fora da política: " + d.name() + " " + d.version() + " (exigido " + r.version() + ")",
                        reason(r, "A política exige " + d.name() + " " + r.version() + ".")));
            }
        }
    }

    private void checkRequired(ArchitecturePolicy.Components cp, List<Dependency> deps, AuditRun run, List<Finding> out) {
        for (DepRule r : cp.required()) {
            if (deps.stream().noneMatch(d -> globMatches(r.dep(), d.name()))) {
                out.add(archFinding(run, "(dependências)", 0, "", "sentinel-comp-required",
                        severityOr(r, Severity.MEDIUM), Category.DEPENDENCY,
                        "Biblioteca obrigatória ausente: " + r.dep(), reason(r, "A política exige " + r.dep() + ".")));
            }
        }
    }

    /** Com lista aprovada, toda dependência declarada fora dela vira achado (no máximo 200). */
    private void checkApprovedOnly(ArchitecturePolicy.Components cp, List<Dependency> deps, AuditRun run, List<Finding> out) {
        if (cp.approvedOnly().isEmpty()) {
            return;
        }
        int n = 0;
        for (Dependency d : deps) {
            String f = d.filePath().toLowerCase(Locale.ROOT);
            if (f.endsWith("package-lock.json") || f.endsWith("yarn.lock") || f.endsWith("pnpm-lock.yaml")) {
                continue;                                   // transitivas: só declaradas diretamente
            }
            if (cp.approvedOnly().stream().noneMatch(r -> globMatches(r.dep(), d.name())) && n < 200) {
                n++;
                out.add(archFinding(run, d.filePath(), 0, d.name() + ":" + d.version(), "sentinel-comp-not-approved",
                        Severity.MEDIUM, Category.DEPENDENCY, "Biblioteca fora da lista aprovada: " + d.name(),
                        "A política só permite as bibliotecas aprovadas; " + d.name() + " não está na lista."));
            }
        }
    }

    private static String reason(DepRule r, String fallback) {
        return r.reason() == null || r.reason().isBlank() ? fallback : r.reason();
    }

    // ------------------------------------------------------------------ utilidades

    private static Finding archFinding(AuditRun run, String file, int line, String snippet, String ruleId, Severity sev,
                                       Category cat, String title, String description) {
        Finding f = new Finding();
        f.setAuditRun(run);
        f.setToolSource(ANALYZER_NAME);
        f.setCategory(cat);
        f.setSeverity(sev);
        f.setRuleId(ruleId);
        f.setTitle(title.length() > 500 ? title.substring(0, 497) + "..." : title);
        f.setDescription(description);
        f.setFilePath(file);
        f.setLineNumber(line);
        if (snippet != null && !snippet.isBlank()) {
            f.setCodeSnippet(snippet);
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

    private static boolean matchesAny(List<PathMatcher> ms, String rel) {
        if (ms == null) {
            return false;
        }
        Path p = Path.of(rel);
        Path prefixed = Path.of("_", rel);          // faz "**/x/**" casar também com "x/..." na raiz
        for (PathMatcher m : ms) {
            if (m.matches(p) || m.matches(prefixed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyPackage(List<Pattern> ps, String name) {
        if (ps == null) {
            return false;
        }
        for (Pattern p : ps) {
            if (p.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Glob simples ({@code *} = qualquer coisa), sem diferenciar maiúsculas. */
    static boolean globMatches(String glob, String value) {
        return toPattern(glob, true).matcher(value).matches();
    }

    private static Pattern toPattern(String glob, boolean ignoreCase) {
        String[] parts = glob.split("\\*", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            sb.append(Pattern.quote(parts[i]));
            if (i < parts.length - 1) {
                sb.append(".*");
            }
        }
        return Pattern.compile(sb.toString(), ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
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
                    ps.add(toPattern(p, false));
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
            int i = glob.indexOf("/**/");
            if (i < 0 || depth >= 3) {
                return List.of(glob);
            }
            List<String> out = new ArrayList<>();
            String with = glob.substring(0, i + 3) + glob.substring(i + 3);
            String without = glob.substring(0, i) + "/" + glob.substring(i + 4);
            for (String v : expand(without, depth + 1)) {
                out.add(v);
            }
            // mantém o original (uma ou mais pastas); procura o próximo "/**/" depois deste
            String head = with.substring(0, i + 4);
            for (String tail : expand(with.substring(i + 4), depth + 1)) {
                out.add(head + tail);
            }
            return out;
        }
    }
}
