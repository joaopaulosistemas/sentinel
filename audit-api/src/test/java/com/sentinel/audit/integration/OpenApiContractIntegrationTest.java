package com.sentinel.audit.integration;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.resource.ApiContractCheck;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /** Rotas que o ApiKeyFilter deixa passar sem a chave global (o MCP tem a chave própria do sistema). */
    private static final List<String> OPEN_PREFIXES = List.of("/api/v1/health", "/api/v1/webhooks/", "/mcp/");
    private static final int MIN_OPERATIONS = 70;

    private static JsonNode document() throws Exception {
        String body = given().get("/q/openapi?format=json").then().statusCode(200).extract().asString();
        return new ObjectMapper().readTree(body);
    }

    /** Guarda o documento do build em target/openapi, de onde o CI valida a estrutura e o publica como artefato. */
    private static void publicar(JsonNode doc) throws Exception {
        java.nio.file.Path destino = java.nio.file.Path.of("target", "openapi", "openapi.json");
        java.nio.file.Files.createDirectories(destino.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(destino.toFile(), doc);
    }

    private static Map<String, JsonNode> operations(JsonNode doc) {
        return ApiContractCheck.operations(doc);
    }

    private static boolean isOpen(String route) {
        String path = route.substring(route.indexOf(' ') + 1);
        return OPEN_PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p));
    }

    private static void assertNoneMissing(String what, List<String> missing) {
        assertTrue(missing.isEmpty(), what + ": " + missing);
    }

    @Test
    void o_contrato_de_toda_rota_esta_completo() throws Exception {
        JsonNode doc = document();
        publicar(doc);
        Map<String, JsonNode> ops = operations(doc);
        System.out.println("OpenAPI: " + ops.size() + " rotas conferidas");
        assertTrue(ops.size() >= MIN_OPERATIONS, "rotas no documento: " + ops.size());
        assertNoneMissing("violações do contrato", ApiContractCheck.violations(doc));
    }

    /** O 404 documentado é o que a API devolve: GET com um só identificador de caminho, para um recurso que não existe. */
    @Test
    void o_404_documentado_e_o_que_a_api_devolve() throws Exception {
        List<String> divergencias = new ArrayList<>();
        for (Map.Entry<String, JsonNode> route : operations(document()).entrySet()) {
            String key = route.getKey();
            String path = key.substring(key.indexOf(' ') + 1);
            if (!key.startsWith("GET ") || isOpen(key) || !path.matches("(/[a-z0-9.-]+)+/\\{id}(/[a-z0-9.-]+)*")
                    || !route.getValue().path("responses").has("404")) {
                continue;
            }
            int status = given().get(path.replace("{id}", "999999999")).statusCode();
            if (status != 404) {
                divergencias.add(key + " devolveu " + status);
            }
        }
        assertNoneMissing("rotas que documentam 404 e respondem outra coisa", divergencias);
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
