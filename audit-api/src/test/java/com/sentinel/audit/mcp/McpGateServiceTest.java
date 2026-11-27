package com.sentinel.audit.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpGateServiceTest {

    private final McpGateService gate = new McpGateService();

    private McpAccess access(boolean enabled, String key) {
        McpAccess a = new McpAccess();
        a.setEnabled(enabled);
        if (key != null) {
            a.setKeyHash(McpGateService.hash(key));
        }
        return a;
    }

    @Test
    void chaveNovaEPrefixadaELongaECadaUmaDiferente() {
        String a = McpGateService.newKey();
        String b = McpGateService.newKey();
        assertTrue(a.startsWith("snt_"));
        assertTrue(a.length() >= 40);
        assertNotEquals(a, b);
    }

    @Test
    void hashNaoGuardaAChaveEEEstavel() {
        String h = McpGateService.hash("snt_abc");
        assertEquals(64, h.length());
        assertFalse(h.contains("abc"));
        assertEquals(h, McpGateService.hash("snt_abc"));
        assertNotEquals(h, McpGateService.hash("snt_abd"));
    }

    @Test
    void soAChaveCertaDeUmAcessoLigadoPassa() {
        McpAccess ligado = access(true, "snt_certa");
        assertTrue(gate.accepts(ligado, "snt_certa"));
        assertTrue(gate.accepts(ligado, "  snt_certa  "));
        assertFalse(gate.accepts(ligado, "snt_errada"));
        assertFalse(gate.accepts(ligado, ""));
        assertFalse(gate.accepts(ligado, null));
    }

    @Test
    void acessoDesligadoOuSemChaveOuInexistenteNaoPassa() {
        assertFalse(gate.accepts(access(false, "snt_certa"), "snt_certa"));
        assertFalse(gate.accepts(access(true, null), "snt_certa"));
        assertFalse(gate.accepts(null, "snt_certa"));
    }

    @Test
    void semEscolhaSoAsFerramentasDeLeituraEstaoLigadas() {
        List<McpTool> tools = gate.enabledTools(access(true, "k"));
        assertFalse(tools.isEmpty());
        assertTrue(tools.stream().noneMatch(McpTool::writes));
        assertEquals(tools, gate.enabledTools(null));
    }

    @Test
    void ferramentaQueGravaSoLigaQuandoEscolhida() {
        McpAccess a = access(true, "k");
        a.setTools("status_auditoria,propor_regra");
        assertEquals(List.of(McpTool.STATUS_AUDITORIA, McpTool.PROPOR_REGRA), gate.enabledTools(a));
    }

    @Test
    void soAsFerramentasEscolhidasFicamLigadasEnomeDesconhecidoEIgnorado() {
        McpAccess a = access(true, "k");
        a.setTools("status_auditoria, nao_existe");
        assertEquals(List.of(McpTool.STATUS_AUDITORIA), gate.enabledTools(a));
    }

    @Test
    void ferramentasQueGravamSaoSoDeRevisaoEPedidosComAprovacao() {
        List<McpTool> writers = java.util.Arrays.stream(McpTool.values()).filter(McpTool::writes).toList();
        assertEquals(List.of(McpTool.REGISTRAR_SUGESTAO, McpTool.PROPOR_REGRA, McpTool.INICIAR_AUDITORIA, McpTool.SUPRIMIR_ACHADO), writers);
        assertFalse(McpTool.STATUS_PEDIDOS.writes(), "consultar pedidos é leitura");
        assertTrue(gate.enabledTools(access(true, "k")).contains(McpTool.STATUS_PEDIDOS));
    }

    @Test
    void recusaDizOMotivoNaOrdemEmQueAPessoaCorrige() {
        assertEquals(McpGateService.REFUSED_UNSET, McpGateService.refusalReason(null, "snt_x"));
        assertEquals(McpGateService.REFUSED_UNSET, McpGateService.refusalReason(access(true, null), "snt_x"));
        assertEquals(McpGateService.REFUSED_ABSENT, McpGateService.refusalReason(access(true, "snt_certa"), null));
        assertEquals(McpGateService.REFUSED_ABSENT, McpGateService.refusalReason(access(true, "snt_certa"), "  "));
        assertEquals(McpGateService.REFUSED_MISMATCH, McpGateService.refusalReason(access(true, "snt_certa"), "snt_outra"));
        assertEquals(McpGateService.REFUSED_MISMATCH, McpGateService.refusalReason(access(false, "snt_certa"), "snt_outra"));
        assertEquals(McpGateService.REFUSED_DISABLED, McpGateService.refusalReason(access(false, "snt_certa"), "snt_certa"));
        assertEquals(McpGateService.REFUSED_DISABLED, McpGateService.refusalReason(access(false, "snt_certa"), "  snt_certa "));
    }

    @Test
    void oMotivoDaRecusaNuncaContemAChaveApresentada() {
        String reason = McpGateService.refusalReason(access(true, "snt_certa"), "snt_segredo_enviado");
        assertFalse(reason.contains("segredo"));
    }

    private McpCallLog logLine(String tool, java.time.LocalDateTime at) {
        McpCallLog l = new McpCallLog();
        l.setTool(tool);
        l.setCalledAt(at);
        return l;
    }

    @Test
    void aConexaoSoEAnotadaDeNovoDepoisDeUmTempoParaNaoEncherORegistro() {
        java.time.LocalDateTime agora = java.time.LocalDateTime.of(2026, 11, 27, 2, 0);
        assertTrue(McpGateService.shouldLogHandshake(null, agora));
        assertTrue(McpGateService.shouldLogHandshake(logLine("status_auditoria", agora.minusSeconds(5)), agora));
        assertFalse(McpGateService.shouldLogHandshake(logLine(McpGateService.HANDSHAKE_TOOL, agora.minusMinutes(1)), agora));
        assertTrue(McpGateService.shouldLogHandshake(logLine(McpGateService.HANDSHAKE_TOOL, agora.minusMinutes(6)), agora));
    }
}
