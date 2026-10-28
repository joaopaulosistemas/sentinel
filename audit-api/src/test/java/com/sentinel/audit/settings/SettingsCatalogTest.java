package com.sentinel.audit.settings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsCatalogTest {

    /** Variáveis que dão acesso a banco, chaves, caminhos do servidor ou execução de programas: nunca editáveis pela tela. */
    private static final Set<String> SO_NO_AMBIENTE = Set.of(
            "API_KEY", "SECRETS_KEY", "DB_URL", "DB_USER", "DB_PASSWORD", "DB_SCHEMA_MODE", "CORS_ORIGINS",
            "OIDC_AUTH_SERVER_URL", "OIDC_CLIENT_ID", "PACKS_ALLOW_TOOLS", "PACKS_DIRS", "PACKS_GIT_URL",
            "PACKS_GIT_REF", "PACKS_GIT_PATH", "PACKS_CACHE_DIR", "PACKS_PUBLIC_KEY", "PACKS_REQUIRE_SIGNATURE",
            "SEMGREP_CONFIG", "PMD_RULESET", "CLONE_DIR", "SENTINEL_LOCAL_ROOT", "SENTINEL_EVIDENCE_DIR");

    private static SettingDef def(String key) {
        return SettingsCatalog.find(key).orElseThrow(() -> new AssertionError("fora do catálogo: " + key));
    }

    @Test
    void chavesSaoUnicasEPreenchidas() {
        Set<String> chaves = new HashSet<>();
        for (SettingDef d : SettingsCatalog.all()) {
            assertTrue(chaves.add(d.key()), "chave repetida " + d.key());
            assertFalse(d.property().isBlank(), d.key());
            assertFalse(d.label().isBlank(), d.key());
            assertFalse(d.help().isBlank(), d.key());
            assertNotNull(d.def(), d.key());
        }
    }

    @Test
    void oPadraoDeCadaConfiguracaoEditavelPassaNaPropriaValidacao() {
        for (SettingDef d : SettingsCatalog.all()) {
            if (d.editable()) {
                assertNull(SettingsCatalog.validate(d, d.def()), d.key() + " com o padrão " + d.def());
            }
        }
    }

    @Test
    void oQueDaAcessoAoServidorSoSeMudaNoAmbiente() {
        for (String k : SO_NO_AMBIENTE) {
            SettingDef d = def(k);
            assertFalse(d.editable(), k + " não pode ser editável pela tela");
            assertTrue(SettingsCatalog.validate(d, "x").contains("ambiente"), k);
        }
    }

    @Test
    void segredosNuncaSaoEditaveis() {
        for (SettingDef d : SettingsCatalog.all()) {
            if (d.secret()) {
                assertFalse(d.editable(), d.key());
            }
        }
        assertTrue(def("API_KEY").secret());
        assertTrue(def("DB_PASSWORD").secret());
    }

    @Test
    void validaBooleanosENumeros() {
        assertNull(SettingsCatalog.validate(def("TRIVY_ENABLED"), "false"));
        assertEquals("Ligado: use true ou false", SettingsCatalog.validate(def("TRIVY_ENABLED"), "sim"));
        assertNull(SettingsCatalog.validate(def("TRIVY_TIMEOUT_SECONDS"), "120"));
        assertTrue(SettingsCatalog.validate(def("TRIVY_TIMEOUT_SECONDS"), "5").contains("entre"));
        assertTrue(SettingsCatalog.validate(def("TRIVY_TIMEOUT_SECONDS"), "abc").contains("inteiro"));
        assertTrue(SettingsCatalog.validate(def("EVIDENCE_MIN_LINE_COVERAGE"), "101").contains("entre"));
        assertNull(SettingsCatalog.validate(def("EVIDENCE_MIN_LINE_COVERAGE"), "0"));
    }

    @Test
    void validaOpcoesDoEslintSoComArquivosDaImagem() {
        assertNull(SettingsCatalog.validate(def("ESLINT_CONFIG"), "/opt/eslint/eslint.google-angular.config.mjs"));
        assertTrue(SettingsCatalog.validate(def("ESLINT_CONFIG"), "/etc/passwd").contains("inválida"));
    }

    @Test
    void validaListasEUrl() {
        assertNull(SettingsCatalog.validate(def("TRIVY_SCANNERS"), "vuln, secret"));
        assertTrue(SettingsCatalog.validate(def("TRIVY_SCANNERS"), "vuln,rm").contains("'rm'"));
        assertNull(SettingsCatalog.validate(def("OSV_URL"), "https://osv.interno.local"));
        assertTrue(SettingsCatalog.validate(def("OSV_URL"), "file:///etc/passwd").contains("http"));
        assertNull(SettingsCatalog.validate(def("PACKS_TRUSTED_TOOL_PACKS"), "market-tools,java-google-style"));
        assertTrue(SettingsCatalog.validate(def("PACKS_TRUSTED_TOOL_PACKS"), "pacote-de-terceiros").contains("pacote-de-terceiros"));
        assertNull(SettingsCatalog.validate(def("PACKS_DISABLED"), "core-quality,ts-clean-code"));
        assertTrue(SettingsCatalog.validate(def("PACKS_DISABLED"), "../x").contains("não é um id"));
    }

    @Test
    void cadaPropriedadeDoCatalogoExisteNoApplicationProperties() throws IOException {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path props = p.resolve("src/main/resources/application.properties");
        if (!Files.isRegularFile(props)) {
            props = p.resolve("audit-api/src/main/resources/application.properties");
        }
        assertTrue(Files.isRegularFile(props), "application.properties não encontrado a partir de " + p);
        String texto = Files.readString(props, StandardCharsets.UTF_8);
        for (SettingDef d : SettingsCatalog.all()) {
            if (d.property().startsWith("quarkus.oidc.")) {
                continue; // vem de %oidc.quarkus.oidc.*
            }
            assertTrue(texto.lines().anyMatch(l -> l.startsWith(d.property() + "=")), d.key() + " -> " + d.property());
        }
    }

    @Test
    void padroesDoCatalogoBatemComOApplicationProperties() throws IOException {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path props = p.resolve("src/main/resources/application.properties");
        if (!Files.isRegularFile(props)) {
            props = p.resolve("audit-api/src/main/resources/application.properties");
        }
        String texto = Files.readString(props, StandardCharsets.UTF_8);
        for (SettingDef d : SettingsCatalog.all()) {
            if (!d.editable()) {
                continue;
            }
            String linha = texto.lines().filter(l -> l.startsWith(d.property() + "=")).findFirst().orElseThrow();
            String esperado = "${" + d.key() + ":" + d.def() + "}";
            assertEquals(esperado, linha.substring(linha.indexOf('=') + 1), d.key());
        }
    }
}
