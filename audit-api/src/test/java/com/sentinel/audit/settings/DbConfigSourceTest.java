package com.sentinel.audit.settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbConfigSourceTest {

    @AfterEach
    void limpa() {
        System.clearProperty("sentinel.settings.from-db");
    }

    @Test
    void temPrioridadeSobreAsVariaveisDeAmbiente() {
        // Variáveis de ambiente têm ordinal 300 no MicroProfile Config; a tela precisa valer por cima delas.
        assertTrue(new DbConfigSource().getOrdinal() > 300);
    }

    @Test
    void desligadoNaoLeNadaENaoTocaNoBanco() {
        System.setProperty("sentinel.settings.from-db", "false");
        DbConfigSource s = new DbConfigSource();
        assertTrue(s.getProperties().isEmpty());
        assertNull(s.getValue("sentinel.audit.trivy.enabled"));
        assertEquals("sentinel-settings-db", s.getName());
    }

    @Test
    void semBancoDisponivelAApiSobeSemConfiguracoesDaTela() {
        System.setProperty("sentinel.settings.from-db", "true");
        // Sem Postgres nesta porta, a leitura falha em silêncio e a fonte fica vazia.
        DbConfigSource s = new DbConfigSource();
        assertNull(s.getValue("sentinel.audit.trivy.enabled"));
    }

    @Test
    void estaRegistradaParaOServiceLoaderDoMicroProfileConfig() {
        boolean achou = StreamSupport.stream(ServiceLoader.load(org.eclipse.microprofile.config.spi.ConfigSource.class).spliterator(), false)
                .anyMatch(c -> c instanceof DbConfigSource);
        assertTrue(achou);
    }
}
