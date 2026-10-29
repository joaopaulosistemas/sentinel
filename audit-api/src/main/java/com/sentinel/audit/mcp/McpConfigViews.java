package com.sentinel.audit.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.List;

/** Monta a visão da configuração MCP de um sistema: nível de dados, chave (só o começo) e o estado de cada ferramenta. */
@ApplicationScoped
public class McpConfigViews {

    public record ToolState(String name, String description, boolean writes, boolean enabled) {}

    public record McpConfigResponse(boolean enabled, boolean hasKey, String keyPrefix, String keyCreatedAt,
                                    DataLevel dataLevel, List<ToolState> tools) {}

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
}
