package com.sentinel.audit.service;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.SuppressionReason;
import com.sentinel.audit.service.RuleQualityService.RuleQuality;
import com.sentinel.audit.service.RuleQualityService.Snapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleQualityServiceTest {

    private static int seq = 0;

    private static Finding f(String rule, String tool, Category cat) {
        Finding f = new Finding();
        f.setRuleId(rule);
        f.setToolSource(tool);
        f.setCategory(cat);
        f.setFingerprint("fp" + (seq++));
        return f;
    }

    /** n findings da regra, dos quais fp marcados como falso positivo e ar como risco aceito. */
    private static Snapshot snap(String rule, int n, int fp, int ar) {
        List<Finding> fs = new ArrayList<>();
        Map<String, SuppressionReason> reasons = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Finding x = f(rule, "Semgrep", Category.SECURITY);
            fs.add(x);
            if (i < fp) {
                reasons.put(x.getFingerprint(), SuppressionReason.FALSE_POSITIVE);
            } else if (i < fp + ar) {
                reasons.put(x.getFingerprint(), SuppressionReason.ACCEPTED_RISK);
            }
        }
        return new Snapshot(fs, reasons);
    }

    private static RuleQuality find(List<RuleQuality> list, String rule) {
        return list.stream().filter(r -> r.ruleId().equals(rule)).findFirst().orElseThrow();
    }

    @Test
    void calculaTaxaDeFalsoPositivoPorRegra() {
        List<RuleQuality> r = RuleQualityService.compute(List.of(snap("regra-a", 10, 4, 2), snap("regra-b", 10, 0, 0)));

        RuleQuality a = find(r, "regra-a");
        assertEquals(10, a.total());
        assertEquals(4, a.falsePositive());
        assertEquals(2, a.acceptedRisk());
        assertEquals(0.4, a.falsePositiveRate(), 1e-9);
        assertTrue(a.needsReview());                 // 40% com amostra suficiente
        assertFalse(find(r, "regra-b").needsReview());
        assertEquals("regra-a", r.get(0).ruleId()); // quem pede revisão vem primeiro
    }

    @Test
    void poucaAmostraNaoMarcaRevisaoEFalsoPositivoNaoRiscoAceito() {
        List<RuleQuality> r = RuleQualityService.compute(List.of(
                snap("poucos", 3, 3, 0),             // 100%, mas só 3 casos
                snap("aceito", 10, 0, 10)));         // risco aceito não é falha da regra
        assertFalse(find(r, "poucos").needsReview());
        assertFalse(find(r, "aceito").needsReview());
        assertEquals(0.0, find(r, "aceito").falsePositiveRate(), 1e-9);
    }

    @Test
    void somaAmostrasDeVariosSistemas() {
        List<RuleQuality> r = RuleQualityService.compute(List.of(snap("regra", 3, 2, 0), snap("regra", 3, 1, 0)));
        RuleQuality q = find(r, "regra");
        assertEquals(6, q.total());
        assertEquals(3, q.falsePositive());
        assertTrue(q.needsReview());                 // 50% de 6
    }

    @Test
    void ignoraDependenciasERegrasSemIdENormalizaOCaminhoDoSemgrep() {
        Finding dep = f("CVE-2021-44228", "Trivy", Category.DEPENDENCY);
        Finding semId = f(null, "Semgrep", Category.SECURITY);
        Finding docker = f("audit-rules.rules.iac.sentinel-docker-sem-user", "Semgrep", Category.SECURITY);
        Finding dockerOutroAmbiente = f("app.audit-rules.rules.iac.sentinel-docker-sem-user", "Semgrep", Category.SECURITY);
        Finding trivy = f("trivy-AVD-DS-0002", "Trivy", Category.SECURITY);

        List<RuleQuality> r = RuleQualityService.compute(List.of(
                new Snapshot(List.of(dep, semId, docker, dockerOutroAmbiente, trivy), Map.of())));

        assertEquals(2, r.size());
        assertEquals(2, find(r, "sentinel-docker-sem-user").total());          // mesma regra, ambientes diferentes
        assertEquals("trivy-AVD-DS-0002", find(r, "trivy-AVD-DS-0002").ruleId());   // fora do Semgrep não mexe no id
    }

    @Test
    void auditoriaAntigaSemFingerprintNaoQuebra() {
        Finding velho = f("regra", "Semgrep", Category.SECURITY);
        velho.setFingerprint(null);
        List<RuleQuality> r = RuleQualityService.compute(List.of(new Snapshot(List.of(velho), Map.of("x", SuppressionReason.FALSE_POSITIVE))));
        assertEquals(1, find(r, "regra").total());
        assertEquals(0, find(r, "regra").falsePositive());
        assertTrue(RuleQualityService.compute(List.of()).isEmpty());
    }
}
