package com.sentinel.audit.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AuditListLimitTest {

    @Test
    void limiteForaDaFaixaVoltaParaOsLimites() {
        assertEquals(1, AuditListLimit.clamp(0));
        assertEquals(1, AuditListLimit.clamp(-5));
        assertEquals(100, AuditListLimit.clamp(100));
        assertEquals(500, AuditListLimit.clamp(100_000));
    }

    @Test
    void soAsMaisRecentesGanhamAComparacaoCompleta() {
        assertEquals(true, AuditListLimit.withDiff(0));
        assertEquals(true, AuditListLimit.withDiff(9));
        assertEquals(false, AuditListLimit.withDiff(10));
        assertEquals(false, AuditListLimit.withDiff(99));
    }
}
