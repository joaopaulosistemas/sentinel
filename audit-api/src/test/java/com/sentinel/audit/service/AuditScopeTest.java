package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuditScopeTest {

    @Test
    void gravaOrdenadoESeparadoPorVirgulaEVazioQuandoNenhumLadoEstaDesligado() {
        assertEquals("", AuditScope.encode(Set.of()));
        assertEquals("", AuditScope.encode(null));
        assertEquals("front", AuditScope.encode(Set.of("front")));
        assertEquals("back,front", AuditScope.encode(Set.of("front", "back")));
    }

    @Test
    void lerNuncaDevolveNuloENuloOuVazioDaListaVazia() {
        assertTrue(AuditScope.decode(null).isEmpty());
        assertTrue(AuditScope.decode("").isEmpty());
        assertTrue(AuditScope.decode("  ").isEmpty());
        assertEquals(List.of("front"), AuditScope.decode("front"));
        assertEquals(List.of("back", "front"), AuditScope.decode("front, back"));
    }

    @Test
    void seloDizQualLadoEstaDesligadoEFicaVazioSemLadoDesligado() {
        assertEquals("front-end desligado", AuditScope.badge(List.of("front")));
        assertEquals("back-end desligado", AuditScope.badge(List.of("back")));
        assertEquals("", AuditScope.badge(List.of()));
        assertEquals("", AuditScope.badge(null));
    }
}
