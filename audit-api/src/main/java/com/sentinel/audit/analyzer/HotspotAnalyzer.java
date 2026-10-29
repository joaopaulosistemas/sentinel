package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.quality.FunctionScanner;
import com.sentinel.audit.analyzer.quality.FunctionScanner.Fn;
import com.sentinel.audit.analyzer.quality.SourceSanitizer;
import com.sentinel.audit.analyzer.structure.Hotspots;
import com.sentinel.audit.analyzer.structure.Hotspots.Cx;
import com.sentinel.audit.analyzer.structure.Hotspots.Hotspot;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.PathRole;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pontos quentes: arquivos que mudam muito e têm função complexa (churn × complexidade). Lê o histórico do clone
 * ({@code git log}); sem histórico suficiente (clone raso ou pasta sem Git) não aponta nada. Limites do perfil:
 * {@code hotspot-min-commits} (20), {@code hotspot-min-churn} (5), {@code hotspot-min-complexity} (15) e
 * {@code hotspot-max} (15).
 */
@ApplicationScoped
public class HotspotAnalyzer implements CodeAnalyzer {

    private static final int DEFAULT_MIN_COMMITS = 20;
    private static final int DEFAULT_MIN_CHURN = 5;
    private static final int DEFAULT_MIN_COMPLEXITY = 15;
    private static final int DEFAULT_MAX_HOTSPOTS = 15;
    /** Complexidade a partir da qual um ponto quente muito mexido vira alto. */
    private static final int HIGH_COMPLEXITY = 30;
    private static final int GIT_TIMEOUT_SECONDS = 30;


    private static final Logger LOG = Logger.getLogger(HotspotAnalyzer.class);
    static final String ANALYZER_NAME = "Pontos quentes";
    private static final long MAX_FILE_BYTES = 1024 * 1024;

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        int minCommits = policy == null ? DEFAULT_MIN_COMMITS : policy.threshold("hotspot-min-commits", DEFAULT_MIN_COMMITS);
        int minChurn = policy == null ? DEFAULT_MIN_CHURN : policy.threshold("hotspot-min-churn", DEFAULT_MIN_CHURN);
        int minCx = policy == null ? DEFAULT_MIN_COMPLEXITY : policy.threshold("hotspot-min-complexity", DEFAULT_MIN_COMPLEXITY);
        int top = policy == null ? DEFAULT_MAX_HOTSPOTS : policy.threshold("hotspot-max", DEFAULT_MAX_HOTSPOTS);
        try {
            Hotspots.Log log = Hotspots.parseLog(gitLog(projectPath));
            if (log.commits() >= minCommits) {
                Map<String, Cx> cx = complexityOf(projectPath, log.churn(), minChurn);
                findings = toFindings(auditRun, Hotspots.rank(log.churn(), cx, minChurn, minCx, top), log.commits());
            } else {
                LOG.infof("Pontos quentes: só %d commits no clone (mínimo %d), nada a apontar", log.commits(), minCommits);
            }
        } catch (Exception e) {
            LOG.warnf("Pontos quentes não puderam ser verificados: %s", e.getMessage());
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    static List<Finding> toFindings(AuditRun run, List<Hotspot> hotspots, int commits) {
        List<Finding> out = new ArrayList<>();
        for (Hotspot h : hotspots) {
            Severity sev = h.churn() >= 10 && h.cx().complexity() >= HIGH_COMPLEXITY ? Severity.HIGH : Severity.MEDIUM;
            out.add(StructuralFindings.make(run, new StructuralFindings.Kind(ANALYZER_NAME, "sentinel-hotspot", sev, Category.QUALITY), new StructuralFindings.Spot(h.file(), h.cx().line(), h.cx().function()), "Ponto quente: " + h.file().substring(h.file().lastIndexOf('/') + 1) + " mudou " + h.churn()
                            + " vezes e tem função de complexidade " + h.cx().complexity(), "Nos últimos " + commits + " commits este arquivo foi alterado " + h.churn() + " vezes e a função "
                            + h.cx().function() + " tem complexidade ciclomática " + h.cx().complexity() + ". Complexidade num "
                            + "arquivo que muda sempre custa a cada mudança: é onde o investimento em refatorar e testar rende mais."));
        }
        return out;
    }

    private static Map<String, Cx> complexityOf(Path root, Map<String, Integer> churn, int minChurn) {
        Map<String, Cx> out = new HashMap<>();
        for (Map.Entry<String, Integer> e : churn.entrySet()) {
            String rel = e.getKey();
            String lower = rel.toLowerCase(Locale.ROOT);
            boolean ts = lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".js") || lower.endsWith(".jsx");
            if (e.getValue() < minChurn || !(lower.endsWith(".java") || ts) || PathRole.of(rel).isNonProduction()) {
                continue;
            }
            Cx worst = worstComplexity(root, root.resolve(rel).normalize(), ts);
            if (worst != null) {
                out.put(rel, worst);
            }
        }
        return out;
    }

    /** A função mais complexa do arquivo; nulo se ele sumiu, é link, é grande demais, não é texto ou não tem função. */
    private static Cx worstComplexity(Path root, Path file, boolean ts) {
        try {
            if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)
                    || Files.size(file) > MAX_FILE_BYTES) {
                return null;                                    // apagado depois, link ou grande demais
            }
            String clean = SourceSanitizer.strip(Files.readString(file, StandardCharsets.UTF_8));
            Fn worst = null;
            for (Fn fn : FunctionScanner.scan(clean, ts)) {
                if (worst == null || fn.complexity() > worst.complexity()) {
                    worst = fn;
                }
            }
            return worst == null ? null : new Cx(worst.complexity(), worst.name(), worst.startLine());
        } catch (IOException ignored) {
            return null;                                        // binário ou codificação inesperada: ignora o arquivo
        }
    }

    /** Saída de {@code git log} do clone, ou texto vazio se não houver Git ou histórico. */
    private static String gitLog(Path root) throws IOException, InterruptedException {
        Path out = Files.createTempFile("sentinel-gitlog-", ".txt");
        try {
            Process p = new ProcessBuilder("git", "-C", root.toString(), "log", "--name-only", "--no-merges",
                    "--pretty=format:%H", "-n", "500")
                    .redirectErrorStream(true).redirectOutput(out.toFile()).start();
            if (!p.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            return p.exitValue() == 0 ? Files.readString(out, StandardCharsets.UTF_8) : "";
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return stack == Stack.JAVA || stack == Stack.JAVASCRIPT || stack == Stack.MIXED;
    }
}
