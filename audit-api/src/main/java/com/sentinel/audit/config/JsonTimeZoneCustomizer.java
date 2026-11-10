package com.sentinel.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.JsonSerializer;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * As datas do banco são {@code LocalDateTime} (horário do servidor, sem fuso). Na API elas saem com o deslocamento
 * explícito ({@code 2026-11-09T14:30:05Z}), para que o navegador e o CLI não as leiam como horário local.
 */
@Singleton
public class JsonTimeZoneCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.registerModule(moduleFor(ZoneId.systemDefault()));
    }

    static SimpleModule moduleFor(ZoneId zone) {
        SimpleModule module = new SimpleModule("sentinel-local-date-time-offset");
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value.atZone(zone)));
            }
        });
        return module;
    }
}
