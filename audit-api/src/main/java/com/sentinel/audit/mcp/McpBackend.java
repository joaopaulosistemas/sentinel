package com.sentinel.audit.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** O que o protocolo precisa saber do sistema ao qual a chave dá acesso. */
public interface McpBackend {

    /** Ferramentas ligadas para este sistema (as desligadas nem aparecem na lista). */
    List<McpTool> enabledTools();

    /** Executa uma ferramenta já liberada. Quem implementa registra a chamada. */
    ToolResult call(McpTool tool, JsonNode arguments);

    /** Resposta de uma ferramenta: o texto (JSON) que vai para o cliente e se deu certo. */
    record ToolResult(boolean ok, String text) {
        public static ToolResult ok(String text) {
            return new ToolResult(true, text);
        }

        public static ToolResult error(String text) {
            return new ToolResult(false, text);
        }
    }
}
