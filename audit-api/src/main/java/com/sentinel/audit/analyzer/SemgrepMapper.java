package com.sentinel.audit.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converte um resultado do JSON do Semgrep em {@link Finding}: gravidade, categoria, CWE, modo sombra e caminho relativo. */
final class SemgrepMapper {

    private static final String TOOL = "Semgrep";
    private static final String ELLIPSIS = "...";
    private static final int TITLE_MAX_CHARS = 500;
    private static final Pattern CWE_PATTERN = Pattern.compile("CWE-\\d+", Pattern.CASE_INSENSITIVE);

    private SemgrepMapper() {
    }

    static Finding toFinding(JsonNode result, Path projectPath, AuditRun auditRun) {
        Finding finding = new Finding();
        finding.setAuditRun(auditRun);
        finding.setToolSource(TOOL);

        String checkId = result.path("check_id").asText("");
        finding.setRuleId(checkId);
        finding.setFilePath(relativize(projectPath, result.path("path").asText("")));
        finding.setLineNumber(result.path("start").path("line").asInt(0));

        JsonNode extra = result.path("extra");
        String message = extra.path("message").asText(checkId);
        finding.setTitle(message.length() > TITLE_MAX_CHARS ? message.substring(0, TITLE_MAX_CHARS - ELLIPSIS.length()) + ELLIPSIS : message);
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
        return m.find() ? m.group().toUpperCase(Locale.ROOT) : null;
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

    private static Severity mapSeverity(String semgrepSeverity) {
        return switch (semgrepSeverity.toUpperCase(Locale.ROOT)) {
            case "ERROR", "CRITICAL" -> Severity.HIGH;
            case "WARNING"           -> Severity.MEDIUM;
            default                  -> Severity.LOW;
        };
    }

    private static final Map<String, Category> METADATA_CATEGORIES = Map.of(
            "security", Category.SECURITY,
            "architecture", Category.ARCHITECTURE,
            "observability", Category.OBSERVABILITY,
            "performance", Category.PERFORMANCE);

    /** Trechos do id da regra que indicam segurança quando a regra não declara a categoria. */
    private static final List<String> SECURITY_HINTS = List.of(
            "security", "sql", "injection", "xss", "csrf", "ssrf", "secret", "owasp");

    private static Category mapCategory(String checkId, String metadataCategory) {
        for (Map.Entry<String, Category> e : METADATA_CATEGORIES.entrySet()) {
            if (e.getKey().equalsIgnoreCase(metadataCategory)) {
                return e.getValue();
            }
        }
        String lower = checkId.toLowerCase(Locale.ROOT);
        return SECURITY_HINTS.stream().anyMatch(lower::contains) ? Category.SECURITY : Category.QUALITY;
    }
}
