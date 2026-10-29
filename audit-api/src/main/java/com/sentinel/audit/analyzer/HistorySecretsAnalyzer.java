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

    private static final int GIT_TIMEOUT_SECONDS = 30;
    private static final String COMMIT_MARKER = "@@commit ";
    private static final String DIFF_NEW_FILE_PREFIX = "+++ b/";
    private static final int SHORT_SHA_LENGTH = 8;
    private static final long MAX_SCANNED_FILE_BYTES = 1024L * 1024;


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

    private static String firstLine(Process p) throws IOException {
        String line = p.inputReader(StandardCharsets.UTF_8).readLine();
        return line == null ? "" : line.trim();
    }

    /** Commits disponíveis no clone: 1 = clone raso (histórico desligado no cadastro do sistema), nada a varrer. */
    private int commitCount(Path repo) {
        try {
            Process p = new ProcessBuilder("git", "-C", repo.toString(), "rev-list", "--count", "HEAD")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String out = firstLine(p);
            return p.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) && p.exitValue() == 0 ? Integer.parseInt(out) : 0;
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

    /** Onde a leitura do diff está: commit, arquivo e linha atuais, e se o arquivo vale ser examinado. */
    private static final class DiffState {
        String commit;
        String path;
        boolean scan;
        int line;

        /** Atualiza o estado se a linha for um cabeçalho do diff; devolve se foi. */
        boolean readHeader(String l) {
            if (l.startsWith(COMMIT_MARKER)) {
                commit = l.substring(COMMIT_MARKER.length()).trim();
            } else if (l.startsWith("+++ ")) {
                path = diffPath(l);
                scan = path != null && SecretsAnalyzer.isScannablePath(path);
            } else if (l.startsWith("@@ ")) {
                line = hunkStart(l);
            } else {
                return false;
            }
            return true;
        }

        boolean isAddedLine(String l) {
            return scan && l.startsWith("+") && !l.startsWith("+++");
        }
    }

    /** Lê a saída de {@code git log -p} e devolve segredos adicionados que não existem mais na versão atual. */
    List<Finding> parse(Path logFile, Path repo, AuditRun auditRun) throws IOException {
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        DiffState st = new DiffState();
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.ISO_8859_1)) {
            String l = reader.readLine();
            while (l != null && findings.size() < MAX_FINDINGS) {
                if (!st.readHeader(l) && st.isAddedLine(l)) {
                    findings.addAll(secretsIn(st, l.substring(1), repo, auditRun, seen));
                    st.line++;
                }
                l = reader.readLine();
            }
        }
        return findings;
    }

    /** Segredos novos na linha adicionada (um por arquivo e valor) que já não estão no arquivo atual. */
    private List<Finding> secretsIn(DiffState st, String added, Path repo, AuditRun auditRun, Set<String> seen) {
        List<Finding> found = new ArrayList<>();
        for (SecretsAnalyzer.SecretMatch m : SecretsAnalyzer.findSecrets(added)) {
            String key = st.path + "|" + m.value();
            if (seen.add(key) && !stillPresent(repo, st.path, m.value())) {
                found.add(build(st, added, m, auditRun));
            }
        }
        return found;
    }

    /** Caminho do arquivo novo no cabeçalho {@code +++ b/...} do diff; nulo para {@code /dev/null}. */
    private static String diffPath(String header) {
        return header.startsWith(DIFF_NEW_FILE_PREFIX) ? header.substring(DIFF_NEW_FILE_PREFIX.length()) : null;
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
                    || Files.size(file) > MAX_SCANNED_FILE_BYTES) {
                return false;
            }
            return Files.readString(file, StandardCharsets.ISO_8859_1).contains(value);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private Finding build(DiffState st, String added, SecretsAnalyzer.SecretMatch m, AuditRun auditRun) {
        String path = st.path;
        String commit = st.commit;
        String shortSha = commit == null ? "?" : commit.substring(0, Math.min(SHORT_SHA_LENGTH, commit.length()));
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
        f.setLineNumber(st.line);
        f.setCodeSnippet(SecretsAnalyzer.redactSecret(added.strip(), m.value()));
        f.setRuleId("secrets-history-" + m.pattern().name());
        f.setToolSource(ANALYZER_NAME);
        return f;
    }
}
