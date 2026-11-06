package com.sentinel.audit.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Andamento de uma auditoria em execução: etapa, analisadores, eventos e o que já se sabe do resultado
 * (tamanho do código e achados por severidade, que crescem enquanto os analisadores terminam). Durante a
 * etapa de clone da pasta local, {@code copyDone} de {@code copyTotal} arquivos já foram copiados (zero = não se sabe).
 */
public record AuditProgressDto(String phase, List<AnalyzerProgress> analyzers, List<Event> events,
                               String repoUrl, long filesRead, long linesOfCode, Map<String, Integer> severities,
                               long copyDone, long copyTotal) {

    /** Compatível com quem só conhece etapa, analisadores e eventos. */
    public AuditProgressDto(String phase, List<AnalyzerProgress> analyzers, List<Event> events) {
        this(phase, analyzers, events, null, 0, 0, Map.of(), 0, 0);
    }

    /** Sem o andamento da cópia (a cópia só existe para pasta local no modo "como está agora"). */
    public AuditProgressDto(String phase, List<AnalyzerProgress> analyzers, List<Event> events,
                            String repoUrl, long filesRead, long linesOfCode, Map<String, Integer> severities) {
        this(phase, analyzers, events, repoUrl, filesRead, linesOfCode, severities, 0, 0);
    }

    /** @param description o que o analisador faz, em uma linha */
    public record AnalyzerProgress(String name, String status, int findings, long durationMs, String note, String description) {

        public AnalyzerProgress(String name, String status, int findings, long durationMs, String note) {
            this(name, status, findings, durationMs, note, "");
        }
    }

    public record Event(LocalDateTime at, String message) {}
}
