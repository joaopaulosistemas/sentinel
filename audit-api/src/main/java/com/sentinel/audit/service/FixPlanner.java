package com.sentinel.audit.service;

import com.sentinel.audit.pack.RemediationCatalog;
import com.sentinel.audit.dto.FixPlanDto;
import com.sentinel.audit.pack.RemediationDto;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta o plano "o que corrigir primeiro". O ganho de cada problema não é uma estimativa por peso: é a nota
 * recalculada pelo próprio {@link ScoringService} sem as ocorrências daquela regra, então inclui o teto por regra,
 * o ajuste por tamanho e o peso de cada dimensão.
 */
public final class FixPlanner {

    static final int MAX_ITEMS = 15;

    private FixPlanner() {}

    /** @param active achados que contam para a nota (já sem suprimidos e modo sombra) */
    public static FixPlanDto plan(List<Finding> active, Long linesOfCode, ScoringService scoring) {
        double now = scoring.calculate(active, linesOfCode).getOverall();

        long critical = active.stream().filter(f -> f.getSeverity() == Severity.CRITICAL).count();
        double withoutCritical = scoring.calculate(
                active.stream().filter(f -> f.getSeverity() != Severity.CRITICAL).toList(), linesOfCode).getOverall();

        Map<String, List<Finding>> byRule = new LinkedHashMap<>();
        for (Finding f : active) {
            byRule.computeIfAbsent(keyOf(f), k -> new ArrayList<>()).add(f);
        }

        List<FixPlanDto.Item> items = new ArrayList<>();
        for (Map.Entry<String, List<Finding>> e : byRule.entrySet()) {
            List<Finding> group = e.getValue();
            List<Finding> rest = active.stream().filter(f -> !keyOf(f).equals(e.getKey())).toList();
            double gain = round1(scoring.calculate(rest, linesOfCode).getOverall() - now);
            if (gain <= 0) {
                continue;
            }
            Finding worst = group.stream()
                    .min(Comparator.comparingInt((Finding f) -> f.getSeverity().ordinal()))
                    .orElseThrow();
            RemediationDto fix = RemediationCatalog.forFinding(worst);
            items.add(new FixPlanDto.Item(worst.getRuleId(), worst.getTitle(), worst.getSeverity(), worst.getCategory(),
                    group.size(), gain, worst.getFilePath(), worst.getLineNumber(),
                    fix == null ? null : fix.summary()));
        }
        items.sort(Comparator.comparingDouble(FixPlanDto.Item::scoreGain).reversed()
                .thenComparing(i -> i.severity().ordinal())
                .thenComparing(FixPlanDto.Item::ruleId, Comparator.nullsLast(Comparator.naturalOrder())));

        return new FixPlanDto(now, withoutCritical, critical, items.stream().limit(MAX_ITEMS).toList());
    }

    private static String keyOf(Finding f) {
        return f.getRuleId() != null ? f.getRuleId() : "título:" + f.getTitle();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
