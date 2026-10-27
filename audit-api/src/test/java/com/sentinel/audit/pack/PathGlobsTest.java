package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class PathGlobsTest {

    private static Predicate<String> m(String... patterns) {
        return PathGlobs.matcher(new LinkedHashSet<>(java.util.List.of(patterns)));
    }

    @Test
    void semPadroesNaoCasaNada() {
        assertFalse(PathGlobs.matcher(Set.of()).test("src/A.java"));
    }

    @Test
    void pastaComBarraNoFimPegaTudoDentro() {
        Predicate<String> p = m("docs/evolucao/");
        assertTrue(p.test("docs/evolucao/relatorios/2026-10-22.md"));
        assertTrue(p.test("docs/evolucao"));
        assertFalse(p.test("docs/arquitetura/ARQUITETURA.md"));
        assertFalse(p.test("outro/docs/evolucao/x.md"), "com barra no meio, vale a partir da raiz");
    }

    @Test
    void nomeSemBarraValeEmQualquerPasta() {
        Predicate<String> p = m("*.md", "fixtures");
        assertTrue(p.test("README.md"));
        assertTrue(p.test("docs/a/b.md"));
        assertTrue(p.test("src/test/fixtures/x.json"));
        assertFalse(p.test("src/Main.java"));
    }

    @Test
    void duploAsteriscoAtravessaPastas() {
        Predicate<String> p = m("audit-api/src/test/**", "audit-rules/packs/**/tests/**");
        assertTrue(p.test("audit-api/src/test/java/com/x/ATest.java"));
        assertTrue(p.test("audit-rules/packs/core-security/tests/sentinel-java-extras.java"));
        assertFalse(p.test("audit-api/src/main/java/com/x/A.java"));
    }

    @Test
    void asteriscoSimplesNaoAtravessaBarra() {
        Predicate<String> p = m("src/*.java");
        assertTrue(p.test("src/A.java"));
        assertFalse(p.test("src/sub/A.java"));
    }

    @Test
    void barraNoComecoAncoraNaRaiz() {
        Predicate<String> p = m("/build");
        assertTrue(p.test("build/out.txt"));
        assertFalse(p.test("sub/build/out.txt"));
    }

    @Test
    void normalizaBarraInvertidaEPrefixoPonto() {
        Predicate<String> p = m("docs/");
        assertTrue(p.test(".\\docs\\a.md".replace('\\', '/')));
        assertTrue(p.test("./docs/a.md"));
        assertTrue(p.test("docs\\a.md"));
    }

    @Test
    void interrogacaoCasaUmCaractere() {
        Predicate<String> p = m("v?.txt");
        assertTrue(p.test("v1.txt"));
        assertFalse(p.test("v10.txt"));
    }

    @Test
    void ignoraLinhaVaziaEComentario() {
        Predicate<String> p = m("", "  ", "# nota", "x/");
        assertTrue(p.test("x/a"));
        assertFalse(p.test("# nota"));
    }
}
