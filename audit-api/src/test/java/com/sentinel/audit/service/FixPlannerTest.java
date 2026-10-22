package com.sentinel.audit.service;

import com.sentinel.audit.dto.FixPlanDto;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixPlannerTest {

    private final ScoringService scoring = new ScoringService();

    private static Finding finding(String rule, Severity sev, Category cat, String file) {
        Finding f = new Finding();
        f.setRuleId(rule);
        f.setSeverity(sev);
        f.setCategory(cat);
        f.setTitle("Problema " + rule);
        f.setFilePath(file);
        f.setLineNumber(10);
        return f;
    }

    @Test
    void semAchadosNaoHaNadaParaCorrigir() {
        FixPlanDto plan = FixPlanner.plan(List.of(), null, scoring);
        assertEquals(100.0, plan.scoreNow());
        assertEquals(0, plan.criticalCount());
        assertTrue(plan.items().isEmpty());
    }

    @Test
    void ganhoEhANotaRecalculadaSemAquelaRegra() {
        List<Finding> all = new ArrayList<>();
        all.add(finding("sql-injection", Severity.CRITICAL, Category.SECURITY, "A.java"));
        all.add(finding("hash-fraco", Severity.HIGH, Category.SECURITY, "B.java"));
        FixPlanDto plan = FixPlanner.plan(all, null, scoring);

        // segurança 100-20-10=70 (peso 0,30) e as demais dimensões em 100: nota 91
        assertEquals(91.0, plan.scoreNow());
        FixPlanDto.Item first = plan.items().get(0);
        assertEquals("sql-injection", first.ruleId());
        // sem o crítico: segurança 90 -> nota 97 (ganho 6,0); sem o alto: segurança 80 -> 94 (ganho 3,0)
        assertEquals(6.0, first.scoreGain());
        assertEquals(3.0, plan.items().get(1).scoreGain());
        assertEquals(97.0, plan.scoreIfCriticalFixed());
    }

    @Test
    void agrupaPorRegraEContaOcorrencias() {
        List<Finding> all = List.of(
                finding("sql-injection", Severity.CRITICAL, Category.SECURITY, "A.java"),
                finding("sql-injection", Severity.CRITICAL, Category.SECURITY, "B.java"));
        FixPlanDto plan = FixPlanner.plan(all, null, scoring);
        assertEquals(1, plan.items().size());
        assertEquals(2, plan.items().get(0).occurrences());
        assertEquals(2, plan.criticalCount());
    }

    @Test
    void ordenaPeloMaiorGanhoELimitaALista() {
        List<Finding> all = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            all.add(finding("regra-" + i, Severity.LOW, Category.QUALITY, "F" + i + ".java"));
        }
        all.add(finding("critica", Severity.CRITICAL, Category.SECURITY, "C.java"));
        FixPlanDto plan = FixPlanner.plan(all, null, scoring);
        assertEquals(FixPlanner.MAX_ITEMS, plan.items().size());
        assertEquals("critica", plan.items().get(0).ruleId());
        for (int i = 1; i < plan.items().size(); i++) {
            assertTrue(plan.items().get(i - 1).scoreGain() >= plan.items().get(i).scoreGain());
        }
    }
}
