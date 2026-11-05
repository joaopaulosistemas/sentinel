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
                java.util.List.of("jquery*.js", "font-awesome*.css", "patternfly*.css", "js/bootstrap*.js", "static/plugins")));
    }

    @Test
    void jsOuCssMuitoGrandeEPacoteDeTerceiros() {
        assertTrue(ExternalTools.isOversizedBundle("frontend/src/assets/private/three.js", 853_000));
        assertTrue(ExternalTools.isOversizedBundle("web/estilos/tema.css", 600_000));
        assertTrue(ExternalTools.isOversizedBundle("a/b.MJS", 500_000));
    }

    @Test
    void arquivoGrandeDeOutroTipoOuPequenoNaoEPacote() {
        assertFalse(ExternalTools.isOversizedBundle("src/app/pedidos.js", 20_000));
        assertFalse(ExternalTools.isOversizedBundle("src/app/pedidos.js", 399_999));
        assertFalse(ExternalTools.isOversizedBundle("src/Grande.java", 900_000));
        assertFalse(ExternalTools.isOversizedBundle("src/grande.ts", 900_000));
        assertFalse(ExternalTools.isOversizedBundle(null, 900_000));
    }

    @Test
    void listaPacotesGrandesParaOMotorExcluir(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path assets = java.nio.file.Files.createDirectories(dir.resolve("front/assets"));
        java.nio.file.Files.write(assets.resolve("three.js"), new byte[450_000]);
        java.nio.file.Files.write(assets.resolve("app.js"), new byte[1_000]);
        assertTrue(ExternalTools.oversizedBundles(dir).contains("front/assets/three.js"));
        assertFalse(ExternalTools.oversizedBundles(dir).contains("front/assets/app.js"));
    }

    @Test
    void jsECssEmPastaDePluginsOuLibsDeAtivosEBibliotecaDeTerceiros() {
        assertTrue(ExternalTools.isVendoredLibrary("src/main/resources/webgoat/static/plugins/bootstrap-wysihtml5/js/wysihtml5-0.3.0.js"));
        assertTrue(ExternalTools.isVendoredLibrary("src/main/resources/webgoat/static/js/libs/ace.js"));
        assertTrue(ExternalTools.isVendoredLibrary("src/main/resources/webgoat/static/css/libs/animate.css"));
        assertTrue(ExternalTools.isVendoredLibrary("public/vendor/x/tema.css"));
        assertTrue(ExternalTools.inIgnoredDir("webapp/plugins/nanoScroller/jquery.nanoscroller.js"));
    }

    @Test
    void pastaPluginsOuLibsForaDeAtivosOuDeOutroTipoNaoEBiblioteca() {
        assertFalse(ExternalTools.isVendoredLibrary("build/plugins/MeuPlugin.java"));
        assertFalse(ExternalTools.isVendoredLibrary("src/plugins/loader.ts"));
        assertFalse(ExternalTools.isVendoredLibrary("src/main/resources/static/js/app/lessons.js"));
        assertFalse(ExternalTools.isVendoredLibrary("src/libs/util.js"));
    }

    @Test
    void cabecalhoDeLicencaComEnderecoIndicaBibliotecaDeTerceiros() {
        assertTrue(ExternalTools.hasThirdPartyBanner("@charset \"UTF-8\";\n\n/*!\nAnimate.css - http://daneden.me/animate\nLicensed under the MIT license\n*/\n.a{}"));
        assertTrue(ExternalTools.hasThirdPartyBanner("/* Asciidoctor default stylesheet | MIT License | https://asciidoctor.org */\nbody{}"));
        assertTrue(ExternalTools.hasThirdPartyBanner("/*! Plugin v1.10.2 | Released under the MIT license */\n(function(){})();"));
        assertTrue(ExternalTools.hasThirdPartyBanner("/**\n * @license Lodash\n */\nvar x;"));
    }

    @Test
    void cabecalhoSemLicencaOuForaDoInicioNaoIndicaTerceiros() {
        assertFalse(ExternalTools.hasThirdPartyBanner("/* ====== Base styles ====== */\nbody{}"));
        assertFalse(ExternalTools.hasThirdPartyBanner("define(['jquery'], function($){});"));
        assertFalse(ExternalTools.hasThirdPartyBanner("/*\nbody{min-width:800px;}\n*/"));
        assertFalse(ExternalTools.hasThirdPartyBanner("function f(){}\n/* MIT License https://x.org */"));
        assertFalse(ExternalTools.hasThirdPartyBanner("/* Copyright Acme. Licensed under MIT. */\nvar a;"));
        assertFalse(ExternalTools.hasThirdPartyBanner(null));
    }
}
