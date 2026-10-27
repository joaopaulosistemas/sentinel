package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingSuppression;
import com.sentinel.audit.model.SuppressionReason;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.repository.FindingSuppressionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mede a qualidade das regras a partir das decisões do time: regra cujos findings viram "falso positivo"
 * com frequência é candidata a ser revista ou desligada.
 * <p>
 * Base de cálculo: os findings da última auditoria concluída de cada sistema (denominador) e as supressões
 * marcadas como {@code FALSE_POSITIVE} (numerador). Findings de dependência ficam de fora: um CVE não é uma regra.
 */
@ApplicationScoped
public class RuleQualityService {

    /** Amostra mínima para opinar: com poucos casos, 2 de 3 falsos positivos não diz nada. */
    static final int MIN_SAMPLE = 5;
    /** A partir desta taxa de falso positivo a regra é marcada para revisão. */
    static final double REVIEW_RATE = 0.4;

    /** Resultado por regra. {@code falsePositiveRate} vai de 0 a 1. */
    public record RuleQuality(String ruleId, String tool, long total, long falsePositive, long acceptedRisk,
                              double falsePositiveRate, boolean needsReview) {}

    /** Findings de uma auditoria e as decisões do sistema (fingerprint -> motivo da supressão). */
    public record Snapshot(List<Finding> findings, Map<String, SuppressionReason> reasonByFingerprint) {}

    @Inject
    AuditedSystemRepository systemRepository;
    @Inject
    AuditRunRepository auditRunRepository;
    @Inject
    FindingRepository findingRepository;
    @Inject
    FindingSuppressionRepository suppressionRepository;

    /** Precisa de transação ativa (quem chama) para os repositórios. */
    public List<RuleQuality> compute() {
        List<Snapshot> snapshots = new ArrayList<>();
        Map<Long, AuditRun> latestBySystem = auditRunRepository.findLatestCompletedBySystem();
        Map<Long, List<Finding>> findingsByRun = findingRepository.findByAuditRunIds(
                latestBySystem.values().stream().map(AuditRun::getId).toList());
        for (AuditedSystem system : systemRepository.listAll()) {
            AuditRun latest = latestBySystem.get(system.getId());
            if (latest == null) {
                continue;
            }
            Map<String, SuppressionReason> reasons = new HashMap<>();
            for (FindingSuppression s : suppressionRepository.findBySystemId(system.getId())) {
                reasons.put(s.getFingerprint(), s.getReason());
            }
            snapshots.add(new Snapshot(findingsByRun.getOrDefault(latest.getId(), List.of()), reasons));
        }
        return compute(snapshots);
    }

    /** Parte pura do cálculo. */
    static List<RuleQuality> compute(List<Snapshot> snapshots) {
        Map<String, long[]> counts = new HashMap<>();       // [total, falsoPositivo, riscoAceito]
        Map<String, String> tools = new HashMap<>();
        for (Snapshot snap : snapshots) {
            for (Finding f : snap.findings()) {
                if (f.getCategory() == Category.DEPENDENCY || f.getRuleId() == null || f.getRuleId().isBlank()) {
                    continue;
                }
                String rule = normalize(f);
                long[] c = counts.computeIfAbsent(rule, k -> new long[3]);
                tools.putIfAbsent(rule, f.getToolSource());
                c[0]++;
                SuppressionReason reason = f.getFingerprint() == null ? null : snap.reasonByFingerprint().get(f.getFingerprint());
                if (reason == SuppressionReason.FALSE_POSITIVE) {
                    c[1]++;
                } else if (reason == SuppressionReason.ACCEPTED_RISK) {
                    c[2]++;
                }
            }
        }
        List<RuleQuality> out = new ArrayList<>();
        counts.forEach((rule, c) -> {
            double rate = c[0] == 0 ? 0 : (double) c[1] / c[0];
            out.add(new RuleQuality(rule, tools.get(rule), c[0], c[1], c[2], rate, c[0] >= MIN_SAMPLE && rate >= REVIEW_RATE));
        });
        out.sort(Comparator.comparing(RuleQuality::needsReview).reversed()
                .thenComparing(Comparator.comparingDouble(RuleQuality::falsePositiveRate).reversed())
                .thenComparing(Comparator.comparingLong(RuleQuality::total).reversed())
                .thenComparing(RuleQuality::ruleId));
        return out;
    }

    /** O Semgrep prefixa o id com o caminho do arquivo de regras, que muda entre ambientes: fica só o nome da regra. */
    static String normalize(Finding f) {
        String id = f.getRuleId();
        if ("Semgrep".equalsIgnoreCase(f.getToolSource())) {
            int dot = id.lastIndexOf('.');
            if (dot >= 0 && dot < id.length() - 1) {
                return id.substring(dot + 1);
            }
        }
        return id;
    }
}
