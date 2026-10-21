package com.sentinel.audit.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Usa o Semgrep real (sem rede: regras locais, métricas desligadas). */
class RulePlaygroundServiceTest {

    private static final String RULE = """
            rules:
              - id: sem-system-exit
                message: "Não encerre a JVM"
                severity: WARNING
                languages: [java]
                pattern: System.exit(...)
            """;

    private static final String CODE = "class A {\n  void f() {\n    System.exit(1);\n  }\n  void g() {\n    System.exit(2);\n  }\n}\n";

    private static RulePlaygroundService service() {
        return new RulePlaygroundService();
    }

    @Test
    void executaARegraDeVerdadeEMostraOQueCasou() {
        RulePlaygroundService.Result r = service().run(RULE, CODE, "java");

        assertTrue(r.valid(), r.errors().toString());
        assertEquals(2, r.matches().size(), r.engineErrors().toString());
        RulePlaygroundService.Match m = r.matches().get(0);
        assertEquals("sem-system-exit", m.ruleId());
        assertEquals(3, m.line());
        assertEquals("WARNING", m.severity());
        assertEquals("Não encerre a JVM", m.message());
        assertEquals("    System.exit(1);", m.snippet());
        assertEquals(List.of(), r.warnings(), "falta de teste não é aviso no laboratório");
    }

    @Test
    void codigoSemOcorrenciaNaoCasaNada() {
        RulePlaygroundService.Result r = service().run(RULE, "class B { void f() { log(); } }", "java");
        assertTrue(r.valid());
        assertEquals(0, r.matches().size());
    }

    @Test
    void regraInvalidaDevolveOsErrosDoCarregadorSemRodarSemgrep() {
        String ruim = RULE.replace("WARNING", "ALTISSIMA").replace("    message: \"Não encerre a JVM\"\n", "");
        RulePlaygroundService.Result r = service().run(ruim, CODE, "java");

        assertFalse(r.valid());
        String all = String.join(" | ", r.errors());
        assertTrue(all.contains("'message' é obrigatório"), all);
        assertTrue(all.contains("'severity' deve ser ERROR, WARNING ou INFO"), all);
        assertEquals(0, r.matches().size());
    }

    @Test
    void yamlMaliciosoNaoInstanciaClasses() {
        RulePlaygroundService.Result r = service().run("x: !!java.lang.ProcessBuilder [[\"touch\", \"/tmp/pg-pwned\"]]\n", CODE, "java");
        assertFalse(r.valid());
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of("/tmp/pg-pwned")));
    }

    @Test
    void validaEntradas() {
        RulePlaygroundService s = service();
        assertThrows(IllegalArgumentException.class, () -> s.run("", CODE, "java"));
        assertThrows(IllegalArgumentException.class, () -> s.run(RULE, null, "java"));
        assertThrows(IllegalArgumentException.class, () -> s.run(RULE, CODE, "exe"));
        assertThrows(IllegalArgumentException.class, () -> s.run(RULE, CODE, "../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> s.run("x".repeat(RulePlaygroundService.MAX_RULE_CHARS + 1), CODE, "java"));
        assertThrows(IllegalArgumentException.class, () -> s.run(RULE, "x".repeat(RulePlaygroundService.MAX_CODE_CHARS + 1), "java"));
    }

    @Test
    void semSemgrepDesligadoOuOcupado() {
        RulePlaygroundService semSemgrep = service();
        semSemgrep.pathEnv = "/nada";
        assertThrows(RulePlaygroundService.UnavailableException.class, () -> semSemgrep.run(RULE, CODE, "java"));

        RulePlaygroundService off = service();
        off.enabled = false;
        assertThrows(RulePlaygroundService.UnavailableException.class, () -> off.run(RULE, CODE, "java"));

        RulePlaygroundService busy = service();
        busy.slots.drainPermits();
        assertThrows(RulePlaygroundService.BusyException.class, () -> busy.run(RULE, CODE, "java"));
    }

    @Test
    void liberaAVagaMesmoQuandoARegraEInvalida() {
        RulePlaygroundService s = service();
        for (int i = 0; i < 5; i++) {
            s.run("rules: []\n", CODE, "java");
        }
        assertEquals(2, s.slots.availablePermits());
    }
}
