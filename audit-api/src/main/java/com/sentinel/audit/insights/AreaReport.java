package com.sentinel.audit.insights;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Relatório de uma área: estado atual, problemas, riscos, recomendações e o que não foi possível verificar.
 * {@code state}: CRITICO, ATENCAO, SEM_ACHADOS ou NAO_VERIFICADO.
 */
public record AreaReport(@Schema(description = "Área.") String area, @Schema(description = "Área, em texto para exibição.") String label, @Schema(description = "Estado: CRITICO, ATENCAO, SEM_ACHADOS ou NAO_VERIFICADO.") String state, @Schema(description = "Estado atual, em texto.") String currentState, @Schema(description = "Achados por severidade.") Map<String, Integer> bySeverity,
                         @Schema(description = "Total de achados.") int total, @Schema(description = "Problemas.") List<String> problems, @Schema(description = "Riscos.") List<String> risks, @Schema(description = "Recomendações.") List<String> recommendations,
                         @Schema(description = "O que não foi possível verificar.") List<String> notVerified) {
}
