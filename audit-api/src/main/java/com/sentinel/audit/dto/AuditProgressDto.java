package com.sentinel.audit.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Andamento de uma auditoria em execução: etapa atual, situação de cada analisador e o que já aconteceu.
 * Existe só enquanto a auditoria roda; depois de concluída o resultado vem dos insights.
 */
public record AuditProgressDto(String phase, List<AnalyzerProgress> analyzers, List<Event> events) {

    /** Situações: ESPERANDO, EXECUTANDO, OK, FALHOU, INDISPONIVEL, NAO_SUPORTADO, SUBSTITUIDO, SEM_EVIDENCIA. */
    public record AnalyzerProgress(String name, String status, int findings, long durationMs, String note) {}

    public record Event(LocalDateTime at, String message) {}
}
