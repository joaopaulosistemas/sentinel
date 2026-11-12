package com.sentinel.audit.resource;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;

/**
 * Completa o documento OpenAPI com o que a API faz e as anotações das rotas não dizem: as formas de autenticação, o
 * formato dos dois erros padrão e o 401 das rotas protegidas. Só acrescenta ao documento; o comportamento da API não muda.
 * Registrado em {@code mp.openapi.filter}. Quais rotas ficam fora da chave global vem de {@link ApiKeyFilter#isOpenPath}.
 */
public class SentinelOpenApiFilter implements OASFilter {

    static final String API_KEY = "ApiKeyHeader";
    static final String BEARER = "BearerKey";
    static final String MCP_KEY = "McpSystemKey";
    static final String API_ERROR = "ApiError";
    static final String API_KEY_ERROR = "ApiKeyError";

    private static final String JSON = "application/json";
    private static final String MCP_PREFIX = "mcp/";

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        if (openAPI.getComponents() == null) {
            openAPI.setComponents(OASFactory.createComponents());
        }
        addSecuritySchemes(openAPI);
        addErrorSchemas(openAPI);
        openAPI.addSecurityRequirement(requirement(API_KEY));
        openAPI.addSecurityRequirement(requirement(BEARER));
        if (openAPI.getPaths() == null || openAPI.getPaths().getPathItems() == null) {
            return;
        }
        for (Map.Entry<String, PathItem> path : openAPI.getPaths().getPathItems().entrySet()) {
            for (Operation operation : path.getValue().getOperations().values()) {
                applySecurity(path.getKey(), operation);
            }
        }
    }

    private static void addSecuritySchemes(OpenAPI openAPI) {
        openAPI.getComponents().addSecurityScheme(API_KEY, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")
                .description("Chave da API (variável API_KEY do servidor). Sem chave configurada a API fica aberta."));
        openAPI.getComponents().addSecurityScheme(BEARER, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                .description("A mesma chave da API, enviada como Authorization: Bearer <chave>."));
        openAPI.getComponents().addSecurityScheme(MCP_KEY, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                .description("Chave de acesso MCP do sistema (gerada na aba MCP do sistema); enxerga só esse sistema."));
    }

    private static void addErrorSchemas(OpenAPI openAPI) {
        openAPI.getComponents().addSchema(API_ERROR, OASFactory.createSchema()
                .addType(Schema.SchemaType.OBJECT)
                .description("Erro padrão da API (400, 404, 409...): a mensagem em português.")
                .addProperty("message", OASFactory.createSchema().addType(Schema.SchemaType.STRING)
                        .example("Auditoria não encontrada: 99")));
        openAPI.getComponents().addSchema(API_KEY_ERROR, OASFactory.createSchema()
                .addType(Schema.SchemaType.OBJECT)
                .description("Erro de autenticação (401): chave ausente ou inválida.")
                .addProperty("error", OASFactory.createSchema().addType(Schema.SchemaType.STRING)
                        .example("API key ausente ou inválida")));
    }

    /** Rotas abertas ficam sem exigência de chave global; o MCP tem a chave própria do sistema; as demais ganham o 401. */
    private static void applySecurity(String path, Operation operation) {
        String normalized = path.replaceAll("^/+", "");
        if (normalized.startsWith(MCP_PREFIX)) {
            operation.setSecurity(List.of(requirement(MCP_KEY)));
        } else if (ApiKeyFilter.isOpenPath(path)) {
            operation.setSecurity(List.of());
        } else if (operation.getResponses() != null && !operation.getResponses().getAPIResponses().containsKey("401")) {
            operation.getResponses().addAPIResponse("401", OASFactory.createAPIResponse()
                    .description("Chave da API ausente ou inválida (só quando a API tem chave configurada).")
                    .content(OASFactory.createContent().addMediaType(JSON, OASFactory.createMediaType()
                            .schema(OASFactory.createSchema().ref(API_KEY_ERROR)))));
        }
    }

    private static SecurityRequirement requirement(String scheme) {
        return OASFactory.createSecurityRequirement().addScheme(scheme);
    }
}
