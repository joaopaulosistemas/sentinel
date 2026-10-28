package com.sentinel.audit.mcp;

import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Arrays;
import java.util.List;

/**
 * Configuração do acesso MCP de cada sistema, usada pela aba "Claude (MCP)" do dashboard.
 * Fica sob a API key global, como o resto de {@code /api/v1}; o que a chave do sistema abre é só {@code /mcp/<sistema>}.
 * O caminho é próprio (e não filho de {@code /api/v1/systems}) para não disputar rota com o SystemResource.
 */
@Path("/api/v1/mcp-access/{id}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "MCP", description = "Acesso do Claude (MCP) aos resultados de um sistema")
public class McpAdminResource {

    public record ToolState(String name, String description, boolean writes, boolean enabled) {}

    public record McpConfigResponse(boolean enabled, boolean hasKey, String keyPrefix, String keyCreatedAt,
                                    DataLevel dataLevel, List<ToolState> tools) {}

    public record McpConfigRequest(Boolean enabled, String dataLevel, List<String> tools) {}

    /** A chave aparece aqui uma única vez; depois só o começo dela. */
    public record NewKeyResponse(String key, String keyPrefix) {}

    public record CallResponse(String tool, boolean ok, long durationMs, String detail, String at) {}

    @Inject
    SystemCatalog systemCatalog;

    @Inject
    McpGateService gate;

    @GET
    @Operation(summary = "Configuração do acesso MCP do sistema")
    public McpConfigResponse get(@PathParam("id") Long id) {
        requireSystem(id);
        return view(gate.find(id));
    }

    @PUT
    @Operation(summary = "Ligar ou desligar o acesso, escolher o nível de dados e as ferramentas")
    public McpConfigResponse update(@PathParam("id") Long id, McpConfigRequest request) {
        requireSystem(id);
        if (request == null) {
            throw new BadRequestException("Corpo ausente");
        }
        DataLevel level = null;
        if (request.dataLevel() != null) {
            level = DataLevel.parse(request.dataLevel(), null);
            if (level == null) {
                throw new BadRequestException("Nível de dados inválido: " + request.dataLevel()
                        + " (use " + Arrays.toString(DataLevel.values()) + ")");
            }
        }
        try {
            return view(gate.update(id, request.enabled(), level, request.tools()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/key")
    @Operation(summary = "Gerar uma chave nova", description = "A chave anterior deixa de valer. O texto completo só é devolvido agora.")
    public NewKeyResponse newKey(@PathParam("id") Long id) {
        requireSystem(id);
        McpGateService.IssuedKey issued = gate.issueKey(id);
        return new NewKeyResponse(issued.key(), issued.prefix());
    }

    @DELETE
    @Path("/key")
    @Operation(summary = "Revogar a chave", description = "Apaga a chave e desliga o acesso MCP do sistema")
    public Response revoke(@PathParam("id") Long id) {
        requireSystem(id);
        gate.revokeKey(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/calls")
    @Operation(summary = "Últimas chamadas feitas pelo MCP neste sistema")
    public List<CallResponse> calls(@PathParam("id") Long id, @QueryParam("limit") @DefaultValue("50") int limit) {
        requireSystem(id);
        return gate.recentCalls(id, limit).stream()
                .map(c -> new CallResponse(c.getTool(), c.isOk(), c.getDurationMs(), c.getDetail(), c.getCalledAt().toString()))
                .toList();
    }

    private AuditedSystem requireSystem(Long id) {
        return systemCatalog.require(id);
    }

    private McpConfigResponse view(McpAccess access) {
        List<McpTool> enabled = gate.enabledTools(access);
        List<ToolState> tools = Arrays.stream(McpTool.values())
                .map(t -> new ToolState(t.wire(), t.description(), t.writes(), enabled.contains(t)))
                .toList();
        if (access == null) {
            return new McpConfigResponse(false, false, null, null, DataLevel.TITLES, tools);
        }
        return new McpConfigResponse(access.isEnabled(), access.getKeyHash() != null, access.getKeyPrefix(),
                access.getKeyCreatedAt() == null ? null : access.getKeyCreatedAt().toString(),
                access.getDataLevel() == null ? DataLevel.TITLES : access.getDataLevel(), tools);
    }
}
