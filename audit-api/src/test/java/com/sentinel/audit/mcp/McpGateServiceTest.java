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
    void soDuasFerramentasGravamEAmbasNaAreaDeRevisao() {
        List<McpTool> writers = java.util.Arrays.stream(McpTool.values()).filter(McpTool::writes).toList();
        assertEquals(List.of(McpTool.REGISTRAR_SUGESTAO, McpTool.PROPOR_REGRA), writers);
    }
}
