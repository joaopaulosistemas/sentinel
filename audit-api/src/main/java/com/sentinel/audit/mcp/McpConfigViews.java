package com.sentinel.audit.mcp;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.Arrays;
import java.util.List;

/** Monta a visão da configuração MCP de um sistema: nível de dados, chave (só o começo) e o estado de cada ferramenta. */
@ApplicationScoped
public class McpConfigViews {

    public record ToolState(@Schema(description = "Ferramenta MCP.") String name, @Schema(description = "O que faz.") String description, @Schema(description = "Verdadeiro se altera dados.") boolean writes, @Schema(description = "Liberada para o sistema.") boolean enabled) {}

    public record McpConfigResponse(@Schema(description = "Acesso MCP ligado.") boolean enabled, @Schema(description = "Há chave gerada.") boolean hasKey, @Schema(description = "Começo da chave.") String keyPrefix, @Schema(description = "Quando a chave foi gerada.") String keyCreatedAt,
                                    @Schema(description = "Nível de dados.") DataLevel dataLevel, @Schema(description = "Estado das ferramentas.") List<ToolState> tools) {}

    @Inject
    McpGateService gate;

    McpConfigResponse view(McpAccess access) {
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

    /** Nível de dados pedido; {@code null} quando não veio, e erro 400 quando não existe. */
    DataLevel parseLevel(String raw) {
        if (raw == null) {
            return null;
        }
        DataLevel level = DataLevel.parse(raw, null);
        if (level == null) {
            throw new BadRequestException("Nível de dados inválido: " + raw + " (use " + Arrays.toString(DataLevel.values()) + ")");
        }
        return level;
    }
}
