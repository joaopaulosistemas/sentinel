package com.sentinel.audit.service;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Hora que aparece nos relatórios (HTML e PDF). O banco guarda {@code LocalDateTime} no relógio do servidor, que no
 * contêiner é UTC; o relatório converte para o fuso de quem lê (padrão {@code America/Sao_Paulo}, ajustável por
 * {@code REPORT_TIMEZONE}). Nada é regravado: só a exibição muda, então as auditorias antigas também saem certas.
 */
public final class HorarioDeExibicao {

    static final String FUSO_PADRAO = "America/Sao_Paulo";
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private HorarioDeExibicao() {
    }

    /** Formata a data gravada (relógio do servidor) no fuso de exibição. */
    public static String formatar(LocalDateTime gravada) {
        return formatar(gravada, ZoneId.systemDefault(), fusoDeExibicao(System.getenv("REPORT_TIMEZONE")));
    }

    static String formatar(LocalDateTime gravada, ZoneId fusoDoServidor, ZoneId fusoDeExibicao) {
        return gravada.atZone(fusoDoServidor).withZoneSameInstant(fusoDeExibicao).format(FORMATO);
    }

    /** O fuso pedido, ou o padrão quando vier vazio ou inválido (um erro de digitação não pode derrubar o relatório). */
    static ZoneId fusoDeExibicao(String pedido) {
        if (pedido == null || pedido.isBlank()) {
            return ZoneId.of(FUSO_PADRAO);
        }
        try {
            return ZoneId.of(pedido.strip());
        } catch (DateTimeException e) {
            return ZoneId.of(FUSO_PADRAO);
        }
    }
}
