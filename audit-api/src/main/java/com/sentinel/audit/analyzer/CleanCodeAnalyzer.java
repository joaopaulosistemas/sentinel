package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.quality.FunctionScanner;
import com.sentinel.audit.analyzer.quality.FunctionScanner.Fn;
import com.sentinel.audit.analyzer.quality.SourceSanitizer;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Métricas de clean code em Java e TypeScript/JavaScript: função longa, parâmetros demais, complexidade
 * ciclomática alta e aninhamento profundo.
 * <p>
 * Complementa o Semgrep (que acha padrões, mas não mede) e o {@link ArchitectureAnalyzer} (God Object por arquivo).
 * Os limites são configuráveis ({@code sentinel.audit.cleancode.*}); o relatório fica limitado às piores
 * ocorrências ({@code max-findings}) para não inundar o painel de um sistema legado.
 * <p>
 * É uma aproximação por análise textual, não um parser completo. Código que não consegue interpretar é ignorado.
 */
@ApplicationScoped
public class CleanCodeAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(CleanCodeAnalyzer.class);
    private static final String ANALYZER_NAME = "CleanCode";
    private static final long MAX_FILE_BYTES = 1024 * 1024;

    static final Set<String> IGNORED_DIRS = Set.of(
            "target", "build", "dist", "out", "node_modules", ".git", ".sentinel", ".idea", ".gradle", "vendor", "coverage",
            "test", "tests", "__tests__", "generated", "generated-sources", "e2e");
    private static final Set<String> JAVA_EXT = Set.of(".java");
    private static final Set<String> TS_EXT = Set.of(".ts", ".tsx", ".js", ".jsx", ".mjs");

    @ConfigProperty(name = "sentinel.audit.cleancode.enabled", defaultValue = "true")
    boolean enabled = true;
    @ConfigProperty(name = "sentinel.audit.cleancode.max-function-lines", defaultValue = "50")
    int maxLines = 50;
    /** Componentes React concentram JSX e costumam ser mais longos: limite próprio para .tsx/.jsx. */
    @ConfigProperty(name = "sentinel.audit.cleancode.max-component-lines", defaultValue = "100")
    int maxLinesJsx = 100;
    @ConfigProperty(name = "sentinel.audit.cleancode.max-parameters", defaultValue = "5")
    int maxParams = 5;
    @ConfigProperty(name = "sentinel.audit.cleancode.max-complexity", defaultValue = "10")
    int maxComplexity = 10;
    @ConfigProperty(name = "sentinel.audit.cleancode.max-nesting", defaultValue = "4")
    int maxNesting = 4;
    @ConfigProperty(name = "sentinel.audit.cleancode.max-findings", defaultValue = "300")
    int maxFindings = 300;

    /** Limites efetivos de uma auditoria (imutáveis: o analisador é compartilhado entre auditorias simultâneas). */
    record Limits(int lines, int linesJsx, int params, int complexity, int nesting, int maxFindings) {}

    /** Limites do perfil quando houver; senão os configurados na aplicação. */
    Limits limits(AuditPolicy policy) {
        if (policy == null) {
            return new Limits(maxLines, maxLinesJsx, maxParams, maxComplexity, maxNesting, maxFindings);
        }
        return new Limits(policy.threshold("max-function-lines", maxLines),
                policy.threshold("max-component-lines", maxLinesJsx),
                policy.threshold("max-parameters", maxParams),
                policy.threshold("max-complexity", maxComplexity),
                policy.threshold("max-nesting", maxNesting),
                policy.threshold("max-findings", maxFindings));
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        if (!enabled) {
            return new AnalyzerResult(findings, ANALYZER_NAME, Duration.ZERO);
        }
        Limits lim = limits(policy);
        try {
            for (Path file : collectSources(projectPath)) {
                findings.addAll(analyzeFile(projectPath, file, auditRun, lim));
            }
        } catch (IOException e) {
            LOG.warnf("Erro ao percorrer o projeto para clean code: %s", e.getMessage());
        }

        int total = findings.size();
        if (total > lim.maxFindings()) {
            findings.sort(Comparator.comparing(Finding::getSeverity)
                    .thenComparing(Comparator.comparingInt(CleanCodeAnalyzer::magnitude).reversed()));
            findings = new ArrayList<>(findings.subList(0, lim.maxFindings()));
            LOG.infof("CleanCode: %d ocorrências, mantidas as %d piores", total, lim.maxFindings());
        }
        LOG.infof("CleanCode encontrou %d findings", findings.size());
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return stack == Stack.JAVA || stack == Stack.JAVASCRIPT || stack == Stack.MIXED;
    }

    // ------------------------------------------------------------------ análise de um arquivo

    List<Finding> analyzeFile(Path root, Path file, AuditRun run) {
        return analyzeFile(root, file, run, limits(null));
    }

    List<Finding> analyzeFile(Path root, Path file, AuditRun run, Limits lim) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean ts = TS_EXT.stream().anyMatch(name::endsWith);
        boolean jsx = name.endsWith(".tsx") || name.endsWith(".jsx");
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();                                        // binário ou codificação inesperada
        }
        return analyzeSource(root.relativize(file).toString(), source, ts, jsx, run, lim);
    }

    List<Finding> analyzeSource(String relPath, String source, boolean ts, boolean jsx, AuditRun run) {
        return analyzeSource(relPath, source, ts, jsx, run, limits(null));
    }

    List<Finding> analyzeSource(String relPath, String source, boolean ts, boolean jsx, AuditRun run, Limits lim) {
        String clean = SourceSanitizer.strip(source);
        String[] originalLines = source.split("\n", -1);
        int lineLimit = jsx ? lim.linesJsx() : lim.lines();

        List<Finding> out = new ArrayList<>();
        for (Fn fn : FunctionScanner.scan(clean, ts)) {
            String signature = signatureLine(originalLines, fn.startLine());

            if (fn.codeLines() > lineLimit) {
                Severity sev = fn.codeLines() > lineLimit * 2 ? Severity.MEDIUM : Severity.LOW;
                out.add(finding(run, relPath, fn, signature, "sentinel-cc-funcao-longa", sev,
                        String.format("Função longa: %s tem %d linhas", fn.name(), fn.codeLines()),
                        String.format("%s tem %d linhas de código (limite recomendado: %d). Funções longas fazem várias "
                                + "coisas, são difíceis de testar e de ler. Extraia trechos coesos em funções com nome "
                                + "que diga o que fazem.", fn.name(), fn.codeLines(), lineLimit)));
            }
            if (fn.paramCount() > lim.params()) {
                Severity sev = fn.paramCount() > lim.params() + 3 ? Severity.MEDIUM : Severity.LOW;
                out.add(finding(run, relPath, fn, signature, "sentinel-cc-muitos-parametros", sev,
                        String.format("Muitos parâmetros: %s recebe %d", fn.name(), fn.paramCount()),
                        String.format("%s recebe %d parâmetros (limite recomendado: %d). Chamadas ficam difíceis de ler e "
                                + "é fácil trocar a ordem. Agrupe os que andam juntos em um objeto/record ou use um builder.",
                                fn.name(), fn.paramCount(), lim.params())));
            }
            if (fn.complexity() > lim.complexity()) {
                Severity sev = fn.complexity() > lim.complexity() * 2 ? Severity.HIGH : Severity.MEDIUM;
                out.add(finding(run, relPath, fn, signature, "sentinel-cc-complexidade", sev,
                        String.format("Complexidade alta: %s tem complexidade ciclomática %d", fn.name(), fn.complexity()),
                        String.format("Complexidade ciclomática %d em %s (limite recomendado: %d). Cada decisão (if, laço, "
                                + "case, &&, ||, ?:) multiplica os caminhos a testar. Simplifique com retornos antecipados, "
                                + "extraia condições com nome e substitua cadeias de if/switch por polimorfismo ou mapa.",
                                fn.complexity(), fn.name(), lim.complexity())));
            }
            if (fn.nesting() > lim.nesting()) {
                Severity sev = fn.nesting() > lim.nesting() + 2 ? Severity.MEDIUM : Severity.LOW;
                out.add(finding(run, relPath, fn, signature, "sentinel-cc-aninhamento", sev,
                        String.format("Aninhamento profundo: %s chega a %d níveis", fn.name(), fn.nesting()),
                        String.format("%s aninha blocos em %d níveis (limite recomendado: %d). Use cláusulas de guarda "
                                + "(retorne cedo) e extraia o miolo dos laços para funções próprias.",
                                fn.name(), fn.nesting(), lim.nesting())));
            }
        }
        return out;
    }

    private Finding finding(AuditRun run, String file, Fn fn, String signature, String ruleId, Severity severity,
                            String title, String description) {
        Finding f = new Finding();
        f.setAuditRun(run);
        f.setToolSource(ANALYZER_NAME);
        f.setCategory(Category.QUALITY);
        f.setSeverity(severity);
        f.setRuleId(ruleId);
        f.setTitle(title);
        f.setDescription(description);
        f.setFilePath(file);
        f.setLineNumber(fn.startLine());
        f.setCodeSnippet(signature);        // identidade estável: não muda quando o corpo cresce ou a função se move
        return f;
    }

    /** Peso de uma ocorrência, só para escolher as piores quando há excesso. */
    private static int magnitude(Finding f) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(f.getTitle());
        int last = 0;
        while (m.find()) {
            last = Integer.parseInt(m.group(1));
        }
        return last;
    }

    private static String signatureLine(String[] lines, int line) {
        String s = line >= 1 && line <= lines.length ? lines[line - 1].strip() : "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    // ------------------------------------------------------------------ varredura de arquivos

    private List<Path> collectSources(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && IGNORED_DIRS.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !attrs.isSymbolicLink() && attrs.size() <= MAX_FILE_BYTES
                        && isSourceFile(file.getFileName().toString())
                        && !ExternalTools.isVendoredLibrary(root.relativize(file).toString())
                        && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    /** Arquivo de código de produção. Compara o nome original: "Audit.java" não é teste, "AuditIT.java" é. */
    static boolean isSourceFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".d.ts") || lower.endsWith(".min.js") || lower.contains(".spec.") || lower.contains(".test.")
                || name.endsWith("Test.java") || name.endsWith("Tests.java") || name.endsWith("IT.java")) {
            return false;
        }
        return JAVA_EXT.stream().anyMatch(lower::endsWith) || TS_EXT.stream().anyMatch(lower::endsWith);
    }
}
