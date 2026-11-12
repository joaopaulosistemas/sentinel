package com.sentinel.audit.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cada regra do contrato é provada com um documento sintético que a faz falhar; o completo não acusa nada. */
class ApiContractCheckTest {

    private static final String ERRO = "{\"content\":{\"application/json\":{\"schema\":{\"$ref\":\"#/components/schemas/ApiError\"}}}}";
    private static final String OK_JSON = "{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\"}}}}";

    private static JsonNode doc(String operation) throws Exception {
        return new ObjectMapper().readTree("{\"paths\":{\"/api/v1/x/{id}\":{\"get\":" + operation + "}}}");
    }

    private static String op(String summary, String description, String tags, String responses, String extra) {
        return "{" + (summary == null ? "" : "\"summary\":\"" + summary + "\",")
                + (description == null ? "" : "\"description\":\"" + description + "\",")
                + (tags == null ? "" : "\"tags\":" + tags + ",")
                + extra + "\"responses\":" + responses + "}";
    }

    private static String completeResponses() {
        return "{\"200\":" + OK_JSON + ",\"404\":" + ERRO + "}";
    }

    private static final String PARAM_OK = "\"parameters\":[{\"name\":\"id\",\"description\":\"Identificador\"}],";

    private static List<String> check(String operation) throws Exception {
        return ApiContractCheck.violations(doc(operation));
    }

    @Test
    void documentoCompletoNaoTemViolacao() throws Exception {
        assertEquals(List.of(), check(op("r", "d", "[\"T\"]", completeResponses(), PARAM_OK)));
    }

    @Test
    void acusaRotaSemResumoDescricaoOuTag() throws Exception {
        List<String> v = check(op(null, null, null, completeResponses(), PARAM_OK));
        assertTrue(v.contains("GET /api/v1/x/{id}: sem resumo"), v.toString());
        assertTrue(v.contains("GET /api/v1/x/{id}: sem descrição"), v.toString());
        assertTrue(v.contains("GET /api/v1/x/{id}: sem tag"), v.toString());
    }

    @Test
    void acusaSucessoSemTipoDeMidiaOuSemEsquemaJson() throws Exception {
        List<String> semMidia = check(op("r", "d", "[\"T\"]", "{\"200\":{},\"404\":" + ERRO + "}", PARAM_OK));
        assertTrue(semMidia.toString().contains("resposta 200 sem tipo de mídia"), semMidia.toString());
        List<String> semEsquema = check(op("r", "d", "[\"T\"]",
                "{\"200\":{\"content\":{\"application/json\":{}}},\"404\":" + ERRO + "}", PARAM_OK));
        assertTrue(semEsquema.toString().contains("resposta 200 em JSON sem esquema"), semEsquema.toString());
    }

    @Test
    void aceitaSucessoSemCorpoEm204E202() throws Exception {
        assertEquals(List.of(), check(op("r", "d", "[\"T\"]", "{\"204\":{},\"404\":" + ERRO + "}", PARAM_OK)));
        assertEquals(List.of(), check(op("r", "d", "[\"T\"]", "{\"202\":{},\"404\":" + ERRO + "}", PARAM_OK)));
    }

    @Test
    void acusaRotaSemRespostaDeSucesso() throws Exception {
        List<String> v = check(op("r", "d", "[\"T\"]", "{\"404\":" + ERRO + "}", PARAM_OK));
        assertTrue(v.toString().contains("sem resposta de sucesso"), v.toString());
    }

    @Test
    void exige404ComApiErrorQuandoHaParametroDeCaminho() throws Exception {
        List<String> semNada = check(op("r", "d", "[\"T\"]", "{\"200\":" + OK_JSON + "}", PARAM_OK));
        assertTrue(semNada.toString().contains("sem 404 com formato de erro"), semNada.toString());
        List<String> semContent = check(op("r", "d", "[\"T\"]", "{\"200\":" + OK_JSON + ",\"404\":{}}", PARAM_OK));
        assertTrue(semContent.toString().contains("sem 404 com formato de erro"), semContent.toString());
    }

    @Test
    void exige400ComApiErrorQuandoHaCorpo() throws Exception {
        List<String> v = check(op("r", "d", "[\"T\"]", completeResponses(), PARAM_OK + "\"requestBody\":{},"));
        assertTrue(v.toString().contains("sem 400 com formato de erro"), v.toString());
    }

    @Test
    void aceitaOFormatoDeErroComChaveError() throws Exception {
        String erro = ERRO.replace("/ApiError", "/ApiKeyError");
        assertEquals(List.of(), check(op("r", "d", "[\"T\"]", "{\"200\":" + OK_JSON + ",\"404\":" + erro + "}", PARAM_OK)));
    }

    @Test
    void mcpPodeResponderSoErro405() throws Exception {
        JsonNode doc = new ObjectMapper().readTree("{\"paths\":{\"/mcp/{system}\":{\"get\":"
                + op("r", "d", "[\"T\"]", "{\"405\":{}}", PARAM_OK) + "}}}");
        assertEquals(List.of(), ApiContractCheck.violations(doc));
    }

    @Test
    void acusaParametroSemDescricao() throws Exception {
        List<String> v = check(op("r", "d", "[\"T\"]", completeResponses(), "\"parameters\":[{\"name\":\"id\"}],"));
        assertTrue(v.contains("GET /api/v1/x/{id}: parâmetro id sem descrição"), v.toString());
    }

    @Test
    void rotasAbertasEMcpFicamForaDoFormatoApiError() throws Exception {
        JsonNode doc = new ObjectMapper().readTree("{\"paths\":{\"/api/v1/webhooks/{x}\":{\"post\":"
                + op("r", "d", "[\"T\"]", "{\"200\":" + OK_JSON + "}", PARAM_OK + "\"requestBody\":{},") + "}}}");
        assertEquals(List.of(), ApiContractCheck.violations(doc));
    }
}
