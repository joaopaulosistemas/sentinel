package com.sentinel.audit.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpAuthGuardTest {

    private long now = 1_000_000L;

    private McpAuthGuard guard(int failuresPerMinute) {
        return new McpAuthGuard(new McpRateLimiter(failuresPerMinute, () -> now));
    }

    @Test
    void chaveCertaPassaMesmoDepoisDeMuitasTentativasErradasContraOMesmoSistema() {
        McpAuthGuard g = guard(3);
        for (int i = 0; i < 10; i++) {
            g.check("loja", false);
        }
        assertEquals(McpAuthGuard.Verdict.TOO_MANY, g.check("loja", false));
        assertEquals(McpAuthGuard.Verdict.ALLOWED, g.check("loja", true), "o cliente legítimo não pode ser barrado por quem erra a chave");
    }

    @Test
    void chaveErradaVira401AteOLimiteE429Depois() {
        McpAuthGuard g = guard(2);
        assertEquals(McpAuthGuard.Verdict.UNAUTHORIZED, g.check("loja", false));
        assertEquals(McpAuthGuard.Verdict.UNAUTHORIZED, g.check("loja", false));
        assertEquals(McpAuthGuard.Verdict.TOO_MANY, g.check("loja", false));
        now += 61_000;
        assertEquals(McpAuthGuard.Verdict.UNAUTHORIZED, g.check("loja", false), "a janela abriu de novo");
    }

    @Test
    void nomesDiferentesTemLimitesSeparados() {
        McpAuthGuard g = guard(1);
        g.check("a", false);
        assertEquals(McpAuthGuard.Verdict.TOO_MANY, g.check("a", false));
        assertEquals(McpAuthGuard.Verdict.UNAUTHORIZED, g.check("b", false));
    }

    @Test
    void comOMapaCheioUmNomeNovoNaoEscapaDoLimite() {
        McpAuthGuard g = guard(10);
        for (int i = 0; i < McpRateLimiter.MAX_KEYS; i++) {
            g.check("x" + i, false);
        }
        assertEquals(McpAuthGuard.Verdict.TOO_MANY, g.check("alvo", false),
                "sem lugar para registrar o nome, a tentativa errada é recusada em vez de ficar sem limite");
        assertEquals(McpAuthGuard.Verdict.ALLOWED, g.check("alvo", true));
    }

    @Test
    void depoisQueAsEntradasExpiramOMapaVoltaAAceitarNomesNovos() {
        McpAuthGuard g = guard(10);
        for (int i = 0; i < McpRateLimiter.MAX_KEYS; i++) {
            g.check("x" + i, false);
        }
        now += 61_000;
        assertEquals(McpAuthGuard.Verdict.UNAUTHORIZED, g.check("alvo", false));
    }

    @Test
    void limitadorNaoConsideraBloqueadoUmNomeNovoComOMapaFolgado() {
        McpRateLimiter l = new McpRateLimiter(1, () -> now);
        assertFalse(l.blocked("novo"));
        l.tryAcquire("novo");
        assertTrue(l.blocked("novo"));
    }
}
