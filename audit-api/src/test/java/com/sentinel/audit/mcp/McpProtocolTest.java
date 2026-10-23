package com.sentinel.audit.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final McpProtocol protocol = new McpProtocol(mapper, "1.2.3");

    private McpBackend backend(boolean fail) {
        return new McpBackend() {
            @Override
            public List<McpTool> enabledTools() {
                return List.of(McpTool.STATUS_AUDITORIA, McpTool.O_QUE_CORRIGIR_PRIMEIRO);
            }

            @Override
            public ToolResult call(McpTool tool, JsonNode arguments) {
                if (fail) {
                    throw new IllegalStateException("segredo interno do servidor");
                }
                return tool == McpTool.STATUS_AUDITORIA ? ToolResult.ok("{\"nota\":62}") : ToolResult.error("Ferramenta desligada");
            }
        };
    }

    private JsonNode reply(String body, boolean fail) throws Exception {
        return mapper.readTree(protocol.handle(body, backend(fail)).orElseThrow());
    }

    @Test
    void initializeDevolveAVersaoPedidaEAsFerramentas() throws Exception {
        JsonNode r = reply("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}", false);
        assertEquals(1, r.get("id").asInt());
        assertEquals("2025-03-26", r.at("/result/protocolVersion").asText());
        assertTrue(r.at("/result/capabilities/tools").isObject());
        assertEquals("sentinel-audit", r.at("/result/serverInfo/name").asText());
        assertEquals("1.2.3", r.at("/result/serverInfo/version").asText());
    }

    @Test
    void versaoDesconhecidaCaiNaMaisNova() throws Exception {
        JsonNode r = reply("{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}", false);
        assertEquals(McpProtocol.LATEST, r.at("/result/protocolVersion").asText());
        assertEquals("a", r.get("id").asText());
    }

    @Test
    void listaSoAsFerramentasLigadasEMarcaComoSomenteLeitura() throws Exception {
        JsonNode r = reply("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", false);
        JsonNode tools = r.at("/result/tools");
        assertEquals(2, tools.size());
        assertEquals("status_auditoria", tools.get(0).get("name").asText());
        assertEquals("object", tools.get(0).at("/inputSchema/type").asText());
        assertTrue(tools.get(0).at("/annotations/readOnlyHint").asBoolean());
    }

    @Test
    void chamadaDeFerramentaDevolveOTextoEOEstadoDeErro() throws Exception {
        JsonNode ok = reply("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"status_auditoria\"}}", false);
        assertFalse(ok.at("/result/isError").asBoolean());
        assertEquals("{\"nota\":62}", ok.at("/result/content/0/text").asText());

        JsonNode ruim = reply("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"o_que_corrigir_primeiro\",\"arguments\":{}}}", false);
        assertTrue(ruim.at("/result/isError").asBoolean());
    }

    @Test
    void erroInternoNaoVazaAMensagemParaOCliente() throws Exception {
        JsonNode r = reply("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"status_auditoria\"}}", true);
        assertTrue(r.at("/result/isError").asBoolean());
        assertFalse(r.toString().contains("segredo interno"));
    }

    @Test
    void ferramentaDesconhecidaEMetodoDesconhecidoViramErroJsonRpc() throws Exception {
        JsonNode f = reply("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"apagar_tudo\"}}", false);
        assertEquals(-32602, f.at("/error/code").asInt());
        JsonNode m = reply("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"resources/list\"}", false);
        assertEquals(-32601, m.at("/error/code").asInt());
    }

    @Test
    void notificacaoNaoTemResposta() {
        assertTrue(protocol.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", backend(false)).isEmpty());
    }

    @Test
    void jsonInvalidoELoteSaoRecusados() throws Exception {
        assertEquals(-32700, reply("{isto não é json", false).at("/error/code").asInt());
        assertEquals(-32600, reply("[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]", false).at("/error/code").asInt());
        assertEquals(-32700, reply("", false).at("/error/code").asInt());
    }

    @Test
    void pingResponde() throws Exception {
        JsonNode r = reply("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\"}", false);
        assertTrue(r.get("result").isObject());
    }
}
