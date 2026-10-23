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
    void semEscolhaTodasAsFerramentasDeLeituraEstaoLigadas() {
        List<McpTool> tools = gate.enabledTools(access(true, "k"));
        assertEquals(McpTool.values().length, tools.size());
        assertEquals(tools.size(), gate.enabledTools(null).size());
    }

    @Test
    void soAsFerramentasEscolhidasFicamLigadasEnomeDesconhecidoEIgnorado() {
        McpAccess a = access(true, "k");
        a.setTools("status_auditoria, nao_existe");
        assertEquals(List.of(McpTool.STATUS_AUDITORIA), gate.enabledTools(a));
    }

    @Test
    void nenhumaFerramentaDestaFaseEscreve() {
        for (McpTool t : McpTool.values()) {
            assertFalse(t.writes(), t.wire());
        }
    }
}
