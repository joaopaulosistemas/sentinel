package com.sentinel.audit.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte os findings da API para SARIF 2.1.0, o formato lido pelo GitHub Code Scanning,
 * Azure DevOps, GitLab e pelo VS Code (extensão SARIF Viewer).
 */
final class SarifWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SarifWriter() {}

    static String write(JsonNode findings, String toolVersion) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        root.put("version", "2.1.0");
        ArrayNode runs = root.putArray("runs");
        ObjectNode run = runs.addObject();

        ObjectNode driver = run.putObject("tool").putObject("driver");
        driver.put("name", "Sentinel Audit Tool");
        driver.put("version", toolVersion);

        Map<String, ObjectNode> rules = new LinkedHashMap<>();
        ArrayNode results = run.putArray("results");

        for (JsonNode f : findings) {
            String ruleId = text(f, "ruleId", "sentinel-audit-" + text(f, "category", "finding").toLowerCase(java.util.Locale.ROOT));
            String severity = text(f, "severity", "MEDIUM");

            rules.computeIfAbsent(ruleId, id -> {
                ObjectNode rule = MAPPER.createObjectNode();
                rule.put("id", id);
                rule.putObject("shortDescription").put("text", text(f, "title", id));
                String ref = firstReference(f);
                if (ref != null) {
                    rule.put("helpUri", ref);
                }
                rule.putObject("properties").put("security-severity", securitySeverity(severity));
                return rule;
            });

            ObjectNode result = results.addObject();
            result.put("ruleId", ruleId);
            result.put("level", level(severity));
            String message = text(f, "title", ruleId);
            String description = text(f, "description", "");
            result.putObject("message").put("text", description.isBlank() ? message : message + " — " + description);

            String path = normalizePath(text(f, "filePath", ""));
            if (!path.isEmpty()) {
                ObjectNode physical = result.putArray("locations").addObject().putObject("physicalLocation");
                physical.putObject("artifactLocation").put("uri", path);
                int line = f.path("lineNumber").asInt(0);
                if (line > 0) {
                    physical.putObject("region").put("startLine", line);
                }
            }
            String fingerprint = text(f, "fingerprint", "");
            if (!fingerprint.isEmpty()) {
                result.putObject("partialFingerprints").put("sentinelFingerprint/v1", fingerprint);
            }
            ObjectNode props = result.putObject("properties");
            props.put("severity", severity);
            props.put("category", text(f, "category", ""));
            props.put("tool", text(f, "toolSource", ""));
            if (f.path("isNew").asBoolean(false)) {
                props.put("new", true);
            }
        }

        ArrayNode ruleArray = driver.putArray("rules");
        rules.values().forEach(ruleArray::add);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    static String level(String severity) {
        return switch (severity) {
            case "CRITICAL", "HIGH" -> "error";
            case "MEDIUM" -> "warning";
            default -> "note";
        };
    }

    /** Escala 0-10 que o GitHub usa para ordenar alertas de segurança. */
    static String securitySeverity(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "9.5";
            case "HIGH" -> "8.0";
            case "MEDIUM" -> "5.5";
            case "LOW" -> "3.0";
            default -> "0.5";
        };
    }

    private static String firstReference(JsonNode f) {
        JsonNode refs = f.path("remediation").path("references");
        return refs.isArray() && !refs.isEmpty() ? refs.get(0).asText() : null;
    }

    /** SARIF pede URI relativa ao repositório, com barras normais e sem "./" nem "/" inicial. */
    static String normalizePath(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("./") || p.startsWith("/")) {
            p = p.startsWith("./") ? p.substring(2) : p.substring(1);
        }
        return p;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? fallback : v.asText();
    }
}
