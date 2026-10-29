package com.sentinel.audit.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

/**
 * Servidor MCP mínimo sobre HTTP (JSON-RPC 2.0, uma requisição por POST, resposta em JSON). Cobre o que um cliente
 * como o Claude Code usa para ler: {@code initialize}, {@code ping}, {@code tools/list} e {@code tools/call}.
 * Não guarda sessão e não abre fluxo SSE; quem decide o que cada chave pode fazer é o {@link McpBackend}.
 */
public final class McpProtocol {

    /** Códigos de erro do JSON-RPC 2.0. */
    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;


    static final String LATEST = "2025-06-18";
    static final List<String> SUPPORTED = List.of("2025-06-18", "2025-03-26", "2024-11-05");

    private final ObjectMapper mapper;
    private final String serverVersion;

    public McpProtocol(ObjectMapper mapper, String serverVersion) {
        this.mapper = mapper;
        this.serverVersion = serverVersion;
    }

    /**
     * @return a resposta em JSON, ou vazio quando a mensagem não pede resposta (notificação): o HTTP devolve 202.
     */
    public Optional<String> handle(String body, McpBackend backend) {
        if (body == null || body.isBlank()) {
            return Optional.of(error(null, PARSE_ERROR, "Corpo vazio"));
        }
        JsonNode msg;
        try {
            msg = mapper.readTree(body);
        } catch (JsonProcessingException e) {
            return Optional.of(error(null, PARSE_ERROR, "JSON inválido"));
        }
        if (msg == null || !msg.isObject()) {
            return Optional.of(error(null, INVALID_REQUEST, "Envie uma mensagem JSON-RPC por vez"));
        }
        return dispatch(msg, backend);
    }

    private Optional<String> dispatch(JsonNode msg, McpBackend backend) {
        JsonNode id = msg.get("id");
        boolean expectsReply = id != null && !id.isNull();
        String method = msg.path("method").asText("");
        if (method.isEmpty()) {
            return withoutMethod(id, msg, expectsReply);
        }
        if (!expectsReply) {
            return Optional.empty(); // notificação, como notifications/initialized
        }
        JsonNode params = msg.path("params");
        return Optional.of(switch (method) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, mapper.createObjectNode());
            case "tools/list" -> result(id, listTools(backend));
            case "tools/call" -> callTool(id, params, backend);
            default -> error(id, METHOD_NOT_FOUND, "Método não suportado: " + method);
        });
    }

    /** Resposta de um cliente (sem método) ou lixo: só há o que responder se pediu uma resposta sem ser uma. */
    private Optional<String> withoutMethod(JsonNode id, JsonNode msg, boolean expectsReply) {
        return expectsReply && !msg.has("result") && !msg.has("error")
                ? Optional.of(error(id, INVALID_REQUEST, "Requisição sem método"))
                : Optional.empty();
    }

    private ObjectNode initialize(JsonNode params) {
        String asked = params.path("protocolVersion").asText("");
        ObjectNode r = mapper.createObjectNode();
        r.put("protocolVersion", SUPPORTED.contains(asked) ? asked : LATEST);
        r.putObject("capabilities").putObject("tools").put("listChanged", false);
        ObjectNode info = r.putObject("serverInfo");
        info.put("name", "sentinel-audit");
        info.put("version", serverVersion);
        r.put("instructions", "Sentinel Audit Tool. Estas ferramentas só leem o resultado das auditorias deste sistema. "
                + "A nota é calculada apenas pelo motor de regras do Sentinel; sugestões suas não entram nela.");
        return r;
    }

    private ObjectNode listTools(McpBackend backend) {
        ObjectNode r = mapper.createObjectNode();
        ArrayNode tools = r.putArray("tools");
        for (McpTool t : backend.enabledTools()) {
            ObjectNode n = tools.addObject();
            n.put("name", t.wire());
            n.put("description", t.description());
            n.set("inputSchema", t.inputSchema(mapper));
            n.putObject("annotations").put("readOnlyHint", !t.writes());
        }
        return r;
    }

    private String callTool(JsonNode id, JsonNode params, McpBackend backend) {
        String name = params.path("name").asText("");
        Optional<McpTool> tool = McpTool.byWire(name);
        if (tool.isEmpty()) {
            return error(id, INVALID_PARAMS, "Ferramenta desconhecida: " + name);
        }
        JsonNode args = params.path("arguments");
        if (!args.isObject()) {
            args = mapper.createObjectNode();
        }
        McpBackend.ToolResult res;
        try {
            res = backend.call(tool.get(), args);
        } catch (RuntimeException e) {
            res = McpBackend.ToolResult.error("Erro interno ao executar a ferramenta.");
        }
        ObjectNode r = mapper.createObjectNode();
        r.putArray("content").addObject().put("type", "text").put("text", res.text());
        r.put("isError", !res.ok());
        return result(id, r);
    }

    private String result(JsonNode id, JsonNode result) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        n.set("id", id);
        n.set("result", result);
        return n.toString();
    }

    private String error(JsonNode id, int code, String message) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        if (id == null) {
            n.putNull("id");
        } else {
            n.set("id", id);
        }
        ObjectNode e = n.putObject("error");
        e.put("code", code);
        e.put("message", message);
        return n.toString();
    }
}
