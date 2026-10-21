package com.sentinel.audit.insights;

import java.util.List;
import java.util.Map;

/**
 * Relatório de uma área: estado atual, problemas, riscos, recomendações e o que não foi possível verificar.
 * {@code state}: CRITICO, ATENCAO, SEM_ACHADOS ou NAO_VERIFICADO.
 */
public record AreaReport(String area, String label, String state, String currentState, Map<String, Integer> bySeverity,
                         int total, List<String> problems, List<String> risks, List<String> recommendations,
                         List<String> notVerified) {
}
