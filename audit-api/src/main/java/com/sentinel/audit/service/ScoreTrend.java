package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;

import java.util.List;

/** Variação da nota entre as duas últimas auditorias completas (as de PR ficam de fora). */
public final class ScoreTrend {

    private ScoreTrend() {}

    /**
     * @param runsNewestFirst execuções do sistema, da mais nova para a mais antiga
     * @return nota da mais nova menos a da anterior, arredondada a uma casa; {@code null} se não houver duas
     */
    public static Double delta(List<AuditRun> runsNewestFirst) {
        List<Double> scores = runsNewestFirst.stream()
                .filter(r -> r.getStatus() == AuditStatus.COMPLETED)
                .filter(r -> r.getBaseRef() == null || r.getBaseRef().isBlank())
                .filter(r -> r.getRiskScore() != null && r.getRiskScore().getOverall() != null)
                .limit(2)
                .map(r -> r.getRiskScore().getOverall())
                .toList();
        if (scores.size() < 2) {
            return null;
        }
        return Math.round((scores.get(0) - scores.get(1)) * 10.0) / 10.0;
    }
}
