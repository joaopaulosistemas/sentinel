package com.sentinel.audit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OffsetDateTimeSerializerTest {

    private static ObjectMapper mapper(ZoneId zone) {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(JsonTimeZoneCustomizer.moduleFor(zone));
        return m;
    }

    @Test
    void dataSemFusoSaiComODeslocamentoDoServidor() throws Exception {
        String json = mapper(ZoneOffset.UTC).writeValueAsString(LocalDateTime.of(2026, 11, 9, 14, 30, 5));
        assertEquals("\"2026-11-09T14:30:05Z\"", json);
    }

    @Test
    void respeitaOFusoConfigurado() throws Exception {
        String json = mapper(ZoneId.of("America/Sao_Paulo")).writeValueAsString(LocalDateTime.of(2026, 11, 9, 14, 30, 5));
        assertEquals("\"2026-11-09T14:30:05-03:00\"", json);
    }

    @Test
    void oTextoGeradoRepresentaOMesmoInstante() throws Exception {
        String json = mapper(ZoneId.of("America/Sao_Paulo")).writeValueAsString(LocalDateTime.of(2026, 11, 9, 14, 30, 5));
        OffsetDateTime parsed = OffsetDateTime.parse(json.replace("\"", ""));
        assertTrue(parsed.toInstant().equals(java.time.Instant.parse("2026-11-09T17:30:05Z")));
    }
}
