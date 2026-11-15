package com.sentinel.audit.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditMetricsTest {

    @Test
    void analisadorTerminadoGravaDuracaoPorSituacaoEContaOsAchados() {
        SimpleMeterRegistry r = new SimpleMeterRegistry();

        AuditMetrics.analyzerFinished(r, "Semgrep", "OK", 1500, 7);
        AuditMetrics.analyzerFinished(r, "Semgrep", "OK", 500, 3);
        AuditMetrics.analyzerFinished(r, "PMD", "FALHOU", 0, 0);

        assertEquals(2, r.get(AuditMetrics.ANALYZER_DURATION).tag("analyzer", "Semgrep").tag("status", "OK").timer().count());
        assertEquals(2000.0, r.get(AuditMetrics.ANALYZER_DURATION).tag("analyzer", "Semgrep").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
        assertEquals(10.0, r.get(AuditMetrics.FINDINGS).tag("analyzer", "Semgrep").counter().count());
        assertEquals(1, r.get(AuditMetrics.ANALYZER_DURATION).tag("analyzer", "PMD").tag("status", "FALHOU").timer().count());
        assertNull(r.find(AuditMetrics.FINDINGS).tag("analyzer", "PMD").counter(), "sem achados não cria o contador");
    }

    @Test
    void auditoriaTerminadaGravaDuracaoPorResultado() {
        SimpleMeterRegistry r = new SimpleMeterRegistry();

        AuditMetrics.auditFinished(r, "COMPLETED", 3000);
        AuditMetrics.auditFinished(r, "FAILED", 100);

        assertEquals(1, r.get(AuditMetrics.AUDIT_DURATION).tag("outcome", "COMPLETED").timer().count());
        assertEquals(1, r.get(AuditMetrics.AUDIT_DURATION).tag("outcome", "FAILED").timer().count());
    }

    @Test
    void duracaoNegativaViraZeroEUsoSemRegistroLigadoNaoQuebra() {
        SimpleMeterRegistry r = new SimpleMeterRegistry();
        AuditMetrics.analyzerFinished(r, "X", "OK", -5, 0);
        assertEquals(0.0, r.get(AuditMetrics.ANALYZER_DURATION).timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));

        AuditMetrics.analyzerFinished("X", "OK", 10, 1);   // registro global sem nenhum registro ligado
        AuditMetrics.auditFinished("COMPLETED", 10);
    }
}
