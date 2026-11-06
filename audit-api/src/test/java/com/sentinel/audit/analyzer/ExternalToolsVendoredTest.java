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
        assertFalse(ExternalTools.hasThirdPartyBanner((String) null));
    }

    @Test
    void testesDeIntegracaoDoMavenFicamFora() {
        assertTrue(ExternalTools.inIgnoredDir("src/it/java/org/owasp/webgoat/integration/AccessControlIntegrationTest.java"));
        assertTrue(ExternalTools.inIgnoredDir("modulo/src/it/java/A.java"));
        assertTrue(ExternalTools.inIgnoredDir("src/integrationTest/java/A.java"));
        assertTrue(ExternalTools.inIgnoredDir("app/integration-tests/A.java"));
    }

    @Test
    void pastaItQueNaoESrcItNaoESaTeste() {
        assertFalse(ExternalTools.inIgnoredDir("src/main/resources/i18n/it/mensagens.ts"));
        assertFalse(ExternalTools.inIgnoredDir("src/item/Pedido.java"));
        assertFalse(ExternalTools.inIgnoredDir("it/Pedido.java"));
    }

    @Test
    void libEThirdPartyEmAtivosEBibliotecaESaidaDeBuildComHashTambem() {
        assertTrue(ExternalTools.isVendoredLibrary("src/main/resources/static/lib/layui/layui.js"));
        assertTrue(ExternalTools.isVendoredLibrary("src/main/resources/static/lib/ueditor/third-party/highcharts/modules/canvas-tools.js"));
        assertTrue(ExternalTools.isVendoredLibrary("src/main/resources/static/other/infoleak/chunk-0226s3f2.57e3ed6f.js"));
        assertTrue(ExternalTools.isVendoredLibrary("public/js/app.3f2a9c1b.css"));
    }

    @Test
    void libForaDeAtivosOuNomeSemHashNaoEBiblioteca() {
        assertFalse(ExternalTools.isVendoredLibrary("src/lib/util.js"));
        assertFalse(ExternalTools.isVendoredLibrary("src/main/java/lib/Util.java"));
        assertFalse(ExternalTools.isVendoredLibrary("src/app/chunk-loader.js"));
        assertFalse(ExternalTools.isVendoredLibrary("static/js/relatorio.2024.js"));
        assertFalse(ExternalTools.isVendoredLibrary("src/app/main.3f2a9c1b.js"));
    }

    @Test
    void comentarioDeLinhaComLicencaEEnderecoIndicaTerceiros() {
        assertTrue(ExternalTools.hasThirdPartyBanner("// CodeMirror, copyright (c) by Marijn Haverbeke and others\n// Distributed under an MIT license: https://codemirror.net/LICENSE\n\n(function(){})();"));
        assertFalse(ExternalTools.hasThirdPartyBanner("// utilitarios\nfunction f(){}\n// MIT license https://x.org"));
        assertFalse(ExternalTools.hasThirdPartyBanner("// Copyright Acme, MIT\nvar a;"));
    }

    @Test
    void motorExcluiBibliotecasEmbutidasDoDiretorio(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path lib = java.nio.file.Files.createDirectories(dir.resolve("src/main/resources/static/lib/layui"));
        java.nio.file.Path js = java.nio.file.Files.createDirectories(dir.resolve("src/main/resources/static/js"));
        java.nio.file.Files.writeString(lib.resolve("layui.js"), "var a;");
        java.nio.file.Files.writeString(js.resolve("cm.js"), "// X, copyright\n// MIT license: https://x.org/LICENSE\nvar a;");
        java.nio.file.Files.writeString(js.resolve("pedidos.js"), "var a;");
        java.util.List<String> v = ExternalTools.vendoredFiles(dir);
        assertTrue(v.contains("src/main/resources/static/lib/layui/layui.js"));
        assertTrue(v.contains("src/main/resources/static/js/cm.js"));
        assertFalse(v.contains("src/main/resources/static/js/pedidos.js"));
    }

    @Test
    void cabecalhoPadraoDaFundacaoApacheIndicaTerceiros() {
        String asf = "/*\n* Licensed to the Apache Software Foundation (ASF) under one\n* or more contributor license agreements.  See the NOTICE file\n* distributed with this work for additional information\n* regarding copyright ownership.  The ASF licenses this file\n* to you under the Apache License, Version 2.0 (the\n* \"License\"); you may not use this file except in compliance\n* with the License.  You may obtain a copy of the License at\n*\n*   http://www.apache.org/licenses/LICENSE-2.0\n*/\n(function(){})();";
        assertTrue(ExternalTools.hasThirdPartyBanner(asf));
        assertFalse(ExternalTools.hasThirdPartyBanner("/* Meu sistema interno. Licensed to Acme. */\nvar a;"));
    }
}
