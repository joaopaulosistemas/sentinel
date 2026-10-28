package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Procura segredos que existiram em <b>commits antigos</b> e já foram removidos da versão atual.
 * <p>
 * Uma senha apagada num commit posterior continua exposta no histórico (e deve ser revogada).
 * Segredos que ainda estão na versão atual já são reportados pelo {@link SecretsAnalyzer} e aqui são ignorados.
 * Varre os commits que vieram no clone; quantos vêm é definido no cadastro do sistema (histórico de commits).
 */
@ApplicationScoped
public class HistorySecretsAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(HistorySecretsAnalyzer.class);
    private static final String ANALYZER_NAME = "Secrets-History";
    private static final int MAX_FINDINGS = 200;
    private static final long MAX_LOG_BYTES = 200L * 1024 * 1024;

    @ConfigProperty(name = "sentinel.audit.git.history-timeout-seconds", defaultValue = "120")
    long timeoutSeconds;

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        if (Files.isDirectory(projectPath.resolve(".git")) && commitCount(projectPath) > 1) {
            try {
                findings = scanHistory(projectPath, auditRun);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warnf("Varredura do histórico falhou: %s", e.getMessage());
            }
        }
        LOG.infof("Secrets-History encontrou %d findings", findings.size());
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return true;
    }

    /** Commits disponíveis no clone: 1 = clone raso (histórico desligado no cadastro do sistema), nada a varrer. */
    private int commitCount(Path repo) {
        try {
            Process p = new ProcessBuilder("git", "-C", repo.toString(), "rev-list", "--count", "HEAD")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0 ? Integer.parseInt(out) : 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    private List<Finding> scanHistory(Path repo, AuditRun auditRun) throws IOException, InterruptedException {
        Path log = Files.createTempFile("git-history-", ".log");
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "-C", repo.toString(), "log",
                    "-p", "-U0", "--no-color", "--no-ext-diff", "--no-textconv", "--diff-filter=AM",
                    "--format=@@commit %H", "--");
            pb.redirectOutput(log.toFile());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process process = pb.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warnf("git log excedeu %ds; histórico ignorado", timeoutSeconds);
                return List.of();
            }
            if (process.exitValue() != 0 || Files.size(log) > MAX_LOG_BYTES) {
                return List.of();
            }
            return parse(log, repo, auditRun);
        } finally {
            Files.deleteIfExists(log);
        }
    }

    /** Lê a saída de {@code git log -p} e devolve segredos adicionados que não existem mais na versão atual. */
    List<Finding> parse(Path logFile, Path repo, AuditRun auditRun) throws IOException {
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String commit = null;
        String path = null;
        boolean scan = false;
        int line = 0;
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.ISO_8859_1)) {
            String l = reader.readLine();
            while (l != null && findings.size() < MAX_FINDINGS) {
                if (l.startsWith("@@commit ")) {
                    commit = l.substring(9).trim();
                } else if (l.startsWith("+++ ")) {
                    path = diffPath(l);
                    scan = path != null && SecretsAnalyzer.isScannablePath(path);
                } else if (l.startsWith("@@ ")) {
                    line = hunkStart(l);
                } else if (scan && l.startsWith("+") && !l.startsWith("+++")) {
                    String added = l.substring(1);
                    for (SecretsAnalyzer.SecretMatch m : SecretsAnalyzer.findSecrets(added)) {
                        String key = path + "|" + m.value();
                        if (seen.add(key) && !stillPresent(repo, path, m.value())) {
                            findings.add(build(path, line, commit, added, m, auditRun));
                        }
                    }
                    line++;
                }
                l = reader.readLine();
            }
        }
        return findings;
    }

    /** Caminho do arquivo novo no cabeçalho {@code +++ b/...} do diff; nulo para {@code /dev/null}. */
    private static String diffPath(String header) {
        return header.startsWith("+++ b/") ? header.substring(6) : null;
    }

    private static int hunkStart(String header) {
        int plus = header.indexOf('+');
        if (plus < 0) {
            return 0;
        }
        int end = plus + 1;
        while (end < header.length() && Character.isDigit(header.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(header.substring(plus + 1, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** O segredo continua no arquivo atual? Então o SecretsAnalyzer já o reportou. */
    private static boolean stillPresent(Path repo, String relPath, String value) {
        try {
            Path file = repo.resolve(relPath).normalize();
            if (!file.startsWith(repo) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)
                    || Files.size(file) > 1024 * 1024) {
                return false;
            }
            return Files.readString(file, StandardCharsets.ISO_8859_1).contains(value);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private Finding build(String path, int line, String commit, String added, SecretsAnalyzer.SecretMatch m,
                          AuditRun auditRun) {
        String shortSha = commit == null ? "?" : commit.substring(0, Math.min(8, commit.length()));
        Finding f = new Finding();
        f.setAuditRun(auditRun);
        f.setCategory(Category.SECURITY);
        // Já foi removido da versão atual, mas continua no histórico: precisa ser revogado.
        f.setSeverity(m.pattern().severity() == Severity.CRITICAL ? Severity.HIGH : m.pattern().severity());
        f.setTitle("Segredo removido mas ainda no histórico do Git: " + m.pattern().description());
        f.setDescription("Um segredo foi adicionado no commit " + shortSha + " (" + path + ") e depois removido, mas "
                + "continua acessível no histórico. Revogue/rotacione a credencial; apagar o commit não basta se "
                + "o repositório já foi clonado por alguém.");
        f.setFilePath(path);
        f.setLineNumber(line);
        f.setCodeSnippet(SecretsAnalyzer.redactSecret(added.strip(), m.value()));
        f.setRuleId("secrets-history-" + m.pattern().name());
        f.setToolSource(ANALYZER_NAME);
        return f;
    }
}
