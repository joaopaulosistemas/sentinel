package com.sentinel.audit.review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleFunnelTest {

    private static final RuleFunnel.Evidence GOOD = new RuleFunnel.Evidence(20, 1, 2);

    @Test
    void ordemDoFunilNaoPermitePularEtapa() {
        assertTrue(RuleFunnel.review(RuleStage.PROPOSED).ok());
        assertFalse(RuleFunnel.review(RuleStage.REVIEWED).ok());
        assertFalse(RuleFunnel.sampleTest(RuleStage.PROPOSED).ok(), "sem revisão humana não há teste");
        assertTrue(RuleFunnel.sampleTest(RuleStage.REVIEWED).ok());
        assertFalse(RuleFunnel.startObservation(RuleStage.REVIEWED).ok(), "sem teste de amostra não observa");
        assertTrue(RuleFunnel.startObservation(RuleStage.SAMPLE_PASSED).ok());
        assertFalse(RuleFunnel.promote(RuleStage.SAMPLE_PASSED, GOOD, 0.9, 5).ok(), "sem observação não promove");
    }

    @Test
    void promoverExigeCicloAchadosSuficientesEPrecisao() {
        assertTrue(RuleFunnel.promote(RuleStage.OBSERVING, GOOD, 0.9, 5).ok());
        assertFalse(RuleFunnel.promote(RuleStage.OBSERVING, new RuleFunnel.Evidence(20, 1, 0), 0.9, 5).ok(), "nenhuma auditoria depois do início");
        assertFalse(RuleFunnel.promote(RuleStage.OBSERVING, new RuleFunnel.Evidence(3, 0, 2), 0.9, 5).ok(), "poucos achados");
        RuleFunnel.Verdict v = RuleFunnel.promote(RuleStage.OBSERVING, new RuleFunnel.Evidence(10, 2, 2), 0.9, 5);
        assertFalse(v.ok());
        assertTrue(v.reason().contains("80%"), v.reason());
    }

    @Test
    void precisaoNoLimiteAprovaEMinimoAjustavel() {
        assertTrue(RuleFunnel.promote(RuleStage.OBSERVING, new RuleFunnel.Evidence(10, 1, 1), 0.9, 5).ok(), "90% exatos");
        assertFalse(RuleFunnel.promote(RuleStage.OBSERVING, new RuleFunnel.Evidence(10, 1, 1), 0.95, 5).ok());
        assertTrue(RuleFunnel.promote(RuleStage.OBSERVING, new RuleFunnel.Evidence(10, 3, 1), 0.7, 5).ok());
        assertEquals(0.0, new RuleFunnel.Evidence(0, 0, 1).precision());
    }

    @Test
    void descartarVaiDeQualquerEtapaAbertaMasNaoDeEncerrada() {
        for (RuleStage s : RuleStage.values()) {
            assertEquals(!s.terminal(), RuleFunnel.discard(s).ok(), s.name());
        }
    }

    @Test
    void soObservandoOuPromovidaRodaNoMotor() {
        for (RuleStage s : RuleStage.values()) {
            assertEquals(s == RuleStage.OBSERVING || s == RuleStage.PROMOTED, s.runs(), s.name());
        }
    }

    @Test
    void compartilharExigeCopiaPromovidaEmOutroSistema() {
        assertFalse(RuleFunnel.sharedEligible(RuleStage.PROMOTED, 0));
        assertTrue(RuleFunnel.sharedEligible(RuleStage.PROMOTED, 1));
        assertFalse(RuleFunnel.sharedEligible(RuleStage.OBSERVING, 3));
    }
}
