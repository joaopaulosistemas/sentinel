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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Procura segredos (arquivos atuais <b>e histórico do git</b>) com o Gitleaks.
 * <p>
 * Só atua quando o executável {@code gitleaks} está no PATH; nesse caso substitui os analisadores internos
 * de segredos ({@link SecretsAnalyzer} e {@link HistorySecretsAnalyzer}), evitando contar o mesmo problema duas vezes.
 * <p>
 * <b>O segredo nunca é gravado.</b> O relatório do Gitleaks (que contém o valor em claro) fica em arquivo
 * temporário de uso exclusivo do processo e é apagado ao final; do valor só se guarda um hash curto, usado para
 * reconhecer o mesmo segredo entre auditorias.
 */
@ApplicationScoped
public class GitleaksAnalyzer implements CodeAnalyzer {

    private static final int SHORT_SHA_LENGTH = 8;
    private static final int FINGERPRINT_HEX_CHARS = 12;


    private static final Logger LOG = Logger.getLogger(GitleaksAnalyzer.class);
    private static final String ANALYZER_NAME = "Gitleaks";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(name = "sentinel.audit.gitleaks.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "sentinel.audit.gitleaks.timeout-seconds", defaultValue = "600")
    long timeoutSeconds;

    /** Substituível em testes. */
    String pathEnv = System.getenv("PATH");

    @Override
    public boolean isAvailable() {
        return enabled && ExternalTools.find("gitleaks", pathEnv) != null;
    }

    @Override
    public List<Class<? extends CodeAnalyzer>> replaces() {
        return List.of(SecretsAnalyzer.class, HistorySecretsAnalyzer.class);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Path exe = enabled ? ExternalTools.find("gitleaks", pathEnv) : null;
        if (exe == null) {
            return ToolRun.unavailable(ANALYZER_NAME,
                    "Gitleaks indisponível; análise de segredos por esta ferramenta ignorada", LOG);
        }
        return ToolRun.run(ANALYZER_NAME, "gitleaks", LOG, (report, stderr) -> {
            List<String> cmd = new ArrayList<>(List.of(exe.toString(), "detect",
                    "--source", projectPath.toString(),
                    "--report-format", "json", "--report-path", report.toString(),
                    "--no-banner", "--log-level", "error", "--exit-code", "0"));
            if (!Files.isDirectory(projectPath.resolve(".git"))) {
                cmd.add("--no-git");
            }
            int code = ExternalTools.runTo(cmd, null, Map.of(), timeoutSeconds, new ExternalTools.Output(null, stderr));
            if (code != 0) {
                String partial = ExternalTools.falha(code, timeoutSeconds, stderr);
                LOG.warnf("Gitleaks %s", partial);
                return new ToolRun.Outcome(new ArrayList<>(), partial);
            }
            List<Finding> found = parse(Files.readString(report, StandardCharsets.UTF_8), projectPath, auditRun);
            LOG.infof("Gitleaks encontrou %d segredos", found.size());
            return new ToolRun.Outcome(found, null);
        });
    }

    /** Converte o relatório JSON (lista) em findings; o valor do segredo é descartado aqui. */
    List<Finding> parse(String json, Path projectPath, AuditRun auditRun) {
        List<Finding> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                return out;
            }
            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode n : root) {
                String rule = n.path("RuleID").asText("secret");
                String file = ExternalTools.relativize(projectPath, n.path("File").asText(""));
                String secretHash = shortHash(n.path("Secret").asText(n.path("Match").asText("")));
                String commit = n.path("Commit").asText("");
                // mesmo segredo no mesmo arquivo em vários commits: um finding só (o mais recente vem primeiro)
                if (!seen.add(rule + "\0" + file + "\0" + secretHash)) {
                    continue;
                }
                Finding f = new Finding();
                f.setAuditRun(auditRun);
                f.setToolSource(ANALYZER_NAME);
                f.setCategory(Category.SECURITY);
                f.setSeverity(Severity.HIGH);
                f.setRuleId("gitleaks-" + rule);
                f.setFilePath(file);
                f.setLineNumber(n.path("StartLine").asInt(0));
                String what = n.path("Description").asText(rule);
                f.setTitle("Segredo exposto: " + what);
                f.setDescription("O Gitleaks detectou " + what + " em " + file
                        + (commit.isBlank() ? "" : " (commit " + commit.substring(0, Math.min(SHORT_SHA_LENGTH, commit.length())) + ")")
                        + ". Revogue/rotacione a credencial: removê-la do código não basta se ela ficou no histórico.");
                f.setCodeSnippet("segredo sha256:" + secretHash);   // identifica sem revelar
                out.add(f);
            }
        } catch (Exception e) {
            LOG.warnf("Não foi possível interpretar o relatório do Gitleaks: %s", e.getMessage());
            return new ArrayList<>();
        }
        return out;
    }

    private static String shortHash(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, FINGERPRINT_HEX_CHARS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
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
