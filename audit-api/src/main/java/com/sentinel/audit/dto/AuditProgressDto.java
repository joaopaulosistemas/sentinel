package com.sentinel.audit.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Andamento de uma auditoria em execução: etapa, analisadores, eventos e o que já se sabe do resultado
 * (tamanho do código e achados por severidade, que crescem enquanto os analisadores terminam).
 */
public record AuditProgressDto(String phase, List<AnalyzerProgress> analyzers, List<Event> events,
                               String repoUrl, long filesRead, long linesOfCode, Map<String, Integer> severities) {

    /** Compatível com quem só conhece etapa, analisadores e eventos. */
    public AuditProgressDto(String phase, List<AnalyzerProgress> analyzers, List<Event> events) {
        this(phase, analyzers, events, null, 0, 0, Map.of());
    }

    /** @param description o que o analisador faz, em uma linha */
    public record AnalyzerProgress(String name, String status, int findings, long durationMs, String note, String description) {

        public AnalyzerProgress(String name, String status, int findings, long durationMs, String note) {
            this(name, status, findings, durationMs, note, "");
        }
    }

    public record Event(LocalDateTime at, String message) {}
}
