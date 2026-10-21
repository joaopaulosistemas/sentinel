package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Analyzer que detecta segredos hardcoded no código-fonte.
 * <p>
 * Escaneia recursivamente arquivos de código e configuração em busca de padrões como senhas,
 * chaves de API, tokens, connection strings com credenciais e formatos conhecidos de chaves
 * (AWS, GitHub, Slack). Valores obviamente fictícios ({@code changeme}, {@code ${VAR}},
 * {@code <sua-chave>}) são ignorados.
 * <p>
 * Limitações: não analisa o histórico do Git (o clone é raso) e usa heurísticas por expressão regular.
 */
@ApplicationScoped
public class SecretsAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(SecretsAnalyzer.class);
    private static final String ANALYZER_NAME = "Secrets";

    private static final long MAX_FILE_BYTES = 1024L * 1024;
    private static final int MAX_LINE_LENGTH = 2000; // ignora arquivos minificados

    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", ".sentinel", "target", "build", "dist", "vendor",
            "test", "tests", "__tests__", ".mvn"
    );

    private static final Set<String> SCANNED_EXTENSIONS = Set.of(
            ".java", ".kt", ".js", ".jsx", ".ts", ".tsx", ".py", ".cs", ".sh",
            ".properties", ".yml", ".yaml", ".xml", ".json", ".env", ".conf", ".ini",
            ".toml", ".gradle", ".tf"
    );

    /** Valores que indicam exemplo/placeholder e não um segredo real. */
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)(\\$\\{|\\{\\{|<[^>]*>|change[-_]?me|your[-_ ]|example|placeholder|dummy|sample|"
                    + "xxxx|\\*{3,}|todo|fixme|\\bnull\\b|\\bnone\\b)");

    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
            new SecretPattern("password-assignment", Severity.CRITICAL,
                    Pattern.compile("(?i)password\\s*[=:]\\s*[\"'](?<v>[^\"']{4,})[\"']"),
                    "Senha hardcoded detectada"),
            new SecretPattern("api-key-assignment", Severity.CRITICAL,
                    Pattern.compile("(?i)api[_-]?key\\s*[=:]\\s*[\"'](?<v>[^\"']{8,})[\"']"),
                    "API key hardcoded detectada"),
            new SecretPattern("secret-assignment", Severity.CRITICAL,
                    Pattern.compile("(?i)secret\\s*[=:]\\s*[\"'](?<v>[^\"']{8,})[\"']"),
                    "Segredo hardcoded detectado"),
            new SecretPattern("token-assignment", Severity.HIGH,
                    Pattern.compile("(?i)token\\s*[=:]\\s*[\"'](?<v>[^\"']{8,})[\"']"),
                    "Token hardcoded detectado"),
            new SecretPattern("jdbc-with-credentials", Severity.CRITICAL,
                    Pattern.compile("jdbc:[\\w:]+://[^\\s]*:(?<v>[^\\s@/]+)@"),
                    "Connection string JDBC com credenciais embutidas"),
            new SecretPattern("private-key-header", Severity.CRITICAL,
                    Pattern.compile("(?<v>-----BEGIN\\s+[A-Z ]*PRIVATE KEY-----)"),
                    "Chave privada hardcoded no código-fonte"),
            new SecretPattern("aws-access-key", Severity.CRITICAL,
                    Pattern.compile("\\b(?<v>AKIA[0-9A-Z]{16})\\b"),
                    "Access key da AWS hardcoded"),
            new SecretPattern("github-token", Severity.CRITICAL,
                    Pattern.compile("\\b(?<v>gh[pousr]_[A-Za-z0-9]{36,})\\b"),
                    "Token do GitHub hardcoded"),
            new SecretPattern("slack-token", Severity.CRITICAL,
                    Pattern.compile("\\b(?<v>xox[baprs]-[A-Za-z0-9-]{10,})\\b"),
                    "Token do Slack hardcoded")
    );

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();

        try {
            for (Path file : collectFiles(projectPath)) {
                findings.addAll(scanFile(projectPath, file, auditRun));
            }
        } catch (IOException e) {
            LOG.warnf("Erro ao escanear segredos: %s", e.getMessage());
        }

        LOG.infof("Secrets Analyzer encontrou %d findings", findings.size());
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

    private List<Path> collectFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> !Files.isSymbolicLink(p))
                  .filter(p -> !isInIgnoredDirectory(root, p))
                  .filter(this::hasScannableName)
                  .filter(this::isSmallEnough)
                  .forEach(files::add);
        }
        return files;
    }

    /** Verifica apenas as pastas <b>dentro</b> do projeto (o caminho do clone não conta). */
    private boolean isInIgnoredDirectory(Path root, Path file) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            if (IGNORED_DIRS.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasScannableName(Path path) {
        return hasScannableFileName(path.getFileName().toString());
    }

    static boolean hasScannableFileName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.equals(".env") || name.startsWith(".env.")) {
            return !name.endsWith(".example") && !name.endsWith(".sample") && !name.endsWith(".template");
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SCANNED_EXTENSIONS.contains(name.substring(dot));
    }

    private boolean isSmallEnough(Path path) {
        try {
            return Files.size(path) <= MAX_FILE_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    private List<Finding> scanFile(Path root, Path file, AuditRun auditRun) {
        List<Finding> findings = new ArrayList<>();
        List<String> lines = readLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > MAX_LINE_LENGTH) {
                continue;
            }
            for (SecretMatch match : findSecrets(line)) {
                findings.add(buildFinding(root, file, i + 1, line, match.value(), match.pattern(), auditRun));
            }
        }
        return findings;
    }

    private List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            try {
                return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            } catch (IOException ex) {
                return List.of();
            }
        } catch (IOException e) {
            LOG.debugf("Não foi possível ler %s: %s", file.getFileName(), e.getMessage());
            return List.of();
        }
    }

    /** Segredo encontrado em uma linha. */
    record SecretMatch(SecretPattern pattern, String value) {}

    /** Aplica todos os padrões a uma linha (ignora linhas gigantes e valores de exemplo). */
    static List<SecretMatch> findSecrets(String line) {
        List<SecretMatch> found = new ArrayList<>();
        if (line == null || line.length() > MAX_LINE_LENGTH) {
            return found;
        }
        for (SecretPattern pattern : SECRET_PATTERNS) {
            Matcher matcher = pattern.regex().matcher(line);
            if (matcher.find() && !isPlaceholder(matcher.group("v"))) {
                found.add(new SecretMatch(pattern, matcher.group("v")));
            }
        }
        return found;
    }

    /**
     * Indica se um caminho relativo (com {@code /}) deve ser varrido: fora de pastas ignoradas
     * (node_modules, test, ...) e com extensão/nome de arquivo de código ou configuração.
     */
    static boolean isScannablePath(String relativePath) {
        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (IGNORED_DIRS.contains(parts[i])) {
                return false;
            }
        }
        return hasScannableFileName(parts[parts.length - 1]);
    }

    static boolean isPlaceholder(String value) {
        return value == null || value.isBlank() || PLACEHOLDER.matcher(value).find();
    }

    private Finding buildFinding(Path root, Path file, int lineNumber, String line, String value,
                                 SecretPattern pattern, AuditRun auditRun) {
        String relativePath = root.relativize(file).toString();
        Finding f = new Finding();
        f.setAuditRun(auditRun);
        f.setCategory(Category.SECURITY);
        f.setSeverity(pattern.severity());
        f.setTitle(pattern.description());
        f.setDescription("Segredo potencial encontrado no arquivo " + file.getFileName() +
                " (linha " + lineNumber + "). Mova credenciais para variáveis de ambiente ou cofre de segredos, " +
                "e revogue/rotacione o segredo caso ele seja real.");
        f.setFilePath(relativePath);
        f.setLineNumber(lineNumber);
        f.setCodeSnippet(redactSecret(line.strip(), value));
        f.setRuleId("secrets-" + pattern.name());
        f.setToolSource(ANALYZER_NAME);
        return f;
    }

    /** Remove o valor do segredo do trecho de código para não expô-lo no finding. */
    static String redactSecret(String line, String secretValue) {
        String redacted = line;
        if (secretValue != null && !secretValue.isEmpty()) {
            redacted = redacted.replace(secretValue, "****");
        }
        return redacted.replaceAll("([\"'])([^\"']{4,})([\"'])", "$1****$3");
    }

    record SecretPattern(String name, Severity severity, Pattern regex, String description) {}
}
