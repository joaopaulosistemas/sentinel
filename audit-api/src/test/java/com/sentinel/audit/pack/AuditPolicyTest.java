package com.sentinel.audit.pack;

import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditPolicyTest {

    private static Finding f(String rule, Severity s) {
        Finding x = new Finding();
        x.setRuleId(rule);
        x.setSeverity(s);
        return x;
    }

    private static AuditPolicy policy(Map<String, RuleOverride> o) {
        return new AuditPolicy("t", List.of(), o, Map.of());
    }

    @Test
    void semSobrescritaNadaMuda() {
        List<Finding> in = List.of(f("a", Severity.LOW));
        List<Finding> out = policy(Map.of()).apply(in);
        assertEquals(1, out.size());
        assertEquals(Severity.LOW, out.get(0).getSeverity());
        assertNull(out.get(0).getShadow());
    }

    @Test
    void desligaTrocaSeveridadeEMarcaSombra() {
        Map<String, RuleOverride> o = new LinkedHashMap<>();
        o.put("off", new RuleOverride(null, true, null));
        o.put("sev", new RuleOverride(Severity.CRITICAL, false, null));
        o.put("sombra", new RuleOverride(null, false, true));

        List<Finding> out = policy(o).apply(List.of(f("off", Severity.HIGH), f("sev", Severity.LOW),
                f("sombra", Severity.MEDIUM), f("outra", Severity.MEDIUM)));

        assertEquals(3, out.size());
        assertEquals(Severity.CRITICAL, out.get(0).getSeverity());
        assertEquals(Boolean.TRUE, out.get(1).getShadow());
        assertEquals(Severity.MEDIUM, out.get(1).getSeverity(), "só sombra: severidade intacta");
        assertNull(out.get(2).getShadow());
    }

    @Test
    void idDoSemgrepComCaminhoCasaPeloUltimoTrecho() {
        AuditPolicy p = policy(Map.of("sentinel-god-class", new RuleOverride(Severity.LOW, false, null)));
        assertNotNull(p.overrideFor("work.audit-rules.packs.core-quality.rules.sentinel-god-class"));
        assertNotNull(p.overrideFor("SENTINEL-GOD-CLASS"));
        assertNull(p.overrideFor("sentinel-god-class-2"), "não casa por prefixo");
        assertNull(p.overrideFor(null));
        assertNull(p.overrideFor(" "));
    }

    @Test
    void globCasaECurvaExataVenceGlob() {
        Map<String, RuleOverride> o = new LinkedHashMap<>();
        o.put("sentinel-ts-*", new RuleOverride(null, true, null));
        o.put("sentinel-ts-any", new RuleOverride(Severity.HIGH, false, null));
        AuditPolicy p = policy(o);

        assertTrue(p.overrideFor("sentinel-ts-foo").disabled());
        assertFalse(p.overrideFor("sentinel-ts-any").disabled(), "chave exata vale mais");
        assertEquals(Severity.HIGH, p.overrideFor("sentinel-ts-any").severity());
        assertNull(p.overrideFor("sentinel-java-foo"));
    }

    @Test
    void globNaoTrataPontoNemColchetesComoRegex() {
        AuditPolicy p = policy(Map.of("a.b*", new RuleOverride(null, true, null)));
        assertNotNull(p.overrideFor("a.bcd"));
        assertNull(p.overrideFor("axbcd"), "ponto é literal");
    }

    @Test
    void promoveRegraExperimentalComShadowFalse() {
        Finding exp = f("exp", Severity.MEDIUM);
        exp.setShadow(true);                    // veio do metadata.status: experimental
        List<Finding> out = policy(Map.of("exp", new RuleOverride(null, false, false))).apply(List.of(exp));
        assertEquals(Boolean.FALSE, out.get(0).getShadow());
    }
}
