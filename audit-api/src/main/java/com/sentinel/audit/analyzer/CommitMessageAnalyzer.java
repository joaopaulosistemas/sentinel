package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.structure.CommitMessages;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Mensagens de commit: aponta um achado só quando a parte dos commits fora do padrão Conventional Commits passa do limite.
 * Sem histórico suficiente (clone raso ou pasta sem Git) não aponta nada. Limites do perfil: {@code commit-min-commits} (20)
 * e {@code commit-max-bad-percent} (30).
 */
@ApplicationScoped
public class CommitMessageAnalyzer implements CodeAnalyzer {

    static final String ANALYZER_NAME = "Mensagens de commit";
    static final String RULE_ID = "sentinel-commit-fora-do-padrao";
    private static final String HISTORY = "(histórico do Git)";
    private static final int DEFAULT_MIN_COMMITS = 20;
    private static final int DEFAULT_MAX_BAD_PERCENT = 30;
    private static final int EXAMPLES = 5;
    private static final int GIT_TIMEOUT_SECONDS = 30;
    private static final Logger LOG = Logger.getLogger(CommitMessageAnalyzer.class);

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        int minCommits = policy == null ? DEFAULT_MIN_COMMITS : policy.threshold("commit-min-commits", DEFAULT_MIN_COMMITS);
        int maxBad = policy == null ? DEFAULT_MAX_BAD_PERCENT : policy.threshold("commit-max-bad-percent", DEFAULT_MAX_BAD_PERCENT);
        try {
            CommitMessages.Report report = CommitMessages.check(CommitMessages.parse(gitLog(projectPath)), EXAMPLES);
            if (report.checked() >= minCommits) {
                findings = toFindings(auditRun, report, maxBad);
            } else {
                LOG.infof("Mensagens de commit: só %d commits avaliáveis (mínimo %d), nada a apontar", report.checked(), minCommits);
            }
        } catch (Exception e) {
            LOG.warnf("Mensagens de commit não puderam ser verificadas: %s", e.getMessage());
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    static List<Finding> toFindings(AuditRun run, CommitMessages.Report report, int maxBadPercent) {
        if (report.percent() <= maxBadPercent) {
            return List.of();
        }
        String title = report.percent() + "% dos commits recentes estão fora do padrão de mensagem";
        String description = report.bad() + " de " + report.checked() + " commits não seguem tipo(escopo): descrição, ou têm "
                + "descrição vazia ou primeira linha com mais de " + CommitMessages.MAX_SUBJECT + " caracteres. Exemplos: "
                + String.join("; ", report.examples()) + ". A primeira linha é o que aparece em git log e em releases: "
                + "ela precisa dizer o que mudou.";
        return List.of(StructuralFindings.make(run,
                new StructuralFindings.Kind(ANALYZER_NAME, RULE_ID, Severity.LOW, Category.QUALITY),
                new StructuralFindings.Spot(HISTORY, 1, String.join("\n", report.examples())), title, description));
    }

    /** Assuntos dos últimos commits do clone, ou texto vazio se não houver Git ou histórico. */
    private static String gitLog(Path root) throws IOException, InterruptedException {
        Path out = Files.createTempFile("sentinel-commits-", ".txt");
        try {
            Process p = new ProcessBuilder("git", "-C", root.toString(), "log", "--no-merges", "--pretty=format:%H%x1f%s", "-n", "200")
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
        return true;
    }
}
