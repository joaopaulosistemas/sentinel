package com.sentinel.audit.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.repository.AuditedSystemRepository;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Ponto de entrada do MCP: {@code POST /mcp/<sistema>}, com a chave do sistema em {@code Authorization: Bearer}.
 * <p>
 * Esta rota não usa a API key global (o {@link com.sentinel.audit.resource.ApiKeyFilter} a deixa passar): cada
 * sistema tem a sua chave, que só enxerga esse sistema. Sem chave válida a resposta é sempre a mesma 401, exista o
 * sistema ou não, para não revelar quais sistemas estão cadastrados.
 */
@Path("/mcp/{system}")
public class McpResource {

    private static final Logger LOG = Logger.getLogger(McpResource.class);
    private static final int MAX_BODY_CHARS = 64 * 1024;
    private static final String UNAUTHORIZED_BODY = "{\"error\":\"Chave ausente, inválida ou acesso MCP desligado para este sistema\"}";

    @Inject
    AuditedSystemRepository systemRepository;

    @Inject
    McpGateService gate;

    @Inject
    McpToolService toolService;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String version;

    private McpProtocol protocol;

    @PostConstruct
    void init() {
        protocol = new McpProtocol(mapper, version);
    }

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response post(@PathParam("system") String systemRef,
                         @HeaderParam("Authorization") String authorization,
                         String body) {
        Optional<AuditedSystem> system = findSystem(systemRef);
        McpAccess access = system.map(s -> gate.find(s.getId())).orElse(null);
        if (access == null || !gate.accepts(access, bearer(authorization))) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Bearer")
                    .type(MediaType.APPLICATION_JSON)
                    .entity(UNAUTHORIZED_BODY)
                    .build();
        }
        if (body != null && body.length() > MAX_BODY_CHARS) {
            return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE).build();
        }

        Long systemId = system.get().getId();
        DataLevel level = access.getDataLevel() == null ? DataLevel.TITLES : access.getDataLevel();
        List<McpTool> enabled = gate.enabledTools(access);

        McpBackend backend = new McpBackend() {
            @Override
            public List<McpTool> enabledTools() {
                return enabled;
            }

            @Override
            public ToolResult call(McpTool tool, JsonNode arguments) {
                long start = System.nanoTime();
                ToolResult result;
                String detail = null;
                if (!enabled.contains(tool)) {
                    result = ToolResult.error("Esta ferramenta está desligada para este sistema.");
                    detail = "ferramenta desligada";
                } else {
                    try {
                        result = ToolResult.ok(toolService.run(tool, systemId, level, arguments));
                    } catch (IllegalArgumentException e) {
                        result = ToolResult.error(e.getMessage());
                        detail = e.getMessage();
                    } catch (RuntimeException e) {
                        LOG.errorf(e, "Falha na ferramenta MCP %s do sistema %d", tool.wire(), systemId);
                        result = ToolResult.error("Erro interno ao executar a ferramenta.");
                        detail = "erro interno";
                    }
                }
                long ms = (System.nanoTime() - start) / 1_000_000;
                try {
                    gate.log(systemId, tool.wire(), result.ok(), ms, detail);
                } catch (RuntimeException e) {
                    LOG.warnf("Não foi possível registrar a chamada MCP (%s)", e.getMessage());
                }
                return result;
            }
        };

        Optional<String> reply = protocol.handle(body, backend);
        return reply.map(r -> Response.ok(r, MediaType.APPLICATION_JSON).build())
                .orElseGet(() -> Response.accepted().build());
    }

    /** Este servidor não abre fluxo SSE nem sessão: só responde a POST. */
    @GET
    public Response get() {
        return Response.status(Response.Status.METHOD_NOT_ALLOWED).header("Allow", "POST").build();
    }

    @DELETE
    public Response delete() {
        return Response.status(Response.Status.METHOD_NOT_ALLOWED).header("Allow", "POST").build();
    }

    /** Aceita o nome do sistema (sem diferenciar maiúsculas) ou o número dele; nome repetido não é aceito. */
    private Optional<AuditedSystem> findSystem(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        List<AuditedSystem> byName = systemRepository.list("lower(name) = ?1", ref.trim().toLowerCase());
        if (byName.size() == 1) {
            return Optional.of(byName.get(0));
        }
        if (byName.isEmpty() && ref.trim().matches("\\d{1,18}")) {
            return systemRepository.findByIdOptional(Long.parseLong(ref.trim()));
        }
        return Optional.empty();
    }

    private static String bearer(String authorization) {
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return null;
    }
}
