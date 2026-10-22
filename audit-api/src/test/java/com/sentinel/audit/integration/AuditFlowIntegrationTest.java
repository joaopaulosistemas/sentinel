package com.sentinel.audit.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Fluxo completo contra PostgreSQL real: cadastro, disparo, execução assíncrona e consultas.
 * Exercita o que os testes unitários não alcançam: mapeamento JPA, transações, o início da auditoria
 * logo após o commit e a leitura de dados lazy fora da transação (a causa do bug original).
 * <p>
 * O clone aponta para um endereço inacessível de propósito (não depende de rede): a auditoria deve
 * terminar FAILED com o motivo registrado, e todas as consultas seguintes devem responder normalmente.
 * <p>
 * Roda só com Docker disponível: {@code ./mvnw verify -Pintegration}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@Tag("integration")
class AuditFlowIntegrationTest {

    @Test
    void auditoriaComRepositorioInacessivelTerminaFailedEAPIContinuaConsistente() throws Exception {
        int systemId = given().contentType("application/json")
                .body(Map.of("name", "Sistema IT", "repoUrl", "https://127.0.0.1:1/org/repo.git",
                        "stack", "JAVA", "team", "Time A"))
                .post("/api/v1/systems")
                .then().statusCode(201).extract().path("id");

        int auditId = given().contentType("application/json")
                .body(Map.of("systemId", systemId, "branch", "main", "triggeredBy", "it"))
                .post("/api/v1/audits")
                .then().statusCode(202).extract().path("id");

        String status = "PENDING";
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (!status.equals("FAILED") && !status.equals("COMPLETED") && Instant.now().isBefore(deadline)) {
            Thread.sleep(500);
            status = given().get("/api/v1/audits/" + auditId).then().statusCode(200).extract().path("status");
        }

        given().get("/api/v1/audits/" + auditId).then().statusCode(200)
                .body("status", equalTo("FAILED"))
                .body("errorMessage", not(emptyOrNullString()));

        // consultas que atravessam relações lazy
        given().get("/api/v1/systems").then().statusCode(200).body("name", hasItem("Sistema IT"));
        given().get("/api/v1/systems/" + systemId + "/audits").then().statusCode(200).body("size()", is(1));
        given().get("/api/v1/systems/" + systemId + "/trend").then().statusCode(200);
        given().get("/api/v1/systems/" + systemId + "/suppressions").then().statusCode(200).body("size()", is(0));
        given().get("/api/v1/audits/" + auditId + "/findings").then().statusCode(200);
        given().get("/api/v1/audits/" + auditId + "/report").then().statusCode(200).body(containsString("Sistema IT"));
        given().get("/api/v1/audits/" + auditId + "/sbom").then().statusCode(404);

        // agendamento persiste
        given().contentType("application/json")
                .body(Map.of("auditIntervalHours", 24, "branch", "main", "auditOnPush", true))
                .put("/api/v1/systems/" + systemId + "/schedule")
                .then().statusCode(200).body("auditIntervalHours", is(24)).body("auditOnPush", is(true));

        // edição cadastral mantém o agendamento e devolve os dados novos
        given().contentType("application/json")
                .body(Map.of("name", "Sistema IT 2", "repoUrl", "https://127.0.0.1:1/org/outro.git",
                        "stack", "MIXED", "team", "Time B"))
                .put("/api/v1/systems/" + systemId)
                .then().statusCode(200)
                .body("name", equalTo("Sistema IT 2")).body("team", equalTo("Time B"))
                .body("auditIntervalHours", is(24));
        given().contentType("application/json")
                .body(Map.of("name", "x", "repoUrl", "http://inseguro/repo", "stack", "JAVA"))
                .put("/api/v1/systems/" + systemId).then().statusCode(400);

        // validação de entrada
        given().contentType("application/json")
                .body(Map.of("systemId", systemId, "branch", "--upload-pack=x", "triggeredBy", "it"))
                .post("/api/v1/audits").then().statusCode(400);
    }
}
