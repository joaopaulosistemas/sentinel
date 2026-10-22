package com.sentinel.audit.dto;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Severity;

import java.util.List;

/**
 * O que corrigir primeiro: problemas agrupados por regra, ordenados pelo quanto a nota sobe se forem resolvidos.
 *
 * @param scoreNow             nota geral da auditoria, recalculada com os mesmos achados ativos
 * @param scoreIfCriticalFixed nota estimada se todos os críticos fossem corrigidos
 * @param criticalCount        quantidade de achados críticos ativos
 * @param items                os problemas de maior ganho (no máximo 15)
 */
public record FixPlanDto(double scoreNow, double scoreIfCriticalFixed, long criticalCount, List<Item> items) {

    /**
     * @param scoreGain quanto a nota geral sobe se todas as ocorrências deste problema forem corrigidas
     */
    public record Item(String ruleId, String title, Severity severity, Category category, int occurrences,
                       double scoreGain, String exampleFile, Integer exampleLine, String howToFix) {}
}
