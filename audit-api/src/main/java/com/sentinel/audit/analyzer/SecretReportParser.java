package com.sentinel.audit.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lê o relatório JSON de segredos no formato do Gitleaks, que o Betterleaks 1.x também escreve
 * (lista com {@code RuleID}, {@code File}, {@code Secret}, {@code Commit}...). O valor do segredo é descartado aqui.
 */
final class SecretReportParser {

    private static final int SHORT_SHA_LENGTH = 8;
    private static final int FINGERPRINT_HEX_CHARS = 12;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(SecretReportParser.class);

    private SecretReportParser() { }

    /** @param tool nome da ferramenta (vai no finding e no texto); o id da regra vira {@code <tool em minúsculas>-<RuleID>} */
    static List<Finding> parse(String tool, String json, Path projectPath, AuditRun auditRun) {
        List<Finding> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                return out;
            }
            String prefix = tool.toLowerCase(java.util.Locale.ROOT) + "-";
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
                f.setToolSource(tool);
                f.setCategory(Category.SECURITY);
                f.setSeverity(Severity.HIGH);
                f.setRuleId(prefix + rule);
                f.setFilePath(file);
                f.setLineNumber(n.path("StartLine").asInt(0));
                String what = n.path("Description").asText(rule);
                f.setTitle("Segredo exposto: " + what);
                f.setDescription("O " + tool + " detectou " + what + " em " + file
                        + (commit.isBlank() ? "" : " (commit " + commit.substring(0, Math.min(SHORT_SHA_LENGTH, commit.length())) + ")")
                        + ". Revogue/rotacione a credencial: removê-la do código não basta se ela ficou no histórico.");
                f.setCodeSnippet("segredo sha256:" + secretHash);   // identifica sem revelar
                out.add(f);
            }
        } catch (Exception e) {
            LOG.warnf("Não foi possível interpretar o relatório do %s: %s", tool, e.getMessage());
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
}
