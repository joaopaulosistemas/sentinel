package com.sentinel.audit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Guarda a mitigação da CVE-2026-118259 (ReDoS em W3CTraceContextPropagator.extract, OpenTelemetry < 1.67.0): enquanto o
 * Quarkus trouxer um OpenTelemetry vulnerável, o {@code traceparent} recebido não pode ser extraído.
 */
class OtelPropagatorsTest {

    private static Properties carrega() throws Exception {
        Properties p = new Properties();
        try (InputStream in = OtelPropagatorsTest.class.getResourceAsStream("/application.properties")) {
            assertNotNull(in, "application.properties não encontrado");
            p.load(in);
        }
        return p;
    }

    @Test
    void semTracecontextNosPropagadores() throws Exception {
        String valor = carrega().getProperty("quarkus.otel.propagators");
        assertNotNull(valor, "quarkus.otel.propagators deve estar definido");
        assertEquals("baggage", valor);
        assertFalse(valor.contains("tracecontext"), "tracecontext reabre o ReDoS do OpenTelemetry < 1.67.0");
    }
}
