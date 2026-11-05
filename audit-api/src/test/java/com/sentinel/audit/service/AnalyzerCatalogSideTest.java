package com.sentinel.audit.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cada analisador conhecido tem um lado; o que não é só de Java nem só de TypeScript é comum. */
class AnalyzerCatalogSideTest {

    @Test
    void todoAnalisadorConhecidoTemLado() {
        for (String name : AnalyzerCatalog.names()) {
            String side = AnalyzerCatalog.sideOf(name);
            org.junit.jupiter.api.Assertions.assertTrue(java.util.List.of("back", "front", "common").contains(side), name + " -> " + side);
        }
    }

    @Test
    void ladosConhecidos() {
        assertEquals("back", AnalyzerCatalog.sideOf("PMD"));
        assertEquals("back", AnalyzerCatalog.sideOf("OWASP-Dependency"));
        assertEquals("front", AnalyzerCatalog.sideOf("ESLint"));
        assertEquals("front", AnalyzerCatalog.sideOf("Código morto"));
        assertEquals("common", AnalyzerCatalog.sideOf("Gitleaks"));
        assertEquals("common", AnalyzerCatalog.sideOf("CleanCode"));
    }

    @Test
    void nomeDesconhecidoOuNuloEComum() {
        assertEquals("common", AnalyzerCatalog.sideOf("Inexistente"));
        assertEquals("common", AnalyzerCatalog.sideOf(null));
    }
}
