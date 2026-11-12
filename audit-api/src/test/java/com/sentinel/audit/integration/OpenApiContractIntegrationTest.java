package com.sentinel.audit.integration;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * O contrato OpenAPI gerado pelo build: nenhuma rota fica sem resumo, tag, resposta e forma de autenticação.
 * Uma rota nova (ou alterada) sem isso derruba o teste e a mensagem lista {@code METODO /caminho}.
 * Mesmo requisito dos demais testes de integração: PostgreSQL real via Testcontainers ({@code -Pintegration}).
 */
@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@Tag("integration")
class OpenApiContractIntegrationTest {

    private static final Set<String> VERBS = Set.of("get", "post", "put", "delete", "patch");
    /** Rotas que o ApiKeyFilter deixa passar sem a chave global (o MCP tem a chave própria do sistema). */
    private static final List<String> OPEN_PREFIXES = List.of("/api/v1/health", "/api/v1/webhooks/", "/mcp/");
    private static final int MIN_OPERATIONS = 70;

    private static JsonNode document() throws Exception {
        String body = given().get("/q/openapi?format=json").then().statusCode(200).extract().asString();
        return new ObjectMapper().readTree(body);
    }

    /** "GET /api/v1/systems" -> a operação. */
    private static Map<String, JsonNode> operations(JsonNode doc) {
        Map<String, JsonNode> out = new java.util.TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> paths = doc.get("paths").fields();
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

    private static boolean isOpen(String route) {
        String path = route.substring(route.indexOf(' ') + 1);
        return OPEN_PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p));
    }

    private static void assertNoneMissing(String what, List<String> missing) {
        assertTrue(missing.isEmpty(), what + ": " + missing);
    }

    @Test
    void toda_rota_tem_resumo_tag_e_resposta() throws Exception {
        Map<String, JsonNode> ops = operations(document());
        System.out.println("OpenAPI: " + ops.size() + " rotas conferidas");
        assertTrue(ops.size() >= MIN_OPERATIONS, "rotas no documento: " + ops.size());

        List<String> semResumo = new ArrayList<>();
        List<String> semTag = new ArrayList<>();
        List<String> semResposta = new ArrayList<>();
        ops.forEach((route, op) -> {
            if (!op.hasNonNull("summary") || op.get("summary").asText().isBlank()) {
                semResumo.add(route);
            }
            if (!op.has("tags") || op.get("tags").isEmpty()) {
                semTag.add(route);
            }
            if (!op.has("responses") || op.get("responses").isEmpty()) {
                semResposta.add(route);
            }
        });
        assertNoneMissing("rotas sem resumo", semResumo);
        assertNoneMissing("rotas sem tag", semTag);
        assertNoneMissing("rotas sem resposta", semResposta);
    }

    @Test
    void rota_protegida_documenta_o_401_e_a_aberta_nao_exige_chave() throws Exception {
        JsonNode doc = document();
        List<String> semAutenticacao = new ArrayList<>();
        List<String> abertaComChave = new ArrayList<>();
        operations(doc).forEach((route, op) -> {
            JsonNode security = op.get("security");
            boolean semExigencia = security != null && security.isEmpty();
            if (isOpen(route) && !route.contains("/mcp/") && !semExigencia) {
                abertaComChave.add(route);
            }
            if (!isOpen(route) && !op.get("responses").has("401")) {
                semAutenticacao.add(route);
            }
        });
        assertNoneMissing("rotas protegidas sem a resposta 401", semAutenticacao);
        assertNoneMissing("rotas abertas que exigem chave", abertaComChave);

        assertTrue(doc.at("/components/securitySchemes").has("ApiKeyHeader"));
        assertTrue(doc.at("/components/securitySchemes").has("BearerKey"));
        assertTrue(doc.at("/components/securitySchemes").has("McpSystemKey"));
        assertTrue(doc.at("/components/schemas").has("ApiError"));
        assertTrue(doc.at("/components/schemas").has("ApiKeyError"));
    }

    @Test
    void o_mcp_esta_no_documento() throws Exception {
        Map<String, JsonNode> ops = operations(document());
        assertTrue(ops.containsKey("POST /mcp/{system}"), ops.keySet().toString());
        assertFalse(ops.get("POST /mcp/{system}").get("tags").isEmpty());
    }
}
