package com.sentinel.audit.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/** Com {@code API_KEY} definida, toda a API exige a chave; só saúde, webhooks e MCP (que têm autenticação própria) ficam abertos. */
@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@TestProfile(ApiKeyIntegrationTest.ComChave.class)
@Tag("integration")
class ApiKeyIntegrationTest {

    static final String CHAVE = "chave-de-teste-integracao";

    public static class ComChave implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("sentinel.audit.api-key", CHAVE);
        }
    }

    @Test
    void apiExigeAChaveNosDoisFormatosEDeixaAbertoSoOQueTemAutenticacaoPropria() {
        given().get("/api/v1/systems").then().statusCode(401);
        given().header("X-API-Key", "errada").get("/api/v1/systems").then().statusCode(401);
        given().header("X-API-Key", CHAVE).get("/api/v1/systems").then().statusCode(200);
        given().header("Authorization", "Bearer " + CHAVE).get("/api/v1/systems").then().statusCode(200);

        given().get("/api/v1/health").then().statusCode(200);
        given().contentType("application/json").body("{}").post("/api/v1/webhooks/github").then().statusCode(401);   // do próprio webhook, não da API key
        given().contentType("application/json").body("{}").post("/mcp/inexistente").then().statusCode(401);
    }

    @Test
    void configuracoesEEvidenciasTambemExigemAChave() {
        given().get("/api/v1/settings").then().statusCode(401);
        given().post("/api/v1/settings/restart").then().statusCode(401);
        given().contentType("application/octet-stream").body("x".getBytes(java.nio.charset.StandardCharsets.UTF_8)).put("/api/v1/systems/1/evidence/a.sql").then().statusCode(401);
    }
}
