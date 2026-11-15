package com.sentinel.audit.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * Métricas de negócio do Sentinel (o que a auditoria fez), além das de infraestrutura que o Quarkus já expõe.
 * Usa o registro global do Micrometer: o Quarkus liga o Prometheus a ele, e sem registro ligado (testes) nada é gravado.
 * Só rótulos de baixa cardinalidade (analisador e situação); nunca o repositório nem o id da auditoria.
 */
public final class AuditMetrics {

    static final String ANALYZER_DURATION = "sentinel.analyzer.duration";
    static final String FINDINGS = "sentinel.findings";
    static final String AUDIT_DURATION = "sentinel.audit.duration";

    /** Limites dos baldes de duração: de 5 segundos a 30 minutos (o que cabe numa análise de código). */
    private static final Duration[] BUCKETS = {Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(30),
            Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(30)};

    private AuditMetrics() {
    }

    /** Um analisador terminou: quanto demorou, com que situação (OK, PARCIAL, FALHOU) e quantos achados trouxe. */
    public static void analyzerFinished(String analyzer, String status, long millis, int findings) {
        analyzerFinished(Metrics.globalRegistry, analyzer, status, millis, findings);
    }

    static void analyzerFinished(MeterRegistry registry, String analyzer, String status, long millis, int findings) {
        Timer.builder(ANALYZER_DURATION).description("Duração de cada analisador")
                .tag("analyzer", analyzer).tag("status", status).serviceLevelObjectives(BUCKETS).register(registry).record(Duration.ofMillis(Math.max(0, millis)));
        if (findings > 0) {
            Counter.builder(FINDINGS).description("Achados por analisador").tag("analyzer", analyzer)
                    .register(registry).increment(findings);
        }
    }

    /** Uma auditoria terminou ({@code COMPLETED}, {@code FAILED} ou {@code CANCELLED}). */
    public static void auditFinished(String outcome, long millis) {
        auditFinished(Metrics.globalRegistry, outcome, millis);
    }

    static void auditFinished(MeterRegistry registry, String outcome, long millis) {
        Timer.builder(AUDIT_DURATION).description("Duração da auditoria, do início ao fim")
                .tag("outcome", outcome).serviceLevelObjectives(BUCKETS).register(registry).record(Duration.ofMillis(Math.max(0, millis)));
    }
}
