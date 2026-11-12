package com.sentinel.audit.resource;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * As regras do contrato OpenAPI, sobre o documento já em JSON: cada rota tem resumo, descrição, tag e resposta de sucesso
 * com tipo de mídia (e esquema quando é JSON); as rotas com parâmetro de caminho documentam o 404 e as com corpo o 400,
 * ambos num dos dois formatos de erro ({@code ApiError} ou {@code ApiKeyError}); o MCP pode responder só 405; todo parâmetro tem descrição. Devolve uma linha por violação, no formato
 * {@code METODO /caminho: o que falta}, e a lista vazia quando o contrato está completo.
 */
public final class ApiContractCheck {

    private static final Set<String> VERBS = Set.of("get", "post", "put", "delete", "patch");
    private static final List<String> OUTSIDE_STANDARD_ERRORS = List.of("/api/v1/health", "/api/v1/webhooks/", "/mcp/");
    private static final String JSON = "application/json";

    private ApiContractCheck() {
    }

    /** "GET /api/v1/systems" -> a operação, em ordem estável. */
    public static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> out = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> paths = document.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            Iterator<Map.Entry<String, JsonNode>> items = path.getValue().fields();
            while (items.hasNext()) {
                Map.Entry<String, JsonNode> item = items.next();
                if (VERBS.contains(item.getKey())) {
                    out.put(item.getKey().toUpperCase() + " " + path.getKey(), item.getValue());
                }
            }
        }
        return out;
    }

    public static List<String> violations(JsonNode document) {
        List<String> found = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> paths = document.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            String pathName = path.getKey();
            checkParameters("path " + pathName, path.getValue().path("parameters"), found);
            Iterator<Map.Entry<String, JsonNode>> items = path.getValue().fields();
            while (items.hasNext()) {
                Map.Entry<String, JsonNode> item = items.next();
                if (VERBS.contains(item.getKey())) {
                    checkOperation(item.getKey().toUpperCase() + " " + pathName, pathName, item.getValue(), found);
                }
            }
        }
        return found;
    }

    private static void checkOperation(String route, String path, JsonNode op, List<String> found) {
        if (blank(op.path("summary"))) {
            found.add(route + ": sem resumo");
        }
        if (blank(op.path("description"))) {
            found.add(route + ": sem descrição");
        }
        if (!op.has("tags") || op.get("tags").isEmpty()) {
            found.add(route + ": sem tag");
        }
        JsonNode responses = op.path("responses");
        if (responses.isEmpty()) {
            found.add(route + ": sem resposta");
            return;
        }
        checkSuccess(route, responses, found);
        checkParameters(route, op.path("parameters"), found);
        if (OUTSIDE_STANDARD_ERRORS.stream().anyMatch(path::startsWith)) {
            return;
        }
        if (path.contains("{") && !isApiError(responses.path("404"))) {
            found.add(route + ": sem 404 com o formato ApiError");
        }
        if (op.has("requestBody") && !isApiError(responses.path("400"))) {
            found.add(route + ": sem 400 com o formato ApiError");
        }
    }

    /** Toda resposta 2xx devolve conteúdo com tipo de mídia, menos 202 e 204 sem corpo; o JSON traz o esquema. */
    private static void checkSuccess(String route, JsonNode responses, List<String> found) {
        boolean anySuccess = false;
        Iterator<Map.Entry<String, JsonNode>> it = responses.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> response = it.next();
            String code = response.getKey();
            if (!code.startsWith("2")) {
                continue;
            }
            anySuccess = true;
            JsonNode content = response.getValue().path("content");
            if (content.isEmpty()) {
                if (!code.equals("204") && !code.equals("202")) {
                    found.add(route + ": resposta " + code + " sem tipo de mídia");
                }
                continue;
            }
            JsonNode json = content.path(JSON);
            if (json.isMissingNode()) {
                continue;
            }
            if (!json.has("schema")) {
                found.add(route + ": resposta " + code + " em JSON sem esquema");
            }
        }
        if (!anySuccess && !route.contains(" /mcp/")) {
            found.add(route + ": sem resposta de sucesso");
        }
    }

    private static void checkParameters(String where, JsonNode parameters, List<String> found) {
        for (JsonNode parameter : parameters) {
            if (blank(parameter.path("description"))) {
                found.add(where + ": parâmetro " + parameter.path("name").asText("?") + " sem descrição");
            }
        }
    }

    private static boolean isApiError(JsonNode response) {
        JsonNode ref = response.path("content").path(JSON).path("schema").path("$ref");
        String name = ref.asText("");
        return name.endsWith("/ApiError") || name.endsWith("/ApiKeyError");
    }

    private static boolean blank(JsonNode node) {
        return !node.isTextual() || node.asText().isBlank();
    }
}
