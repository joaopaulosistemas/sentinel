package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.RiskScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScoreTrendTest {

    private static AuditRun run(AuditStatus status, Double overall, String baseRef) {
        AuditRun r = new AuditRun();
        r.setStatus(status);
        r.setBaseRef(baseRef);
        if (overall != null) {
            RiskScore s = new RiskScore();
            s.setOverall(overall);
            r.setRiskScore(s);
        }
        return r;
    }

    @Test
    void diferencaEntreAsDuasUltimasCompletas() {
        List<AuditRun> runs = List.of(run(AuditStatus.COMPLETED, 63.0, null), run(AuditStatus.COMPLETED, 61.0, null), run(AuditStatus.COMPLETED, 40.0, null));
        assertEquals(2.0, ScoreTrend.delta(runs));
    }

    @Test
    void ignoraFalhasCanceladasEAuditoriasDePullRequest() {
        List<AuditRun> runs = List.of(
                run(AuditStatus.FAILED, null, null),
                run(AuditStatus.CANCELLED, null, null),
                run(AuditStatus.COMPLETED, 90.0, "main"),      // PR: não conta
                run(AuditStatus.COMPLETED, 54.4, null),
                run(AuditStatus.COMPLETED, 58.0, null));
        assertEquals(-3.6, ScoreTrend.delta(runs));
    }

    @Test
    void semDuasAuditoriasNaoHaTendencia() {
        assertNull(ScoreTrend.delta(List.of()));
        assertNull(ScoreTrend.delta(List.of(run(AuditStatus.COMPLETED, 70.0, null))));
    }
}
