package com.sentinel.audit.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resume os parâmetros de uma chamada MCP para o registro de chamadas: nome e valor curtos, só de campos simples.
 * Campos com cara de segredo saem mascarados, para o registro mostrar o que o Claude perguntou sem guardar credenciais.
 */
final class McpCallSummary {

    static final int MAX_VALUE = 60;
    static final int MAX_TOTAL = 300;
    private static final Pattern SECRET = Pattern.compile("(?i)(key|token|secret|senha|password|authorization)");

    private McpCallSummary() {}

    /** {@code erro} (pode ser nulo) e os parâmetros, no formato {@code erro | campo=valor, campo=valor}. */
    static String of(String erro, JsonNode arguments) {
        StringBuilder sb = new StringBuilder();
        if (erro != null && !erro.isBlank()) {
            sb.append(erro.strip());
        }
        String params = params(arguments);
        if (!params.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(params);
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.length() <= MAX_TOTAL ? sb.toString() : sb.substring(0, MAX_TOTAL);
    }

    private static String params(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> it = arguments.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v == null || v.isNull() || v.isContainerNode()) {
                continue;
            }
            String text = SECRET.matcher(e.getKey()).find() ? "***" : v.asText().replaceAll("\\s+", " ").strip();
            if (text.length() > MAX_VALUE) {
                text = text.substring(0, MAX_VALUE) + "…";
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('=').append(text);
        }
        return sb.toString();
    }
}
