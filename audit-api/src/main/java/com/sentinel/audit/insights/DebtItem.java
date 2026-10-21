package com.sentinel.audit.insights;

import com.sentinel.audit.model.Severity;

/**
 * Uma linha da tabela de dívida técnica: um problema (agrupado por regra), com impacto, esforço e prioridade.
 *
 * @param effort          S (minutos), M (horas) ou L (dias)
 * @param effortEstimated {@code true} quando o esforço é o padrão e não vem da orientação de correção da regra
 * @param priority        P0 Bloqueador, P1 Alta, P2 Importante, P3 Futura
 */
public record DebtItem(String id, String problem, String area, String areaLabel, Severity severity, String priority,
                       String priorityLabel, int occurrences, String impact, String effort, boolean effortEstimated,
                       String regressionRisk, String recommendation, String example) {
}
