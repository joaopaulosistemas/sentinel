package com.sentinel.audit.mcp;

import jakarta.annotation.security.PermitAll;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.service.SystemCatalog;
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
// Acesso: fora de /api/*; autenticado aqui dentro pela chave do sistema (guardada como hash), não pela API key global.
@PermitAll
@Path("/mcp/{system}")
public class McpResource {

    private static final long NANOS_PER_MILLI = 1_000_000;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final String BEARER_PREFIX = "Bearer ";


    private static final Logger LOG = Logger.getLogger(McpResource.class);
    private static final int MAX_BODY_CHARS = 64 * 1024;
    private static final String UNAUTHORIZED_BODY = "{\"error\":\"Chave ausente, inválida ou acesso MCP desligado para este sistema\"}";

    @Inject
    SystemCatalog systemCatalog;

    @Inject
    McpGateService gate;

    @Inject
    McpToolService toolService;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String version;

    @ConfigProperty(name = "sentinel.mcp.rate-per-minute", defaultValue = "60")
    int ratePerMinute;

    @ConfigProperty(name = "sentinel.mcp.failed-per-minute", defaultValue = "10")
    int failedPerMinute;

    private McpProtocol protocol;
    private McpRateLimiter callLimiter;
    private McpRateLimiter failureLimiter;

    @PostConstruct
    void init() {
        protocol = new McpProtocol(mapper, version);
        callLimiter = new McpRateLimiter(ratePerMinute);
        failureLimiter = new McpRateLimiter(failedPerMinute);
    }

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response post(@PathParam("system") String systemRef,
                         @HeaderParam("Authorization") String authorization,
                         String body) {
        String ref = systemRef == null ? "" : systemRef.trim().toLowerCase(java.util.Locale.ROOT);
        if (failureLimiter.blocked("fail:" + ref)) {
            return tooMany(failureLimiter.retryAfterSeconds("fail:" + ref));
        }
        Optional<AuditedSystem> system = findSystem(systemRef);
        McpAccess access = system.map(s -> gate.find(s.getId())).orElse(null);
        if (access == null || !gate.accepts(access, bearer(authorization))) {
            failureLimiter.tryAcquire("fail:" + ref);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Bearer")
                    .type(MediaType.APPLICATION_JSON)
                    .entity(UNAUTHORIZED_BODY)
                    .build();
        }
        if (!callLimiter.tryAcquire("ok:" + system.get().getId())) {
            return tooMany(callLimiter.retryAfterSeconds("ok:" + system.get().getId()));
        }
        if (body != null && body.length() > MAX_BODY_CHARS) {
            return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE).build();
        }

        Long systemId = system.get().getId();
        DataLevel level = access.getDataLevel() == null ? DataLevel.TITLES : access.getDataLevel();
        McpBackend backend = new SystemBackend(gate.enabledTools(access), systemId, level);

        Optional<String> reply = protocol.handle(body, backend);
        return reply.map(r -> Response.ok(r, MediaType.APPLICATION_JSON).build())
                .orElseGet(() -> Response.accepted().build());
    }

    /** As ferramentas de um sistema: só as ligadas para ele, cada chamada registrada no log de uso. */
    private final class SystemBackend implements McpBackend {
        private final List<McpTool> enabled;
        private final Long systemId;
        private final DataLevel level;

        SystemBackend(List<McpTool> enabled, Long systemId, DataLevel level) {
            this.enabled = enabled;
            this.systemId = systemId;
            this.level = level;
        }

        @Override
        public List<McpTool> enabledTools() {
            return enabled;
        }

        @Override
        public ToolResult call(McpTool tool, JsonNode arguments) {
            long start = System.nanoTime();
            Outcome outcome = execute(tool, arguments);
            long ms = (System.nanoTime() - start) / NANOS_PER_MILLI;
            try {
                gate.log(systemId, tool.wire(), outcome.result().ok(), ms, McpCallSummary.of(outcome.detail(), arguments));
            } catch (RuntimeException e) {
                LOG.warnf("Não foi possível registrar a chamada MCP (%s)", e.getMessage());
            }
            return outcome.result();
        }

        private Outcome execute(McpTool tool, JsonNode arguments) {
            if (!enabled.contains(tool)) {
                return new Outcome(ToolResult.error("Esta ferramenta está desligada para este sistema."), "ferramenta desligada");
            }
            try {
                return new Outcome(ToolResult.ok(toolService.run(tool, systemId, level, arguments)), null);
            } catch (IllegalArgumentException e) {
                return new Outcome(ToolResult.error(e.getMessage()), e.getMessage());
            } catch (RuntimeException e) {
                LOG.errorf(e, "Falha na ferramenta MCP %s do sistema %d", tool.wire(), systemId);
                return new Outcome(ToolResult.error("Erro interno ao executar a ferramenta."), "erro interno");
            }
        }
    }

    /** Resultado da chamada e o motivo (se houve falha) que vai para o log. */
    private record Outcome(McpBackend.ToolResult result, String detail) { }

    private static Response tooMany(int seconds) {
        return Response.status(HTTP_TOO_MANY_REQUESTS)
                .header("Retry-After", seconds)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"Muitas chamadas em pouco tempo. Tente de novo em " + seconds + " s.\"}")
                .build();
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
        List<AuditedSystem> byName = systemCatalog.byLowerName(ref.trim().toLowerCase(java.util.Locale.ROOT));
        if (byName.size() == 1) {
            return Optional.of(byName.get(0));
        }
        if (byName.isEmpty() && ref.trim().matches("\\d{1,18}")) {
            return systemCatalog.find(Long.parseLong(ref.trim()));
        }
        return Optional.empty();
    }

    private static String bearer(String authorization) {
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
