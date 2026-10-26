package com.sentinel.audit.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpRateLimiterTest {

    private long now = 1_000_000L;

    @Test
    void recusaDepoisDoLimiteEVoltaQuandoAJanelaPassa() {
        McpRateLimiter l = new McpRateLimiter(3, () -> now);
        assertTrue(l.tryAcquire("a"));
        assertTrue(l.tryAcquire("a"));
        assertTrue(l.tryAcquire("a"));
        assertFalse(l.tryAcquire("a"));
        now += 59_000;
        assertFalse(l.tryAcquire("a"), "ainda dentro do minuto");
        now += 1_001;
        assertTrue(l.tryAcquire("a"));
    }

    @Test
    void chavesDiferentesNaoSeAfetam() {
        McpRateLimiter l = new McpRateLimiter(1, () -> now);
        assertTrue(l.tryAcquire("a"));
        assertFalse(l.tryAcquire("a"));
        assertTrue(l.tryAcquire("b"));
    }

    @Test
    void tentativaRecusadaNaoProlongaOBloqueio() {
        McpRateLimiter l = new McpRateLimiter(1, () -> now);
        assertTrue(l.tryAcquire("a"));
        for (int i = 0; i < 20; i++) {
            now += 1_000;
            assertFalse(l.tryAcquire("a"));
        }
        now += 41_000;
        assertTrue(l.tryAcquire("a"), "60s depois da única chamada aceita");
    }

    @Test
    void retryAfterDizQuantoFalta() {
        McpRateLimiter l = new McpRateLimiter(1, () -> now);
        assertEquals(1, l.retryAfterSeconds("nova"));
        l.tryAcquire("a");
        now += 20_000;
        assertEquals(40, l.retryAfterSeconds("a"));
    }

    @Test
    void blockedSoConsultaENaoGastaAJanela() {
        McpRateLimiter l = new McpRateLimiter(2, () -> now);
        for (int i = 0; i < 10; i++) {
            assertFalse(l.blocked("a"));
        }
        l.tryAcquire("a");
        assertFalse(l.blocked("a"));
        l.tryAcquire("a");
        assertTrue(l.blocked("a"));
        now += 60_001;
        assertFalse(l.blocked("a"));
    }

    @Test
    void limiteInvalidoViraUm() {
        McpRateLimiter l = new McpRateLimiter(0, () -> now);
        assertTrue(l.tryAcquire("a"));
        assertFalse(l.tryAcquire("a"));
    }

    @Test
    void mapaNaoCresceSemLimiteComNomesInventados() {
        McpRateLimiter l = new McpRateLimiter(5, () -> now);
        for (int i = 0; i < McpRateLimiter.MAX_KEYS; i++) {
            assertTrue(l.tryAcquire("k" + i));
        }
        assertFalse(l.tryAcquire("novo"), "cheio de chaves ativas: recusa em vez de crescer");
        now += 61_000;
        assertTrue(l.tryAcquire("novo"), "janelas vencidas são limpas");
    }
}
