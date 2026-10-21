package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.AnalyzerResult;
import com.sentinel.audit.analyzer.CodeAnalyzer;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Stack;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Quando uma ferramenta externa está disponível, ela substitui o analisador interno equivalente; senão, o interno segue. */
class AnalyzerSelectionTest {

    private static class Fake implements CodeAnalyzer {
        final String name;
        final boolean available;
        final List<Class<? extends CodeAnalyzer>> replaces;

        Fake(String name, boolean available, List<Class<? extends CodeAnalyzer>> replaces) {
            this.name = name;
            this.available = available;
            this.replaces = replaces;
        }

        public AnalyzerResult analyze(Path p, AuditRun r) { return null; }
        public String getName() { return name; }
        public boolean supports(Stack s) { return true; }
        @Override public boolean isAvailable() { return available; }
        @Override public List<Class<? extends CodeAnalyzer>> replaces() { return replaces; }
    }

    private static class Internal extends Fake {
        Internal(String n) { super(n, true, List.of()); }
    }

    private static class External extends Fake {
        External(boolean available) { super("externo", available, List.of(Internal.class)); }
    }

    private static List<String> names(List<CodeAnalyzer> list) {
        return list.stream().map(CodeAnalyzer::getName).toList();
    }

    @Test
    void ferramentaExternaDisponivelSubstituiOInternoEquivalente() {
        List<CodeAnalyzer> r = AuditOrchestrationService.selectAnalyzers(
                List.of(new Internal("interno"), new Fake("outro", true, List.of()), new External(true)));
        assertEquals(List.of("outro", "externo"), names(r));
    }

    @Test
    void semAFerramentaExternaOInternoContinuaRodando() {
        List<CodeAnalyzer> r = AuditOrchestrationService.selectAnalyzers(
                List.of(new Internal("interno"), new Fake("outro", true, List.of()), new External(false)));
        assertEquals(List.of("interno", "outro"), names(r));
    }

    @Test
    void indisponivelNuncaSubstituiNemRoda() {
        List<CodeAnalyzer> r = AuditOrchestrationService.selectAnalyzers(
                List.of(new Fake("fora", false, List.of(Internal.class)), new Internal("interno")));
        assertEquals(List.of("interno"), names(r));
    }
}
