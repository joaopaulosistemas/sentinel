package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Andamento de uma auditoria em execução: etapa, analisadores, eventos e o que já se sabe do resultado
 * (tamanho do código e achados por severidade, que crescem enquanto os analisadores terminam). Durante a
 * etapa de clone da pasta local, {@code copyDone} de {@code copyTotal} arquivos já foram copiados (zero = não se sabe).
 */
public record AuditProgressDto(@Schema(description = "Etapa atual da auditoria.") String phase, @Schema(description = "Andamento de cada analisador.") List<AnalyzerProgress> analyzers, @Schema(description = "Eventos da execução, em ordem.") List<Event> events,
                               @Schema(description = "Repositório que está sendo auditado.") String repoUrl, @Schema(description = "Arquivos lidos até agora.") long filesRead, @Schema(description = "Linhas de código contadas até agora.") long linesOfCode, @Schema(description = "Achados por severidade até agora; crescem enquanto os analisadores terminam.") Map<String, Integer> severities,
                               @Schema(description = "Arquivos já copiados na etapa de clone da pasta local.") long copyDone, @Schema(description = "Total de arquivos a copiar; zero = não se sabe.") long copyTotal) {

    /** Compatível com quem só conhece etapa, analisadores e eventos. */
    public AuditProgressDto(String phase, List<AnalyzerProgress> analyzers, List<Event> events) {
        this(phase, analyzers, events, null, 0, 0, Map.of(), 0, 0);
    }

    /** @param description o que o analisador faz, em uma linha */
    public record AnalyzerProgress(@Schema(description = "Nome do analisador.") String name, @Schema(description = "Situação do analisador.") String status, @Schema(description = "Achados do analisador.") int findings, @Schema(description = "Duração, em milissegundos.") long durationMs, @Schema(description = "Observação do analisador, quando há.") String note, @Schema(description = "O que o analisador faz, em uma linha.") String description) {

        public AnalyzerProgress(String name, String status, int findings, long durationMs, String note) {
            this(name, status, findings, durationMs, note, "");
        }
    }

    public record Event(@Schema(description = "Quando ocorreu.") LocalDateTime at, @Schema(description = "O que aconteceu.") String message) {}
}
