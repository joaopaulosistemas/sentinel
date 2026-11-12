package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
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
public record FixPlanDto(@Schema(description = "Nota geral da auditoria, recalculada com os mesmos achados ativos.") double scoreNow, @Schema(description = "Nota estimada se todos os críticos fossem corrigidos.") double scoreIfCriticalFixed, @Schema(description = "Quantidade de achados críticos ativos.") long criticalCount, @Schema(description = "Os problemas de maior ganho (no máximo 15).") List<Item> items) {

    /**
     * @param scoreGain quanto a nota geral sobe se todas as ocorrências deste problema forem corrigidas
     */
    public record Item(@Schema(description = "Regra do problema.") String ruleId, @Schema(description = "Título do problema.") String title, @Schema(description = "Severidade.") Severity severity, @Schema(description = "Categoria.") Category category, @Schema(description = "Quantas ocorrências a regra tem.") int occurrences,
                       @Schema(description = "Quanto a nota geral sobe se todas as ocorrências forem corrigidas.") double scoreGain, @Schema(description = "Arquivo de uma ocorrência, como exemplo.") String exampleFile, @Schema(description = "Linha dessa ocorrência.") Integer exampleLine, @Schema(description = "Como corrigir.") String howToFix) {}
}
