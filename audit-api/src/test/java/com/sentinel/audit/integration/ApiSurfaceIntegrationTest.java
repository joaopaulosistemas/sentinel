package com.sentinel.audit.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * A superfície HTTP que os testes unitários não alcançam: MCP (autenticação por chave de sistema e limite de falhas),
 * webhooks (assinatura), configurações (somente leitura e validação) e evidências (pasta não configurada).
 * Mesmo requisito do {@link AuditFlowIntegrationTest}: PostgreSQL real via Testcontainers ({@code -Pintegration}).
 */
@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@Tag("integration")
class ApiSurfaceIntegrationTest {

    private static final String JSON = "application/json";
    private static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"it\",\"version\":\"1\"}}}";

    private static int criaSistema(String nome, String repoUrl, String webhookSecret) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
                "name", nome, "repoUrl", repoUrl, "stack", "JAVA", "team", "Time IT"));
        if (webhookSecret != null) {
            body.put("webhookSecret", webhookSecret);
        }
        return given().contentType(JSON).body(body).post("/api/v1/systems").then().statusCode(201).extract().path("id");
    }

    private static String assinatura(String segredo, String corpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    // ---- MCP ---------------------------------------------------------------------------------------------------

    @Test
    void mcpExigeAChaveDoSistemaELimitaSoQuemErra() {
        int id = criaSistema("Sistema MCP IT", "https://127.0.0.1:1/org/mcp.git", null);

        given().contentType(JSON).body(INITIALIZE).post("/mcp/" + id).then().statusCode(401);   // acesso desligado e sem chave

        String chave = given().contentType(JSON).post("/api/v1/mcp-access/" + id + "/key")
                .then().statusCode(200).body("key", not(emptyOrNullString())).extract().path("key");
        given().contentType(JSON).body(Map.of("enabled", true, "dataLevel", "TITLES"))
                .put("/api/v1/mcp-access/" + id).then().statusCode(200);

        given().contentType(JSON).header("Authorization", "Bearer " + chave).body(INITIALIZE)
                .post("/mcp/" + id).then().statusCode(200).body("jsonrpc", equalTo("2.0")).body("id", is(1));

        // quem erra a chave é freado (401 e, passado o limite de falhas, 429)...
        int ultimo = 0;
        for (int i = 0; i < 15; i++) {
            ultimo = given().contentType(JSON).header("Authorization", "Bearer chave-errada-" + i).body(INITIALIZE)
                    .post("/mcp/" + id).then().extract().statusCode();
        }
        org.junit.jupiter.api.Assertions.assertEquals(429, ultimo);

        // ...mas o cliente legítimo, com a chave certa, não é barrado por isso
        given().contentType(JSON).header("Authorization", "Bearer " + chave).body(INITIALIZE)
                .post("/mcp/" + id).then().statusCode(200);
    }

    @Test
    void chaveRevogadaDeixaDeValer() {
        int id = criaSistema("Sistema MCP Revogado", "https://127.0.0.1:1/org/mcp2.git", null);
        String chave = given().contentType(JSON).post("/api/v1/mcp-access/" + id + "/key").then().statusCode(200).extract().path("key");
        given().contentType(JSON).body(Map.of("enabled", true)).put("/api/v1/mcp-access/" + id).then().statusCode(200);
        given().contentType(JSON).header("Authorization", "Bearer " + chave).body(INITIALIZE).post("/mcp/" + id).then().statusCode(200);

        given().delete("/api/v1/mcp-access/" + id + "/key").then().statusCode(204);

        given().contentType(JSON).header("Authorization", "Bearer " + chave).body(INITIALIZE).post("/mcp/" + id).then().statusCode(401);
    }

    @Test
    void nivelDeDadosInvalidoEBarradoNaConfiguracaoDoMcp() {
        int id = criaSistema("Sistema MCP Nivel", "https://127.0.0.1:1/org/mcp3.git", null);
        given().contentType(JSON).body(Map.of("dataLevel", "TUDO")).put("/api/v1/mcp-access/" + id).then().statusCode(400);
        given().get("/api/v1/mcp-access/999999").then().statusCode(404);
    }

    // ---- webhooks ---------------------------------------------------------------------------------------------

    @Test
    void webhookDoGithubSoAceitaAssinaturaValidaDeUmSistemaComSegredo() throws Exception {
        String repo = "https://127.0.0.1:1/org/webhook.git";
        criaSistema("Sistema Webhook IT", repo, "segredo-do-webhook");
        String corpo = "{\"ref\":\"refs/heads/main\",\"repository\":{\"clone_url\":\"" + repo + "\"}}";

        given().contentType(JSON).header("X-GitHub-Event", "ping").body(corpo)
                .post("/api/v1/webhooks/github").then().statusCode(401);                                  // sem assinatura
        given().contentType(JSON).header("X-GitHub-Event", "ping")
                .header("X-Hub-Signature-256", assinatura("outro-segredo", corpo)).body(corpo)
                .post("/api/v1/webhooks/github").then().statusCode(401);                                  // segredo errado
        given().contentType(JSON).header("X-GitHub-Event", "ping")
                .header("X-Hub-Signature-256", assinatura("segredo-do-webhook", corpo)).body(corpo)
                .post("/api/v1/webhooks/github").then().statusCode(200).body("message", equalTo("pong"));
        given().contentType(JSON).header("X-GitHub-Event", "issues")
                .header("X-Hub-Signature-256", assinatura("segredo-do-webhook", corpo)).body(corpo)
                .post("/api/v1/webhooks/github").then().statusCode(200).body("triggered", is(0));        // evento ignorado
    }

    @Test
    void webhookComCorpoQueNaoEJsonDa400ESemSistemaDa401() throws Exception {
        given().contentType(JSON).body("isto não é json").post("/api/v1/webhooks/github").then().statusCode(400);
        String corpo = "{\"repository\":{\"clone_url\":\"https://127.0.0.1:1/org/desconhecido.git\"}}";
        given().contentType(JSON).header("X-Gitlab-Token", "qualquer").body(corpo)
                .post("/api/v1/webhooks/gitlab").then().statusCode(401);
    }

    // ---- configurações ----------------------------------------------------------------------------------------

    @Test
    void configuracoesProtegidasSaoSomenteLeituraEValoresInvalidosSaoRecusados() {
        given().get("/api/v1/settings").then().statusCode(200).body("settings.size()", greaterThan(10));

        given().contentType(JSON).body(Map.of("values", Map.of("API_KEY", "x")))
                .put("/api/v1/settings").then().statusCode(400).body(containsString("API_KEY"));
        given().contentType(JSON).body(Map.of("values", Map.of("CLEANCODE_MAX_NESTING", "abc")))
                .put("/api/v1/settings").then().statusCode(400);
        given().delete("/api/v1/settings/NAO_EXISTE").then().statusCode(400);
    }

    // ---- evidências --------------------------------------------------------------------------------------------

    @Test
    void semPastaDeEvidenciasConfiguradaOEnvioDa409() {
        int id = criaSistema("Sistema Evidencia IT", "https://127.0.0.1:1/org/evid.git", null);

        given().get("/api/v1/systems/" + id + "/evidence").then().statusCode(200).body("enabled", is(false));
        given().contentType("application/octet-stream").body("CREATE TABLE a (x int);".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .put("/api/v1/systems/" + id + "/evidence/esquema.sql").then().statusCode(409);
        given().get("/api/v1/systems/999999/evidence").then().statusCode(404);
    }
}
