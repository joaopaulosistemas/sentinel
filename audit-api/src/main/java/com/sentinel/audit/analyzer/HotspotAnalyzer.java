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
        int minCommits = policy == null ? 20 : policy.threshold("hotspot-min-commits", 20);
        int minChurn = policy == null ? 5 : policy.threshold("hotspot-min-churn", 5);
        int minCx = policy == null ? 15 : policy.threshold("hotspot-min-complexity", 15);
        int top = policy == null ? 15 : policy.threshold("hotspot-max", 15);
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
            Severity sev = h.churn() >= 10 && h.cx().complexity() >= 30 ? Severity.HIGH : Severity.MEDIUM;
            out.add(StructuralFindings.make(run, ANALYZER_NAME, "sentinel-hotspot", sev, Category.QUALITY,
                    h.file(), h.cx().line(),
                    "Ponto quente: " + h.file().substring(h.file().lastIndexOf('/') + 1) + " mudou " + h.churn()
                            + " vezes e tem função de complexidade " + h.cx().complexity(),
                    "Nos últimos " + commits + " commits este arquivo foi alterado " + h.churn() + " vezes e a função "
                            + h.cx().function() + " tem complexidade ciclomática " + h.cx().complexity() + ". Complexidade num "
                            + "arquivo que muda sempre custa a cada mudança: é onde o investimento em refatorar e testar rende mais.",
                    h.cx().function()));
        }
        return out;
    }

    private static Map<String, Cx> complexityOf(Path root, Map<String, Integer> churn, int minChurn) {
        Map<String, Cx> out = new HashMap<>();
        for (Map.Entry<String, Integer> e : churn.entrySet()) {
            String rel = e.getKey();
            String lower = rel.toLowerCase(Locale.ROOT);
            boolean java = lower.endsWith(".java");
            boolean ts = lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".js") || lower.endsWith(".jsx");
            if (e.getValue() < minChurn || !(java || ts) || PathRole.of(rel).isNonProduction()) {
                continue;
            }
            Path file = root.resolve(rel).normalize();
            try {
                if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)
                        || Files.size(file) > MAX_FILE_BYTES) {
                    continue;                                   // apagado depois, link ou grande demais
                }
                String clean = SourceSanitizer.strip(Files.readString(file, StandardCharsets.UTF_8));
                Fn worst = null;
                for (Fn fn : FunctionScanner.scan(clean, ts)) {
                    if (worst == null || fn.complexity() > worst.complexity()) {
                        worst = fn;
                    }
                }
                if (worst != null) {
                    out.put(rel, new Cx(worst.complexity(), worst.name(), worst.startLine()));
                }
            } catch (IOException ignored) {
                // binário ou codificação inesperada: ignora o arquivo
            }
        }
        return out;
    }

    /** Saída de {@code git log} do clone, ou texto vazio se não houver Git ou histórico. */
    private static String gitLog(Path root) throws IOException, InterruptedException {
        Path out = Files.createTempFile("sentinel-gitlog-", ".txt");
        try {
            Process p = new ProcessBuilder("git", "-C", root.toString(), "log", "--name-only", "--no-merges",
                    "--pretty=format:%H", "-n", "500")
                    .redirectErrorStream(true).redirectOutput(out.toFile()).start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
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
