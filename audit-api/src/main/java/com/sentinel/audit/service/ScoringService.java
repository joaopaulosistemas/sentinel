package com.sentinel.audit.service;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.RiskScore;
import com.sentinel.audit.model.Severity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço responsável pelo cálculo do score de risco a partir dos findings de uma auditoria.
 * <p>
 * O score é calculado por dimensão (segurança, qualidade, etc.) com base nas penalidades
 * acumuladas pelos findings de cada categoria. O score geral é uma média ponderada das dimensões.
 */
@ApplicationScoped
public class ScoringService {

    /** Penalidade por finding de acordo com a severidade. */
    private static final Map<Severity, Double> SEVERITY_PENALTY = Map.of(
            Severity.CRITICAL, 20.0,
            Severity.HIGH,     10.0,
            Severity.MEDIUM,   5.0,
            Severity.LOW,      2.0,
            Severity.INFO,     1.0
    );

    /** Teto de penalidade para findings MEDIUM/LOW/INFO repetidos da mesma regra. */
    private static final double MAX_LOW_SEVERITY_PENALTY_PER_RULE = 20.0;

    /** Peso de cada dimensão no score geral (soma = 1.0). */
    private static final Map<Category, Double> DIMENSION_WEIGHT = Map.of(
            Category.SECURITY,      0.35,
            Category.QUALITY,       0.25,
            Category.ARCHITECTURE,  0.20,
            Category.DEPENDENCY,    0.10,
            Category.OBSERVABILITY, 0.10
    );

    /**
     * Calcula o {@link RiskScore} completo a partir da lista de findings.
     *
     * @param findings lista de findings encontrados na auditoria
     * @return objeto {@link RiskScore} com scores por dimensão e score geral
     */
    public RiskScore calculate(List<Finding> findings) {
        double security      = calculateDimensionScore(findings, Category.SECURITY);
        double quality       = calculateDimensionScore(findings, Category.QUALITY);
        double architecture  = calculateDimensionScore(findings, Category.ARCHITECTURE);
        double dependencies  = calculateDimensionScore(findings, Category.DEPENDENCY);
        double observability = calculateDimensionScore(findings, Category.OBSERVABILITY);

        double overall = (security      * DIMENSION_WEIGHT.get(Category.SECURITY))
                       + (quality       * DIMENSION_WEIGHT.get(Category.QUALITY))
                       + (architecture  * DIMENSION_WEIGHT.get(Category.ARCHITECTURE))
                       + (dependencies  * DIMENSION_WEIGHT.get(Category.DEPENDENCY))
                       + (observability * DIMENSION_WEIGHT.get(Category.OBSERVABILITY));

        return new RiskScore(
                round(overall),
                round(security),
                round(quality),
                round(architecture),
                round(dependencies),
                round(observability)
        );
    }

    /**
     * Calcula o score de uma dimensão específica subtraindo as penalidades dos findings
     * dessa categoria do score base de 100. Findings CRITICAL/HIGH sempre penalizam por inteiro;
     * os de severidade menor têm teto por regra (veja {@code MAX_LOW_SEVERITY_PENALTY_PER_RULE}).
     *
     * @param findings lista completa de findings
     * @param category categoria (dimensão) a calcular
     * @return score entre 0 e 100
     */
    public double calculateDimensionScore(List<Finding> findings, Category category) {
        double penalty = 0.0;
        Map<String, Double> lowSeverityByRule = new HashMap<>();

        for (Finding f : findings) {
            if (f.getCategory() != category) {
                continue;
            }
            double p = SEVERITY_PENALTY.getOrDefault(f.getSeverity(), 0.0);
            boolean capped = f.getRuleId() != null && isLowSeverity(f.getSeverity());
            if (capped) {
                // Muitas ocorrências de severidade baixa da mesma regra não devem zerar a dimensão.
                lowSeverityByRule.merge(f.getRuleId() + "|" + f.getSeverity(), p, Double::sum);
            } else {
                penalty += p;
            }
        }
        for (double rulePenalty : lowSeverityByRule.values()) {
            penalty += Math.min(rulePenalty, MAX_LOW_SEVERITY_PENALTY_PER_RULE);
        }

        return Math.max(0.0, 100.0 - penalty);
    }

    private boolean isLowSeverity(Severity severity) {
        return severity == Severity.MEDIUM || severity == Severity.LOW || severity == Severity.INFO;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
