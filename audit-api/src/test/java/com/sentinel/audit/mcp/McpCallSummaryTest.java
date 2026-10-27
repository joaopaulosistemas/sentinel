package com.sentinel.audit.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpCallSummaryTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void listaCamposSimplesEMascaraSegredos() throws Exception {
        var args = JSON.readTree("{\"severity\":\"CRITICAL\",\"limit\":5,\"apiKey\":\"snt_abc\",\"filtro\":{\"a\":1}}");
        assertEquals("severity=CRITICAL, limit=5, apiKey=***", McpCallSummary.of(null, args));
    }

    @Test
    void juntaOErroComOsParametros() throws Exception {
        var args = JSON.readTree("{\"auditId\":9}");
        assertEquals("ferramenta desligada | auditId=9", McpCallSummary.of("ferramenta desligada", args));
    }

    @Test
    void semErroNemParametrosDevolveNulo() {
        assertNull(McpCallSummary.of(null, null));
        assertNull(McpCallSummary.of("", JSON.createObjectNode()));
    }

    @Test
    void cortaValoresELimitaOTotal() throws Exception {
        String longo = "x".repeat(200);
        var args = JSON.readTree("{\"q\":\"" + longo + "\"}");
        String out = McpCallSummary.of(null, args);
        assertEquals("q=" + "x".repeat(McpCallSummary.MAX_VALUE) + "…", out);
        assertTrue(McpCallSummary.of(longo, args).length() <= McpCallSummary.MAX_TOTAL);
    }
}
