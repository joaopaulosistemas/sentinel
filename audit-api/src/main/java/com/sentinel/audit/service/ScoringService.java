package com.sentinel.audit.service;

import com.sentinel.audit.dto.ScoreExplanationDto;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.RiskScore;
import com.sentinel.audit.model.Severity;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Serviço responsável pelo cálculo do score de risco a partir dos findings de uma auditoria.
 * <p>
 * O score é calculado por dimensão (segurança, qualidade, etc.) com base nas penalidades
 * acumuladas pelos findings de cada categoria. O score geral é uma média ponderada das dimensões.
 */
@ApplicationScoped
public class ScoringService {

    private static final long DEFAULT_REFERENCE_LINES = 20_000;


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
            Category.SECURITY,      0.30,
            Category.QUALITY,       0.20,
            Category.ARCHITECTURE,  0.20,
            Category.DEPENDENCY,    0.10,
            Category.OBSERVABILITY, 0.10,
            Category.PERFORMANCE,   0.10
    );

    /**
     * Score por densidade: projetos maiores toleram proporcionalmente mais achados. Acima de
     * {@code reference-lines} linhas de código, as penalidades (exceto CRITICAL) são multiplicadas por
     * raiz(referência / linhas), com piso em {@link #MIN_SIZE_FACTOR}. A raiz quadrada evita dois extremos:
     * penalizar sem critério um sistema grande e deixar o grande "de graça".
     */
    @ConfigProperty(name = "sentinel.audit.score.density.enabled", defaultValue = "true")
    boolean densityEnabled = true;

    @ConfigProperty(name = "sentinel.audit.score.density.reference-lines", defaultValue = "20000")
    long referenceLines = DEFAULT_REFERENCE_LINES;

    /**
     * Quando ligada, achados de baixa confiança (arquivo de teste, valor de exemplo, regra que o time recusa) pesam menos
     * na nota ({@link com.sentinel.audit.model.Confidence#scoreWeight()}). Desligada por padrão para não mudar as notas
     * existentes nem a comparação com auditorias antigas.
     */
    @ConfigProperty(name = "sentinel.audit.score.confidence-weighting.enabled", defaultValue = "false")
    boolean confidenceWeighting = false;

    /**
     * Curva da nota. {@code linear}: 100 menos a penalidade, com piso em 0, então várias dimensões chegam a 0 e
     * deixam de mostrar melhora. {@code suave}: 100 * e^(-penalidade/100), que fica quase igual à linear para penalidades
     * pequenas (10 pontos de penalidade dão 90,5 em vez de 90) mas nunca chega a 0, e por isso continua distinguindo um
     * sistema ruim de um péssimo. É o padrão desde a 12.30.0; {@code linear} continua disponível na configuração.
     * O valor inicial do campo ({@code linear}) vale só para quem cria o serviço sem o CDI (testes); em execução
     * quem manda é a configuração.
     */
    @ConfigProperty(name = "sentinel.audit.score.curve", defaultValue = "suave")
    String curve = "linear";

    /** Escala da curva suave: penalidade em que a nota cai para ~37 (1/e). */
    static final double SMOOTH_SCALE = 100.0;

    /** Mesmo em um sistema enorme, achados HIGH/MEDIUM ainda pesam pelo menos este tanto. */
    static final double MIN_SIZE_FACTOR = 0.2;

    /**
     * Calcula o {@link RiskScore} sem considerar o tamanho do projeto (tamanho desconhecido).
     */
    public RiskScore calculate(List<Finding> findings) {
        return calculate(findings, null);
    }

    /**
     * Calcula o {@link RiskScore} completo a partir da lista de findings.
     *
     * @param findings    lista de findings encontrados na auditoria
     * @param linesOfCode linhas de código analisadas; nulo = desconhecido (sem ajuste por tamanho)
     * @return objeto {@link RiskScore} com scores por dimensão e score geral
     */
    public RiskScore calculate(List<Finding> findings, Long linesOfCode) {
        double factor = sizeFactor(linesOfCode);
        double security      = calculateDimensionScore(findings, Category.SECURITY, factor);
        double quality       = calculateDimensionScore(findings, Category.QUALITY, factor);
        double architecture  = calculateDimensionScore(findings, Category.ARCHITECTURE, factor);
        double dependencies  = calculateDimensionScore(findings, Category.DEPENDENCY, factor);
        double observability = calculateDimensionScore(findings, Category.OBSERVABILITY, factor);
        double performance   = calculateDimensionScore(findings, Category.PERFORMANCE, factor);

        double overall = (security      * DIMENSION_WEIGHT.get(Category.SECURITY))
                       + (quality       * DIMENSION_WEIGHT.get(Category.QUALITY))
                       + (architecture  * DIMENSION_WEIGHT.get(Category.ARCHITECTURE))
                       + (dependencies  * DIMENSION_WEIGHT.get(Category.DEPENDENCY))
                       + (observability * DIMENSION_WEIGHT.get(Category.OBSERVABILITY))
                       + (performance   * DIMENSION_WEIGHT.get(Category.PERFORMANCE));

        return new RiskScore(
                round(overall),
                round(security),
                round(quality),
                round(architecture),
                round(dependencies),
                round(observability),
                round(performance)
        );
    }

    /** Fator (0,2 a 1,0) aplicado às penalidades que não são CRITICAL, conforme o tamanho do projeto. */
    double sizeFactor(Long linesOfCode) {
        if (!densityEnabled || linesOfCode == null || referenceLines <= 0 || linesOfCode <= referenceLines) {
            return 1.0;
        }
        return Math.max(MIN_SIZE_FACTOR, Math.sqrt((double) referenceLines / linesOfCode));
    }

    /** Score de uma dimensão sem ajuste por tamanho. */
    public double calculateDimensionScore(List<Finding> findings, Category category) {
        return calculateDimensionScore(findings, category, 1.0);
    }

    /**
     * Calcula o score de uma dimensão específica subtraindo as penalidades dos findings
     * dessa categoria do score base de 100. Findings CRITICAL sempre penalizam por inteiro; os demais
     * são multiplicados por {@code sizeFactor}. Os de severidade MEDIUM ou menor têm teto por regra (veja
     * {@code MAX_LOW_SEVERITY_PENALTY_PER_RULE}).
     *
     * @param findings   lista completa de findings
     * @param category   categoria (dimensão) a calcular
     * @param sizeFactor fator por tamanho do projeto (1.0 = sem ajuste)
     * @return score entre 0 e 100
     */
    public double calculateDimensionScore(List<Finding> findings, Category category, double sizeFactor) {
        double criticalPenalty = 0.0;
        double penalty = 0.0;
        Map<String, Double> lowSeverityByRule = new HashMap<>();

        for (Finding f : findings) {
            if (f.getCategory() != category) {
                continue;
            }
            double p = findingPenalty(f);
            if (f.getSeverity() == Severity.CRITICAL) {
                criticalPenalty += p;
            } else if (f.getRuleId() != null && isLowSeverity(f.getSeverity())) {
                // Muitas ocorrências de severidade baixa da mesma regra não devem zerar a dimensão.
                lowSeverityByRule.merge(f.getRuleId() + "|" + f.getSeverity(), p, Double::sum);
            } else {
                penalty += p;
            }
        }
        for (double rulePenalty : lowSeverityByRule.values()) {
            penalty += Math.min(rulePenalty, MAX_LOW_SEVERITY_PENALTY_PER_RULE);
        }

        return applyCurve(criticalPenalty + penalty * sizeFactor);
    }

    /** Penalidade de um achado: a da severidade, reduzida pela confiança quando essa ponderação está ligada. */
    private double findingPenalty(Finding f) {
        double p = SEVERITY_PENALTY.getOrDefault(f.getSeverity(), 0.0);
        if (confidenceWeighting && f.getConfidence() != null && f.getSeverity() != Severity.CRITICAL) {
            p *= f.getConfidence().scoreWeight();
        }
        return p;
    }

    /** Quantas regras aparecem por dimensão na explicação da nota; as demais entram só na contagem de "outras". */
    static final int EXPLAIN_GROUPS_PER_DIMENSION = 12;

    /** Dimensões na ordem em que a explicação é mostrada. */
    private static final List<Category> EXPLAINED = List.of(Category.SECURITY, Category.QUALITY, Category.ARCHITECTURE,
            Category.DEPENDENCY, Category.OBSERVABILITY, Category.PERFORMANCE);

    /** Soma de uma regra numa severidade dentro de uma dimensão. */
    private static final class Acc {
        final String ruleId;
        final Severity severity;
        int count;
        double raw;

        Acc(String ruleId, Severity severity) {
            this.ruleId = ruleId;
            this.severity = severity;
        }
    }

    /**
     * Explica a nota: por dimensão, as regras que mais pesam, quanto cada uma tirou e onde o teto por regra cortou.
     * Usa as mesmas regras de {@link #calculate(List, Long)}; a soma bate com a nota mostrada.
     *
     * @param findings    achados que contam para a nota
     * @param linesOfCode linhas de código analisadas; nulo = sem ajuste por tamanho
     */
    public ScoreExplanationDto explain(List<Finding> findings, Long linesOfCode) {
        double factor = sizeFactor(linesOfCode);
        double overall = calculate(findings, linesOfCode).getOverall();
        List<ScoreExplanationDto.Dimension> dimensions = new ArrayList<>();
        for (Category category : EXPLAINED) {
            dimensions.add(explainDimension(findings, category, factor));
        }
        return new ScoreExplanationDto(curve, factor, linesOfCode, overall, overall, List.of(), dimensions);
    }

    /** Teto de regras aceitas na simulação "e se", para a consulta não virar um filtro sem limite. */
    static final int MAX_SIMULATED_RULES = 50;

    /**
     * Simulação "e se": a explicação da nota como ficaria sem as regras indicadas (id exato, sem diferenciar
     * maiúsculas). {@code overallBefore} guarda a nota real, para mostrar quanto cada decisão rende.
     */
    public ScoreExplanationDto explainWithout(List<Finding> findings, Long linesOfCode, List<String> removedRules) {
        if (removedRules == null || removedRules.isEmpty()) {
            return explain(findings, linesOfCode);
        }
        List<String> removed = removedRules.stream().filter(r -> r != null && !r.isBlank()).map(String::strip)
                .distinct().limit(MAX_SIMULATED_RULES).toList();
        Set<String> lower = new java.util.HashSet<>();
        removed.forEach(r -> lower.add(r.toLowerCase(Locale.ROOT)));
        List<Finding> rest = findings.stream()
                .filter(f -> f.getRuleId() == null || !lower.contains(f.getRuleId().toLowerCase(Locale.ROOT))).toList();
        ScoreExplanationDto after = explain(rest, linesOfCode);
        double before = calculate(findings, linesOfCode).getOverall();
        return new ScoreExplanationDto(after.curve(), after.sizeFactor(), linesOfCode, before, after.overall(), removed,
                after.dimensions());
    }

    private ScoreExplanationDto.Dimension explainDimension(List<Finding> findings, Category category, double factor) {
        Map<String, Acc> accs = new LinkedHashMap<>();
        for (Finding f : findings) {
            if (f.getCategory() != category) {
                continue;
            }
            String rule = f.getRuleId() == null ? "(sem regra)" : f.getRuleId();
            Acc acc = accs.computeIfAbsent(rule + "|" + f.getSeverity(), k -> new Acc(rule, f.getSeverity()));
            acc.count++;
            acc.raw += findingPenalty(f);
        }
        List<ScoreExplanationDto.Group> groups = new ArrayList<>();
        double critical = 0.0;
        double others = 0.0;
        for (Acc a : accs.values()) {
            boolean capCandidate = a.severity != Severity.CRITICAL && !"(sem regra)".equals(a.ruleId) && isLowSeverity(a.severity);
            double counted = capCandidate ? Math.min(a.raw, MAX_LOW_SEVERITY_PENALTY_PER_RULE) : a.raw;
            if (a.severity == Severity.CRITICAL) {
                critical += counted;
            } else {
                others += counted;
            }
            groups.add(new ScoreExplanationDto.Group(a.ruleId, a.severity, a.count, round(a.raw), round(counted),
                    capCandidate && a.raw > MAX_LOW_SEVERITY_PENALTY_PER_RULE));
        }
        groups.sort(Comparator.comparingDouble((ScoreExplanationDto.Group g) -> g.counted() * (g.severity() == Severity.CRITICAL ? 1.0 : factor))
                .reversed().thenComparing(ScoreExplanationDto.Group::ruleId));
        int hidden = Math.max(0, groups.size() - EXPLAIN_GROUPS_PER_DIMENSION);
        List<ScoreExplanationDto.Group> shown = groups.subList(0, groups.size() - hidden);
        return new ScoreExplanationDto.Dimension(category, DIMENSION_WEIGHT.get(category),
                round(calculateDimensionScore(findings, category, factor)), round(critical + others * factor), hidden,
                List.copyOf(shown));
    }

    /** Converte a penalidade total em nota (0 a 100) conforme a curva configurada. */
    double applyCurve(double totalPenalty) {
        if ("suave".equalsIgnoreCase(curve)) {
            return 100.0 * Math.exp(-Math.max(0.0, totalPenalty) / SMOOTH_SCALE);
        }
        return Math.max(0.0, 100.0 - totalPenalty);
    }

    private boolean isLowSeverity(Severity severity) {
        return severity == Severity.MEDIUM || severity == Severity.LOW || severity == Severity.INFO;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
