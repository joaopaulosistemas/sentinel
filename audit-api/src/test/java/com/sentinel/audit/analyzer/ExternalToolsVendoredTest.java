package com.sentinel.audit.analyzer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bibliotecas de terceiros copiadas para o projeto ficam fora do lint e das métricas. */
class ExternalToolsVendoredTest {

    @Test
    void reconheceBibliotecasEmbutidas() {
        assertTrue(ExternalTools.isVendoredLibrary("app/src/main/webapp/js/jquery-2.0.3.js"));
        assertTrue(ExternalTools.isVendoredLibrary("web/css/font-awesome.css"));
        assertTrue(ExternalTools.isVendoredLibrary("web/css/patternfly.css"));
        assertTrue(ExternalTools.isVendoredLibrary("web/js/bootstrap.js"));
        assertTrue(ExternalTools.isVendoredLibrary("web/css/bootstrap-theme.css"));
        assertTrue(ExternalTools.isVendoredLibrary("static/app.min.js"));
        assertTrue(ExternalTools.isVendoredLibrary("static\\js\\jquery.min.js"));
    }

    @Test
    void naoConfundeCodigoDoProjeto() {
        assertFalse(ExternalTools.isVendoredLibrary("src/bootstrap.ts"));
        assertFalse(ExternalTools.isVendoredLibrary("src/app/bootstrap.js"));
        assertFalse(ExternalTools.isVendoredLibrary("src/main/js/jquery-plugin-pedidos.ts"));
        assertFalse(ExternalTools.isVendoredLibrary("src/main/java/Fontes.java"));
        assertFalse(ExternalTools.isVendoredLibrary("src/styles/tema.css"));
        assertFalse(ExternalTools.isVendoredLibrary(null));
    }

    @Test
    void pastaIgnoradaIncluiBibliotecaEmbutida() {
        assertTrue(ExternalTools.inIgnoredDir("web/js/jquery-2.0.3.js"));
        assertTrue(ExternalTools.inIgnoredDir("node_modules/x/index.js"));
        assertFalse(ExternalTools.inIgnoredDir("web/js/pedidos.js"));
    }

    @Test
    void semgrepExcluiAsMesmasBibliotecas() {
        assertTrue(SemgrepAnalyzer.VENDORED_EXCLUDES.containsAll(
                java.util.List.of("jquery*.js", "font-awesome*.css", "patternfly*.css", "js/bootstrap*.js")));
    }
}
