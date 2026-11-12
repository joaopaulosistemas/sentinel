package com.sentinel.audit.resource;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;

/**
 * Completa o documento OpenAPI com o que a API faz e as anotações das rotas não dizem: as formas de autenticação, o
 * formato dos dois erros padrão e o 401 das rotas protegidas. Só acrescenta ao documento; o comportamento da API não muda.
 * Registrado em {@code mp.openapi.filter}. Quais rotas ficam fora da chave global vem de {@link ApiKeyFilter#isOpenPath}.
 */
public class SentinelOpenApiFilter implements OASFilter {

    static final String SCHEME_HEADER = "ApiKeyHeader";
    static final String SCHEME_BEARER = "BearerKey";
    static final String SCHEME_MCP = "McpSystemKey";
    static final String SCHEMA_ERROR = "ApiError";
    static final String SCHEMA_AUTH_ERROR = "ApiKeyError";

    private static final String JSON = "application/json";
    private static final String MCP_PREFIX = "mcp/";

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        if (openAPI.getComponents() == null) {
            openAPI.setComponents(OASFactory.createComponents());
        }
        addSecuritySchemes(openAPI);
        addErrorSchemas(openAPI);
        openAPI.addSecurityRequirement(requirement(SCHEME_HEADER));
        openAPI.addSecurityRequirement(requirement(SCHEME_BEARER));
        if (openAPI.getPaths() == null || openAPI.getPaths().getPathItems() == null) {
            return;
        }
        for (Map.Entry<String, PathItem> path : openAPI.getPaths().getPathItems().entrySet()) {
            for (Operation operation : path.getValue().getOperations().values()) {
                applySecurity(path.getKey(), operation);
                completeErrors(path.getKey(), operation);
                describeParameters(operation.getParameters());
            }
            describeParameters(path.getValue().getParameters());
        }
    }

    private static void addSecuritySchemes(OpenAPI openAPI) {
        openAPI.getComponents().addSecurityScheme(SCHEME_HEADER, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")
                .description("Chave da API (variável SCHEME_HEADER do servidor). Sem chave configurada a API fica aberta."));
        openAPI.getComponents().addSecurityScheme(SCHEME_BEARER, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                .description("A mesma chave da API, enviada como Authorization: Bearer <chave>."));
        openAPI.getComponents().addSecurityScheme(SCHEME_MCP, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                .description("Chave de acesso MCP do sistema (gerada na aba MCP do sistema); enxerga só esse sistema."));
    }

    private static void addErrorSchemas(OpenAPI openAPI) {
        openAPI.getComponents().addSchema(SCHEMA_ERROR, OASFactory.createSchema()
                .addType(Schema.SchemaType.OBJECT)
                .description("Erro padrão da API (400, 404, 409...): a mensagem em português.")
                .addProperty("message", OASFactory.createSchema().addType(Schema.SchemaType.STRING)
                        .example("Auditoria não encontrada: 99")));
        openAPI.getComponents().addSchema(SCHEMA_AUTH_ERROR, OASFactory.createSchema()
                .addType(Schema.SchemaType.OBJECT)
                .description("Erro no formato {\"error\": ...}: o 401 de chave ausente ou inválida e as rotas que respondem assim (webhooks, laboratório de regras, CLAUDE.md).")
                .addProperty("error", OASFactory.createSchema().addType(Schema.SchemaType.STRING)
                        .example("API key ausente ou inválida")));
    }

    /** Rotas abertas ficam sem exigência de chave global; o MCP tem a chave própria do sistema; as demais ganham o 401. */
    private static void applySecurity(String path, Operation operation) {
        String normalized = path.replaceAll("^/+", "");
        if (normalized.startsWith(MCP_PREFIX)) {
            operation.setSecurity(List.of(requirement(SCHEME_MCP)));
        } else if (ApiKeyFilter.isOpenPath(path)) {
            operation.setSecurity(List.of());
        } else if (operation.getResponses() != null && !operation.getResponses().getAPIResponses().containsKey("401")) {
            operation.getResponses().addAPIResponse("401", OASFactory.createAPIResponse()
                    .description("Chave da API ausente ou inválida (só quando a API tem chave configurada).")
                    .content(OASFactory.createContent().addMediaType(JSON, OASFactory.createMediaType()
                            .schema(OASFactory.createSchema().ref(SCHEMA_AUTH_ERROR)))));
        }
    }

    /**
     * Rotas protegidas com parâmetro de caminho respondem 404 quando o recurso não existe e as com corpo respondem 400
     * quando ele é inválido; os dois erros usam o formato {@code ApiError}. Só preenche o que as anotações não disseram.
     */
    private static void completeErrors(String path, Operation operation) {
        if (isOutsideStandardErrors(path) || operation.getResponses() == null) {
            return;
        }
        if (path.contains("{")) {
            ensureError(operation, "404", "Recurso não encontrado.");
        }
        if (operation.getRequestBody() != null) {
            ensureError(operation, "400", "Corpo da requisição inválido.");
        }
        for (String code : List.of("400", "404", "409")) {
            org.eclipse.microprofile.openapi.models.responses.APIResponse response =
                    operation.getResponses().getAPIResponse(code);
            if (response != null && response.getContent() == null) {
                response.setContent(errorContent());
            }
        }
    }

    private static boolean isOutsideStandardErrors(String path) {
        return path.replaceAll("^/+", "").startsWith(MCP_PREFIX) || ApiKeyFilter.isOpenPath(path);
    }

    private static void ensureError(Operation operation, String code, String description) {
        if (operation.getResponses().getAPIResponse(code) == null) {
            operation.getResponses().addAPIResponse(code,
                    OASFactory.createAPIResponse().description(description).content(errorContent()));
        }
    }

    private static org.eclipse.microprofile.openapi.models.media.Content errorContent() {
        return OASFactory.createContent().addMediaType(JSON, OASFactory.createMediaType()
                .schema(OASFactory.createSchema().ref(SCHEMA_ERROR)));
    }

    /** Descrição dos parâmetros pelo nome, para os que as anotações não descreveram. */
    private static void describeParameters(List<Parameter> parameters) {
        if (parameters == null) {
            return;
        }
        for (Parameter parameter : parameters) {
            if ((parameter.getDescription() == null || parameter.getDescription().isBlank()) && parameter.getName() != null) {
                String text = PARAMETER_DOCS.get(parameter.getName());
                if (text != null) {
                    parameter.setDescription(text);
                }
            }
        }
    }

    static final Map<String, String> PARAMETER_DOCS = Map.ofEntries(
            Map.entry("id", "Identificador numérico do recurso do caminho (sistema, auditoria ou finding, conforme a rota)."),
            Map.entry("rid", "Identificador numérico da auditoria."),
            Map.entry("systemId", "Restringe ao sistema com este identificador."),
            Map.entry("system", "Identificador ou nome do sistema."),
            Map.entry("sid", "Identificador numérico do sistema."),
            Map.entry("actionId", "Identificador do pedido do Claude."),
            Map.entry("suppressionId", "Identificador numérico da supressão."),
            Map.entry("name", "Nome do recurso (por exemplo, do arquivo de evidência)."),
            Map.entry("key", "Chave da configuração."),
            Map.entry("stack", "Identificador da tecnologia do guia."),
            Map.entry("limit", "Quantidade máxima de itens devolvidos."),
            Map.entry("offset", "Quantos itens pular antes de começar a lista."),
            Map.entry("days", "Limita o período, em dias."),
            Map.entry("type", "Tipo do relatório ou do item, conforme a rota."),
            Map.entry("format", "Formato da resposta."),
            Map.entry("status", "Filtra pelo estado."),
            Map.entry("severity", "Filtra pela severidade."),
            Map.entry("category", "Filtra pela categoria."),
            Map.entry("onlyNew", "Só o que apareceu pela primeira vez na auditoria."),
            Map.entry("includeSuppressed", "Inclui também os findings suprimidos."),
            Map.entry("without", "Itens a deixar de fora da resposta."),
            Map.entry("X-Hub-Signature-256", "Assinatura HMAC do corpo, calculada com o segredo do webhook do sistema."),
            Map.entry("X-Gitlab-Token", "Token do webhook configurado no GitLab."),
            Map.entry("X-GitHub-Event", "Tipo do evento enviado pelo GitHub (ping, push...)."),
            Map.entry("Authorization", "Chave de acesso no formato Bearer <chave>."));

    private static SecurityRequirement requirement(String scheme) {
        return OASFactory.createSecurityRequirement().addScheme(scheme);
    }
}
