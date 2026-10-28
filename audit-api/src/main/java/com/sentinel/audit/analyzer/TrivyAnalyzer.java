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

import java.io.IOException;
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

    private static final long DEFAULT_GRACE_SECONDS = 30;
    private static final int ERROR_TAIL_CHARS = 4000;


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
    long graceSeconds = DEFAULT_GRACE_SECONDS;

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
        Path stderr = null;
        String partial = null;
        try {
            report = Files.createTempFile("trivy-", ".json");
            stderr = Files.createTempFile("trivy-err-", ".txt");
            int code = executar(exe, projectPath, report, stderr, false);
            if (code != 0 && code != -1 && limiteDoMaven(stderr)) {
                // O Maven Central bloqueou o IP (429) ao baixar POMs pai: o Trivy aborta a varredura inteira.
                // Refaz sem consultar repositórios remotos; o que já estiver no ~/.m2 continua sendo usado.
                LOG.warn("Trivy: repositório Maven limitou as requisições; repetindo com --offline-scan");
                code = executar(exe, projectPath, report, stderr, true);
                if (code == 0) {
                    partial = "dependências Maven resolvidas só pelo cache local (o Maven Central limitou as requisições); "
                            + "para cobertura completa rode `mvn dependency:resolve` antes ou monte um volume em ~/.m2";
                }
            }
            if (code != 0) {
                partial = ExternalTools.falha(code, timeoutSeconds, stderr)
                        + (code == -1 ? "" : " (base de vulnerabilidades indisponível?)")
                        + dicaCertificado(code, stderr);
                LOG.warnf("Trivy %s", partial);
            } else {
                findings = parse(Files.readString(report, StandardCharsets.UTF_8), projectPath, auditRun);
                LOG.infof("Trivy encontrou %d findings", findings.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            partial = "execução interrompida";
        } catch (Exception e) {
            LOG.errorf("Erro ao executar o Trivy: %s", e.getMessage());
            partial = "erro ao executar: " + e.getMessage();
        } finally {
            if (report != null) {
                try {
                    Files.deleteIfExists(report);
                } catch (Exception ignored) {
                    // arquivo temporário sem segredos; não vale falhar por isso
                }
            }
        }
        ExternalTools.deleteQuietly(stderr);
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()), partial);
    }

    private int executar(Path exe, Path projectPath, Path report, Path stderr, boolean offline)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(exe.toString(), "fs", "--quiet", "--no-progress",
                "--format", "json", "--output", report.toString(),
                "--scanners", String.join(",", scannerList()),
                "--skip-dirs", "node_modules,.git",
                "--timeout", timeoutSeconds + "s"));
        if (offline) {
            cmd.add("--offline-scan");
        }
        cmd.add(projectPath.toString());
        return ExternalTools.run(cmd, null, Map.of("TRIVY_DISABLE_TELEMETRY", "true"), timeoutSeconds + graceSeconds, null, stderr);
    }

    /** O stderr do Trivy indica bloqueio/limite de taxa do Maven Central (HTTP 429, Retry-After). */
    static boolean limiteDoMaven(Path stderrFile) {
        String err = ExternalTools.tail(stderrFile, ERROR_TAIL_CHARS).toLowerCase(java.util.Locale.ROOT);
        return err.contains("retry-after") || err.contains("429") || err.contains("too many requests");
    }

    /**
     * Quando o download da base falha por certificado não confiável (inspeção SSL de proxy corporativo, como o
     * Zscaler), diz o que fazer. Vazio nos demais casos.
     */
    static String dicaCertificado(int code, Path stderrFile) {
        if (code == -1) {
            return "";
        }
        String err = ExternalTools.tail(stderrFile, ERROR_TAIL_CHARS).toLowerCase(java.util.Locale.ROOT);
        if (!err.contains("x509") && !err.contains("certificate")) {
            return "";
        }
        return " Rede com inspeção SSL? Coloque o CA raiz da empresa em audit-api/certs/ e refaça a imagem"
                + " (docker compose build --no-cache audit-api); veja audit-api/certs/README.md.";
    }

    private List<String> scannerList() {
        List<String> list = new ArrayList<>();
        for (String s : (scanners == null ? "" : scanners).split(",")) {
            String t = s.trim().toLowerCase(java.util.Locale.ROOT);
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
        return switch (s.toUpperCase(java.util.Locale.ROOT)) {
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
