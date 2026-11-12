package com.sentinel.audit.insights;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.sentinel.audit.model.Severity;

/**
 * Uma linha da tabela de dívida técnica: um problema (agrupado por regra), com impacto, esforço e prioridade.
 *
 * @param effort          S (minutos), M (horas) ou L (dias)
 * @param effortEstimated {@code true} quando o esforço é o padrão e não vem da orientação de correção da regra
 * @param priority        P0 Bloqueador, P1 Alta, P2 Importante, P3 Futura
 */
public record DebtItem(@Schema(description = "Identificador do item.") String id, @Schema(description = "O problema, agrupado por regra.") String problem, @Schema(description = "Área.") String area, @Schema(description = "Área, em texto para exibição.") String areaLabel, @Schema(description = "Severidade.") Severity severity, @Schema(description = "Prioridade: P0 Bloqueador, P1 Alta, P2 Importante ou P3 Futura.") String priority,
                       @Schema(description = "Prioridade, em texto para exibição.") String priorityLabel, @Schema(description = "Quantas ocorrências.") int occurrences, @Schema(description = "Impacto.") String impact, @Schema(description = "Esforço: S (minutos), M (horas) ou L (dias).") String effort, @Schema(description = "Verdadeiro quando o esforço é o padrão e não vem da orientação de correção da regra.") boolean effortEstimated,
                       @Schema(description = "Risco de regressão.") String regressionRisk, @Schema(description = "Recomendação.") String recommendation, @Schema(description = "Exemplo de ocorrência.") String example) {
}
