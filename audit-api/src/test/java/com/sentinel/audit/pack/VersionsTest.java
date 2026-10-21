package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionsTest {

    @Test
    void comparaOperadores() {
        assertEquals(Boolean.TRUE, Versions.satisfies("2.17.1", ">=2.17.1"));
        assertEquals(Boolean.FALSE, Versions.satisfies("2.14.1", ">=2.17.1"));
        assertEquals(Boolean.TRUE, Versions.satisfies("2.17.2", ">2.17.1"));
        assertEquals(Boolean.FALSE, Versions.satisfies("2.17.1", ">2.17.1"));
        assertEquals(Boolean.TRUE, Versions.satisfies("5.9", "<6"));
        assertEquals(Boolean.FALSE, Versions.satisfies("6.0.0", "<6"));
        assertEquals(Boolean.TRUE, Versions.satisfies("3", "<=3.0.0"));
        assertEquals(Boolean.TRUE, Versions.satisfies("1.2.0", "1.2"));
        assertEquals(Boolean.TRUE, Versions.satisfies("1.2.0", "=1.2"));
    }

    @Test
    void naoEscolheAlfabeticamenteVersaoDeDoisDigitos() {
        assertEquals(Boolean.TRUE, Versions.satisfies("10.0.0", ">=9.0.0"), "10 > 9 (não comparar como texto)");
        assertEquals(Boolean.TRUE, Versions.satisfies("1.10.0", ">1.9.9"));
    }

    @Test
    void prefixoComCuringa() {
        assertEquals(Boolean.TRUE, Versions.satisfies("1.4.7", "1.*"));
        assertEquals(Boolean.FALSE, Versions.satisfies("2.0.0", "1.*"));
        assertEquals(Boolean.TRUE, Versions.satisfies("1.4.7", "1.4.*"));
        assertEquals(Boolean.FALSE, Versions.satisfies("1.5.0", "1.4.*"));
    }

    @Test
    void qualificadoresSaoIgnoradosEVersaoDesconhecidaNaoReprova() {
        assertEquals(Boolean.TRUE, Versions.satisfies("2.17.1-SNAPSHOT", ">=2.17.1"));
        assertEquals(Boolean.TRUE, Versions.satisfies("v2.0.0", ">=2"));
        assertNull(Versions.satisfies("${log4j.version}", ">=2.17.1"), "variável Maven: não dá para saber");
        assertNull(Versions.satisfies("[1.0,2.0)", ">=2.17.1"), "intervalo: não dá para saber");
        assertNull(Versions.satisfies(null, ">=1"));
        assertNull(Versions.satisfies("1.0", ">=abc"), "regra inválida");
    }
}
