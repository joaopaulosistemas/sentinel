package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class HorarioDeExibicaoTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    @Test
    void converteDoRelogioDoServidorParaBrasilia() {
        assertEquals("16/11/2026 12:31", HorarioDeExibicao.formatar(LocalDateTime.of(2026, 11, 16, 15, 31), UTC, BRASILIA));
    }

    @Test
    void viraODiaAnteriorQuandoPassaDaMeiaNoite() {
        assertEquals("15/11/2026 22:30", HorarioDeExibicao.formatar(LocalDateTime.of(2026, 11, 16, 1, 30), UTC, BRASILIA));
    }

    @Test
    void servidorJaEmBrasiliaNaoMudaAHora() {
        assertEquals("16/11/2026 12:31", HorarioDeExibicao.formatar(LocalDateTime.of(2026, 11, 16, 12, 31), BRASILIA, BRASILIA));
    }

    @Test
    void fusoPedidoPorConfiguracaoValeEOutroFusoTambem() {
        assertEquals(ZoneId.of("America/Manaus"), HorarioDeExibicao.fusoDeExibicao(" America/Manaus "));
    }

    @Test
    void fusoVazioOuInvalidoCaiNoPadraoEmVezDeQuebrarORelatorio() {
        assertEquals(BRASILIA, HorarioDeExibicao.fusoDeExibicao(null));
        assertEquals(BRASILIA, HorarioDeExibicao.fusoDeExibicao("  "));
        assertEquals(BRASILIA, HorarioDeExibicao.fusoDeExibicao("Marte/Olimpo"));
    }
}
