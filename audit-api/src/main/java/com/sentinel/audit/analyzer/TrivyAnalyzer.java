package com.sentinel.audit.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vulnerabilidades em dependências e más configurações de IaC/Dockerfile/Kubernetes com o Trivy.
 * <p>
 * Só atua quando o executável {@code trivy} está no PATH. Se o scanner {@code vuln} estiver ligado
 * (padrão), substitui o {@link OWASPDependencyAnalyzer}. Segredos ficam a cargo do Gitleaks.
 * <p>
 * O Trivy baixa a base de vulnerabilidades na primeira execução; em rede fechada use
 * {@code TRIVY_SKIP_DB_UPDATE=true} com uma base já presente no cache ({@code TRIVY_CACHE_DIR}).
 */
@ApplicationScoped
public class TrivyAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(TrivyAnalyzer.class);
    private static final String ANALYZER_NAME = "Trivy";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(name = "sentinel.audit.trivy.enabled", defaultValue = "true")
    boolean enabled;

    /** Scanners do Trivy, separados por vírgula: {@code vuln}, {@code misconfig}. */
    @ConfigProperty(name = "sentinel.audit.trivy.scanners", defaultValue = "vuln,misconfig")
    String scanners;

    @ConfigProperty(name = "sentinel.audit.trivy.timeout-seconds", defaultValue = "900")
    long timeoutSeconds;

    String pathEnv = System.getenv("PATH");

    /** Folga além do --timeout do próprio Trivy, para ele encerrar sozinho antes de ser morto. */
    long graceSeconds = 30;

    @Override
    public boolean isAvailable() {
        return enabled && ExternalTools.find("trivy", pathEnv) != null;
    }

    @Override
    public List<Class<? extends CodeAnalyzer>> replaces() {
        return scannerList().contains("vuln") ? List.of(OWASPDependencyAnalyzer.class) : List.of();
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        Path exe = enabled ? ExternalTools.find("trivy", pathEnv) : null;
        if (exe == null) {
            LOG.warn("Trivy indisponível; análise por esta ferramenta ignorada");
            return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
        }

        Path report = null;
        try {
            report = Files.createTempFile("trivy-", ".json");
            List<String> cmd = List.of(exe.toString(), "fs", "--quiet", "--no-progress",
                    "--format", "json", "--output", report.toString(),
                    "--scanners", String.join(",", scannerList()),
                    "--skip-dirs", "node_modules,.git",
                    "--timeout", timeoutSeconds + "s",
                    projectPath.toString());
            int code = ExternalTools.run(cmd, Map.of("TRIVY_DISABLE_TELEMETRY", "true"), timeoutSeconds + graceSeconds);
            if (code == -1) {
                LOG.warnf("Trivy excedeu o tempo limite de %ds e foi encerrado", timeoutSeconds);
            } else if (code != 0) {
                LOG.warnf("Trivy terminou com código %d (base de vulnerabilidades indisponível?)", code);
            } else {
                findings = parse(Files.readString(report, StandardCharsets.UTF_8), projectPath, auditRun);
                LOG.infof("Trivy encontrou %d findings", findings.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.errorf("Erro ao executar o Trivy: %s", e.getMessage());
        } finally {
            if (report != null) {
                try {
                    Files.deleteIfExists(report);
                } catch (Exception ignored) {
                    // arquivo temporário sem segredos; não vale falhar por isso
                }
            }
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    private List<String> scannerList() {
        List<String> list = new ArrayList<>();
        for (String s : (scanners == null ? "" : scanners).split(",")) {
            String t = s.trim().toLowerCase();
            if ((t.equals("vuln") || t.equals("misconfig")) && !list.contains(t)) {
                list.add(t);
            }
        }
        return list.isEmpty() ? List.of("vuln", "misconfig") : list;
    }

    /** Converte o JSON do {@code trivy fs} em findings. */
    List<Finding> parse(String json, Path projectPath, AuditRun auditRun) {
        List<Finding> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode results = MAPPER.readTree(json).path("Results");
            if (!results.isArray()) {
                return out;
            }
            for (JsonNode result : results) {
                String target = ExternalTools.relativize(projectPath, result.path("Target").asText(""));
                for (JsonNode v : result.path("Vulnerabilities")) {
                    out.add(vulnerability(v, target, auditRun));
                }
                for (JsonNode m : result.path("Misconfigurations")) {
                    out.add(misconfiguration(m, target, auditRun));
                }
            }
        } catch (Exception e) {
            LOG.warnf("Não foi possível interpretar a saída do Trivy: %s", e.getMessage());
            return new ArrayList<>();
        }
        return out;
    }

    private Finding vulnerability(JsonNode v, String target, AuditRun run) {
        String id = v.path("VulnerabilityID").asText("");
        String pkg = v.path("PkgName").asText("?");
        String installed = v.path("InstalledVersion").asText("?");
        String fixed = v.path("FixedVersion").asText("");
        String title = v.path("Title").asText("");

        Finding f = base(run);
        f.setCategory(Category.DEPENDENCY);
        f.setSeverity(severity(v.path("Severity").asText("")));
        f.setRuleId(id);                       // identidade: CVE + arquivo (atualizar a versão não cria "novo")
        f.setCveId(id);
        f.setFilePath(target);
        f.setTitle(id + " em " + pkg + " " + installed + (title.isBlank() ? "" : " — " + title));
        f.setDescription(v.path("Description").asText(title)
                + (fixed.isBlank() ? " Ainda não há versão corrigida publicada."
                        : " Atualize " + pkg + " para " + fixed + " (versões corrigidas: " + fixed + ").")
                + (v.path("PrimaryURL").asText("").isBlank() ? "" : " Detalhes: " + v.path("PrimaryURL").asText()));
        return f;
    }

    private Finding misconfiguration(JsonNode m, String target, AuditRun run) {
        String id = m.path("AVDID").asText(m.path("ID").asText("misconfig"));
        String message = m.path("Message").asText(m.path("Title").asText(id));

        Finding f = base(run);
        f.setCategory(Category.SECURITY);
        f.setSeverity(severity(m.path("Severity").asText("")));
        f.setRuleId("trivy-" + id);
        f.setFilePath(target);
        f.setLineNumber(m.path("CauseMetadata").path("StartLine").asInt(0));
        f.setTitle(message);
        String resolution = m.path("Resolution").asText("");
        f.setDescription(m.path("Description").asText(message) + (resolution.isBlank() ? "" : " Como corrigir: " + resolution));
        return f;
    }

    private Finding base(AuditRun run) {
        Finding f = new Finding();
        f.setAuditRun(run);
        f.setToolSource(ANALYZER_NAME);
        return f;
    }

    static Severity severity(String s) {
        return switch (s.toUpperCase()) {
            case "CRITICAL" -> Severity.CRITICAL;
            case "HIGH" -> Severity.HIGH;
            case "MEDIUM" -> Severity.MEDIUM;
            case "LOW" -> Severity.LOW;
            default -> Severity.INFO;
        };
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
