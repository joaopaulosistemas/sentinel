package com.sentinel.audit.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleDraftTest {

    private static final String OK = """
            rules:
              - id: local-sem-system-out
                languages: [java]
                message: Use o logger em vez de System.out
                severity: WARNING
                pattern: System.out.println(...)
            """;

    @Test
    void regraValidaPassa() {
        assertEquals(List.of(), RuleDraft.validate("sem-system-out", OK));
    }

    @Test
    void idPrecisaSerSlugEIgualAoDaRegra() {
        assertFalse(RuleDraft.validate("Sem Espaço", OK).isEmpty());
        assertFalse(RuleDraft.validate("ab", OK).isEmpty());
        assertFalse(RuleDraft.validate("outro-id", OK).isEmpty(), "id dentro do YAML diferente");
        assertEquals("local-x-y", RuleDraft.semgrepId("x-y"));
    }

    @Test
    void recusaVazioGrandeIlegivelEEstruturaErrada() {
        assertFalse(RuleDraft.validate("regra-x", " ").isEmpty());
        assertFalse(RuleDraft.validate("regra-x", "a".repeat(9000)).isEmpty());
        assertFalse(RuleDraft.validate("regra-x", "rules: [").isEmpty());
        assertFalse(RuleDraft.validate("regra-x", "foo: bar").isEmpty());
        assertFalse(RuleDraft.validate("regra-x", "rules: []").isEmpty());
        String two = OK + "  - id: local-b\n    languages: [java]\n    message: m\n    severity: INFO\n    pattern: a\n";
        assertFalse(RuleDraft.validate("sem-system-out", two).isEmpty(), "só uma regra por proposta");
    }

    @Test
    void exigeCamposEUmUnicoCasador() {
        assertFalse(RuleDraft.validate("sem-system-out", OK.replace("severity: WARNING", "severity: GRAVE")).isEmpty());
        assertFalse(RuleDraft.validate("sem-system-out", OK.replace("message: Use o logger em vez de System.out\n", "")).isEmpty());
        assertFalse(RuleDraft.validate("sem-system-out", OK.replace("languages: [java]\n", "")).isEmpty());
        assertFalse(RuleDraft.validate("sem-system-out", OK + "    pattern-regex: foo\n").isEmpty());
        assertFalse(RuleDraft.validate("sem-system-out", OK.replace("pattern: System.out.println(...)\n", "")).isEmpty());
    }

    @Test
    void recusaCamposQueReescrevemCodigoEExpressaoPerigosa() {
        assertFalse(RuleDraft.validate("sem-system-out", OK + "    fix: logger.info(...)\n").isEmpty());
        String rx = OK.replace("pattern: System.out.println(...)", "pattern-regex: \"(a+)+$\"");
        assertFalse(RuleDraft.validate("sem-system-out", rx).isEmpty(), "repetição dentro de repetição");
        assertTrue(RuleDraft.validate("sem-system-out", OK.replace("pattern: System.out.println(...)", "pattern-regex: \"System\\\\.out\"")).isEmpty());
        assertFalse(RuleDraft.validate("sem-system-out", OK.replace("pattern: System.out.println(...)", "pattern-regex: \"(\"")).isEmpty());
    }
}
