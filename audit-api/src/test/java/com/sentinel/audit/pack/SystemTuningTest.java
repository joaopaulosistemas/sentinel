package com.sentinel.audit.pack;

import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.tuning.TuningJson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SystemTuningTest {

    private static Finding f(String rule, Severity s) {
        Finding x = new Finding();
        x.setRuleId(rule);
        x.setSeverity(s);
        return x;
    }

    private static List<String> ids(AuditPolicy p) {
        return p.packs().stream().map(RulePack::id).toList();
    }

    @Test
    void semAjustesAPoliticaEAMesma() {
        PackRegistry reg = TestPacks.registry();
        AuditPolicy base = reg.policyFor("default");
        assertSame(base, base.withTuning(SystemTuning.EMPTY, reg.snapshot().packs()));
        assertSame(base, base.withTuning(null, reg.snapshot().packs()));
    }

    @Test
    void regressaoTodoPerfilComAjusteVazioDaMesmaPoliticaEOMesmoResultado() {
        PackRegistry reg = TestPacks.registry();
        List<String> perfis = new java.util.ArrayList<>(reg.snapshot().profiles().keySet());
        perfis.add("default");
        for (String id : perfis) {
            AuditPolicy a = reg.policyFor(id);
            AuditPolicy b = reg.policyFor(id, SystemTuning.EMPTY);
            assertEquals(ids(a), ids(b), id);
            assertEquals(a.baseOverrides(), b.baseOverrides(), id);
            assertEquals(a.thresholds(), b.thresholds(), id);
            List<Finding> in = List.of(f("sentinel-god-class", Severity.HIGH), f("x.y.z", Severity.LOW));
            List<Severity> sa = a.apply(copy(in)).stream().map(Finding::getSeverity).toList();
            List<Severity> sb = b.apply(copy(in)).stream().map(Finding::getSeverity).toList();
            assertEquals(sa, sb, id);
        }
    }

    private static List<Finding> copy(List<Finding> in) {
        return in.stream().map(x -> f(x.getRuleId(), x.getSeverity())).toList();
    }

    @Test
    void ajusteDoSistemaMudaSoOQueFoiAjustado() {
        PackRegistry reg = TestPacks.registry();
        AuditPolicy base = reg.policyFor("default");
        SystemTuning t = new SystemTuning(
                Map.of("regra-a", new RuleOverride(null, true, null), "regra-b", new RuleOverride(Severity.CRITICAL, false, null)),
                Map.of(), Set.of(), Set.of(), Set.of());
        AuditPolicy p = base.withTuning(t, reg.snapshot().packs());
        List<Finding> out = p.apply(List.of(f("regra-a", Severity.HIGH), f("regra-b", Severity.LOW), f("regra-c", Severity.LOW)));
        assertEquals(2, out.size(), "regra-a foi desligada");
        assertEquals(Severity.CRITICAL, out.get(0).getSeverity());
        assertEquals(Severity.LOW, out.get(1).getSeverity());
        assertEquals(ids(base), ids(p), "pacotes intactos");
        assertEquals(0, base.baseOverrides().keySet().stream().filter(k -> k.startsWith("regra-")).count(), "a política base não muda");
    }

    @Test
    void ajusteSubstituiASobrescritaDoPerfilNaMesmaChave() {
        AuditPolicy base = new AuditPolicy("p", List.of(), Map.of("regra-a", new RuleOverride(Severity.LOW, false, null)), Map.of());
        SystemTuning t = new SystemTuning(Map.of("REGRA-A", new RuleOverride(Severity.HIGH, false, null)), Map.of(), Set.of(), Set.of(), Set.of());
        AuditPolicy p = base.withTuning(t, List.of());
        assertEquals(Severity.HIGH, p.apply(List.of(f("regra-a", Severity.INFO))).get(0).getSeverity());
        assertEquals(Severity.LOW, base.apply(List.of(f("regra-a", Severity.INFO))).get(0).getSeverity());
    }

    @Test
    void ligaEDesligaPacotesEMesclaLimites() {
        PackRegistry reg = TestPacks.registry();
        AuditPolicy base = reg.policyFor("default");
        List<String> todos = reg.snapshot().packs().stream().map(RulePack::id).toList();
        assertFalse(base.packs().isEmpty());
        String ativo = ids(base).get(0);
        SystemTuning off = new SystemTuning(Map.of(), Map.of("max-function-lines", 40), Set.of(), Set.of(ativo), Set.of());
        AuditPolicy p = base.withTuning(off, reg.snapshot().packs());
        assertFalse(ids(p).contains(ativo));
        assertEquals(40, p.threshold("max-function-lines", 80));
        assertEquals(ids(base).size() - 1, ids(p).size());
        // ligar de volta um que o perfil não tinha
        String fora = todos.stream().filter(x -> !ids(base).contains(x)).findFirst().orElse(null);
        if (fora != null) {
            AuditPolicy q = base.withTuning(new SystemTuning(Map.of(), Map.of(), Set.of(fora), Set.of(), Set.of()), reg.snapshot().packs());
            assertTrue(ids(q).contains(fora));
        }
    }

    @Test
    void analisadorDesligadoIgnoraMaiusculas() {
        AuditPolicy base = new AuditPolicy("p", List.of(), Map.of(), Map.of());
        AuditPolicy p = base.withTuning(new SystemTuning(Map.of(), Map.of(), Set.of(), Set.of(), Set.of("PMD")), List.of());
        assertTrue(p.analyzerDisabled("pmd"));
        assertFalse(p.analyzerDisabled("ESLint"));
        assertFalse(base.analyzerDisabled("PMD"));
    }

    @Test
    void contaOsAjustes() {
        SystemTuning t = new SystemTuning(Map.of("a", new RuleOverride(null, true, null)), Map.of("max-nesting", 3),
                Set.of("p1"), Set.of("p2"), Set.of("PMD"));
        assertEquals(5, t.adjustments());
        assertFalse(t.isEmpty());
        assertTrue(SystemTuning.EMPTY.isEmpty());
    }

    @Test
    void jsonVoltaIgualEIgnoraAjusteSemEfeito() throws Exception {
        String json = "{\"rules\":[{\"id\":\"Regra-A\",\"severity\":\"high\",\"disabled\":false},{\"id\":\"vazia\"},"
                + "{\"id\":\"b.*\",\"disabled\":true,\"shadow\":true}],\"thresholds\":{\"max-nesting\":3},"
                + "\"enablePacks\":[\"p1\"],\"disablePacks\":[],\"disableAnalyzers\":[\"PMD\"]}";
        SystemTuning t = TuningJson.parse(new com.fasterxml.jackson.databind.ObjectMapper().readTree(json));
        assertEquals(2, t.rules().size());
        assertEquals(Severity.HIGH, t.rules().get("regra-a").severity());
        assertEquals(t, TuningJson.read(TuningJson.write(t)));
    }

    @Test
    void jsonRecusaEntradaInvalida() throws Exception {
        var m = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String bad : List.of(
                "{\"rules\":[{\"id\":\"a b\",\"disabled\":true}]}",
                "{\"rules\":[{\"id\":\"a\",\"severity\":\"ENORME\"}]}",
                "{\"thresholds\":{\"inventado\":3}}",
                "{\"thresholds\":{\"max-nesting\":0}}",
                "{\"thresholds\":{\"max-nesting\":\"3\"}}",
                "{\"enablePacks\":[\"p\"],\"disablePacks\":[\"p\"]}")) {
            assertThrows(IllegalArgumentException.class, () -> TuningJson.parse(m.readTree(bad)), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> TuningJson.parse(m.readTree("[]")));
        assertEquals(SystemTuning.EMPTY, TuningJson.read("  "));
        assertThrows(IllegalArgumentException.class, () -> TuningJson.read("{quebrado"));
    }
}
