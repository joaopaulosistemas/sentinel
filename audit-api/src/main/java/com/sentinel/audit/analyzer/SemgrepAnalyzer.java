package com.sentinel.audit.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.RulePack;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzer que executa o Semgrep para detectar problemas de segurança e qualidade.
 * <p>
 * Comportamento:
 * <ul>
 *   <li>Se o Semgrep não estiver instalado, retorna lista vazia e registra um WARNING.</li>
 *   <li>Usa as regras dos pacotes ativos ({@code rules/} de cada pacote) ou o valor de
 *       {@code sentinel.audit.semgrep.config}; na ausência de ambos, usa {@code p/default}.</li>
 *   <li>Roda sempre com {@code --metrics=off}: nenhum dado do código auditado é enviado ao Semgrep.</li>
 *   <li>A saída JSON (stdout) é lida separadamente do stderr, para que avisos não quebrem o parse.</li>
 *   <li>Há timeout; se estourar, o processo é encerrado e a análise é descartada com um WARNING.</li>
 * </ul>
 */
@ApplicationScoped
public class SemgrepAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(SemgrepAnalyzer.class);
    private static final String ANALYZER_NAME = "Semgrep";
    private static final String DEFAULT_REGISTRY_CONFIG = "p/default";
    private static final Pattern CWE_PATTERN = Pattern.compile("CWE-\\d+", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Configs do Semgrep separadas por vírgula (caminhos ou p/...). Vazio = auto-detecção. */
    @ConfigProperty(name = "sentinel.audit.semgrep.config")
    Optional<String> configuredRules;

    @ConfigProperty(name = "sentinel.audit.semgrep.timeout-seconds", defaultValue = "600")
    long timeoutSeconds;

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();

        if (!isSemgrepAvailable()) {
            LOG.warnf("Semgrep não está instalado. Análise SAST ignorada para %s", projectPath);
            return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
        }

        try {
            List<String> configs = resolveConfigs(policy);
            String jsonOutput = configs.isEmpty() ? null : runSemgrep(projectPath, configs);
            if (jsonOutput != null) {
                findings = parseSemgrepOutput(jsonOutput, projectPath, auditRun);
                LOG.infof("Semgrep encontrou %d findings", findings.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Execução do Semgrep interrompida");
        } catch (Exception e) {
            LOG.errorf(e, "Erro ao executar Semgrep");
        }

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

    /**
     * Procura o executável no PATH sem executá-lo: {@code semgrep --version} faz uma checagem de
     * versão pela rede e, em redes restritas, pode levar minutos.
     */
    private boolean isSemgrepAvailable() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return false;
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String name : List.of("semgrep", "semgrep.exe", "semgrep.cmd", "semgrep.bat")) {
                Path candidate = Paths.get(dir, name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Bibliotecas de terceiros e arquivos gerados: não são código do projeto e inflam o relatório
     * (na calibração, um único projeto trouxe milhares de achados só de jQuery e Ace embutidos).
     */
    static final List<String> VENDORED_EXCLUDES = List.of(
            "node_modules", "vendor", "vendors", "third_party", "thirdparty", "bower_components", "libs",
            "*.min.js", "*.min.css", "*.bundle.js", "*.pack.js");

    /**
     * Executa o Semgrep e devolve o JSON de saída, ou {@code null} se a execução falhou.
     */
    private String runSemgrep(Path projectPath, List<String> configs) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("semgrep");
        for (String config : configs) {
            command.add("--config");
            command.add(config);
        }
        for (String pattern : VENDORED_EXCLUDES) {
            command.add("--exclude");
            command.add(pattern);
        }
        command.add("--json");
        command.add("--quiet");
        command.add("--metrics=off");
        command.add("--disable-version-check");
        command.add("--timeout");
        command.add("60");
        command.add(projectPath.toString());

        Path outputFile = Files.createTempFile("semgrep-out-", ".json");
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("SEMGREP_SEND_METRICS", "off");
            pb.environment().put("SEMGREP_ENABLE_VERSION_CHECK", "0");
            pb.redirectOutput(outputFile.toFile());               // somente stdout (JSON)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);    // stderr nunca contamina o JSON
            Process process = pb.start();

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warnf("Semgrep excedeu o tempo limite de %ds e foi encerrado", timeoutSeconds);
                return null;
            }
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            // 0 = ok; 1 = achados (com --error). Outros códigos (ex.: 2 = regra inválida ou arquivo que
            // não pôde ser analisado) ainda podem trazer resultados parciais em JSON: aproveita se houver.
            if (process.exitValue() > 1) {
                LOG.warnf("Semgrep terminou com código %d; usando os resultados parciais, se existirem",
                        process.exitValue());
            }
            return output.isBlank() ? null : output;
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    /**
     * Configs do Semgrep desta auditoria: o que foi configurado à mão; senão as pastas {@code rules/} dos pacotes
     * ativos da política. Lista vazia = nada a rodar (ex.: perfil só com pacotes de arquitetura).
     */
    List<String> resolveConfigs(AuditPolicy policy) {
        if (configuredRules.isPresent() && !configuredRules.get().isBlank()) {
            List<String> configs = new ArrayList<>();
            for (String c : configuredRules.get().split(",")) {
                if (!c.isBlank()) {
                    configs.add(c.trim());
                }
            }
            if (!configs.isEmpty()) {
                return configs;
            }
        }
        if (policy == null) {
            LOG.warnf("Sem política de pacotes; usando o conjunto público '%s'", DEFAULT_REGISTRY_CONFIG);
            return List.of(DEFAULT_REGISTRY_CONFIG);
        }
        List<String> dirs = new ArrayList<>();
        for (RulePack pack : policy.packs()) {
            if (pack.rulesDir() != null) {
                dirs.add(pack.rulesDir().toString());
            }
        }
        if (dirs.isEmpty() && "default".equals(policy.profileId()) && policy.packs().isEmpty()) {
            LOG.warnf("Nenhum pacote de regras instalado; usando o conjunto público '%s'", DEFAULT_REGISTRY_CONFIG);
            return List.of(DEFAULT_REGISTRY_CONFIG);
        }
        if (dirs.isEmpty()) {
            LOG.infof("O perfil '%s' não tem pacotes com regras de código; Semgrep não será executado", policy.profileId());
        } else {
            LOG.infof("Semgrep com %d pacote(s) de regras do perfil '%s'", dirs.size(), policy.profileId());
        }
        return dirs;
    }

    List<Finding> parseSemgrepOutput(String jsonOutput, Path projectPath, AuditRun auditRun) {
        List<Finding> findings = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonOutput);
            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode result : results) {
                    findings.add(mapToFinding(result, projectPath, auditRun));
                }
            }
        } catch (Exception e) {
            LOG.warnf("Não foi possível interpretar a saída do Semgrep: %s", e.getMessage());
        }
        return findings;
    }

    private Finding mapToFinding(JsonNode result, Path projectPath, AuditRun auditRun) {
        Finding finding = new Finding();
        finding.setAuditRun(auditRun);
        finding.setToolSource(ANALYZER_NAME);

        String checkId = result.path("check_id").asText("");
        finding.setRuleId(checkId);
        finding.setFilePath(relativize(projectPath, result.path("path").asText("")));
        finding.setLineNumber(result.path("start").path("line").asInt(0));

        JsonNode extra = result.path("extra");
        String message = extra.path("message").asText(checkId);
        finding.setTitle(message.length() > 500 ? message.substring(0, 497) + "..." : message);
        finding.setDescription(message);

        // Sem login, o Semgrep devolve "requires login" no lugar do trecho de código.
        String codeSnippet = extra.path("lines").asText("");
        if (!codeSnippet.isBlank() && !"requires login".equalsIgnoreCase(codeSnippet.strip())) {
            finding.setCodeSnippet(codeSnippet);
        }

        JsonNode metadata = extra.path("metadata");
        finding.setCveId(extractCwe(metadata.path("cwe")));
        finding.setSeverity(mapSeverity(extra.path("severity").asText("WARNING")));
        finding.setCategory(mapCategory(checkId, metadata.path("category").asText("")));
        if (isShadowStatus(metadata.path("status").asText(""))) {
            finding.setShadow(true);                  // regra em rascunho/experimental: aparece, mas não pesa no score
        }

        return finding;
    }

    /** Regras {@code draft} e {@code experimental} começam em modo sombra até serem promovidas (status: stable). */
    static boolean isShadowStatus(String status) {
        return "draft".equalsIgnoreCase(status) || "experimental".equalsIgnoreCase(status);
    }

    /** O campo {@code cwe} pode ser texto ("CWE-89: ...") ou lista; devolve só o "CWE-NN". */
    static String extractCwe(JsonNode cwe) {
        String raw = "";
        if (cwe.isArray() && !cwe.isEmpty()) {
            raw = cwe.get(0).asText("");
        } else if (cwe.isTextual()) {
            raw = cwe.asText("");
        }
        Matcher m = CWE_PATTERN.matcher(raw);
        return m.find() ? m.group().toUpperCase() : null;
    }

    private static String relativize(Path root, String path) {
        if (path.isBlank()) {
            return path;
        }
        try {
            Path p = Paths.get(path);
            return p.isAbsolute() ? root.relativize(p).toString() : path;
        } catch (RuntimeException e) {
            return path;
        }
    }

    private Severity mapSeverity(String semgrepSeverity) {
        return switch (semgrepSeverity.toUpperCase()) {
            case "ERROR", "CRITICAL" -> Severity.HIGH;
            case "WARNING"           -> Severity.MEDIUM;
            default                  -> Severity.LOW;
        };
    }

    private Category mapCategory(String checkId, String metadataCategory) {
        if ("security".equalsIgnoreCase(metadataCategory)) {
            return Category.SECURITY;
        }
        if ("architecture".equalsIgnoreCase(metadataCategory)) {
            return Category.ARCHITECTURE;
        }
        if ("observability".equalsIgnoreCase(metadataCategory)) {
            return Category.OBSERVABILITY;
        }
        if ("performance".equalsIgnoreCase(metadataCategory)) {
            return Category.PERFORMANCE;
        }
        String lower = checkId.toLowerCase();
        if (lower.contains("security") || lower.contains("sql") || lower.contains("injection")
                || lower.contains("xss") || lower.contains("csrf") || lower.contains("ssrf")
                || lower.contains("secret") || lower.contains("owasp")) {
            return Category.SECURITY;
        }
        return Category.QUALITY;
    }
}
