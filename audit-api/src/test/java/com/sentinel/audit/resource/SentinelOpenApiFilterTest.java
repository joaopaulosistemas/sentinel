package com.sentinel.audit.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.junit.jupiter.api.Test;

/** O filtro completa o documento com a autenticação e o 401, e deixa de fora só o que o ApiKeyFilter deixa passar sem chave. */
class SentinelOpenApiFilterTest {

    private static OpenAPI documentoCom(String... caminhos) {
        OpenAPI api = OASFactory.createOpenAPI();
        api.setPaths(OASFactory.createPaths());
        for (String caminho : caminhos) {
            Operation get = OASFactory.createOperation().summary("x").responses(OASFactory.createAPIResponses()
                    .addAPIResponse("200", OASFactory.createAPIResponse().description("ok")));
            api.getPaths().addPathItem(caminho, OASFactory.createPathItem().GET(get));
        }
        return api;
    }

    private static Operation get(OpenAPI api, String caminho) {
        PathItem item = api.getPaths().getPathItem(caminho);
        return item.getGET();
    }

    @Test
    void declaraAsTresFormasDeChaveEOsDoisErrosPadrao() {
        OpenAPI api = documentoCom("/api/v1/systems");
        new SentinelOpenApiFilter().filterOpenAPI(api);

        assertTrue(api.getComponents().getSecuritySchemes().keySet()
                .containsAll(java.util.List.of("ApiKeyHeader", "BearerKey", "McpSystemKey")));
        assertEquals("X-API-Key", api.getComponents().getSecuritySchemes().get("ApiKeyHeader").getName());
        assertTrue(api.getComponents().getSchemas().keySet().containsAll(java.util.List.of("ApiError", "ApiKeyError")));
        assertEquals(2, api.getSecurity().size(), "chave no cabeçalho OU Bearer");
    }

    @Test
    void rotaProtegidaGanhaO401ESegueComAChaveGlobal() {
        OpenAPI api = documentoCom("/api/v1/systems");
        new SentinelOpenApiFilter().filterOpenAPI(api);

        Operation op = get(api, "/api/v1/systems");
        assertNotNull(op.getResponses().getAPIResponse("401"));
        assertTrue(op.getResponses().getAPIResponse("200") != null, "o 200 que já existia continua");
        assertTrue(op.getSecurity() == null, "sem exigência própria: vale a global");
    }

    @Test
    void saudeEWebhooksFicamSemChaveEOMcpUsaAChaveDoSistema() {
        OpenAPI api = documentoCom("/api/v1/health", "/api/v1/webhooks/github", "/mcp/{system}");
        new SentinelOpenApiFilter().filterOpenAPI(api);

        assertTrue(get(api, "/api/v1/health").getSecurity().isEmpty());
        assertTrue(get(api, "/api/v1/webhooks/github").getSecurity().isEmpty());
        assertEquals(1, get(api, "/mcp/{system}").getSecurity().size());
        assertTrue(get(api, "/mcp/{system}").getSecurity().get(0).hasScheme("McpSystemKey"));
        assertTrue(get(api, "/api/v1/health").getResponses().getAPIResponse("401") == null, "rota aberta não ganha 401 da chave");
    }

    @Test
    void naoSobrescreveUm401JaDocumentadoNaRota() {
        OpenAPI api = documentoCom("/api/v1/systems");
        get(api, "/api/v1/systems").getResponses().addAPIResponse("401", OASFactory.createAPIResponse().description("minha"));
        new SentinelOpenApiFilter().filterOpenAPI(api);

        assertEquals("minha", get(api, "/api/v1/systems").getResponses().getAPIResponse("401").getDescription());
    }
}
