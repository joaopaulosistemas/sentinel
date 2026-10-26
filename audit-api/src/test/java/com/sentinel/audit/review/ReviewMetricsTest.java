package com.sentinel.audit.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewMetricsTest {

    @Test
    void semDecisaoNaoHaTaxa() {
        ReviewMetrics.Result r = ReviewMetrics.compute(List.of(SuggestionStatus.OPEN), List.of(RuleStage.OBSERVING));
        assertNull(r.suggestionAdoption());
        assertNull(r.rulePromotion());
        assertEquals(1, r.suggestionsOpen());
        assertEquals(1, r.rulesInProgress());
    }

    @Test
    void taxasConsideramSoOQueUmaPessoaDecidiu() {
        ReviewMetrics.Result r = ReviewMetrics.compute(
                List.of(SuggestionStatus.ADDRESSED, SuggestionStatus.ADDRESSED, SuggestionStatus.ADDRESSED,
                        SuggestionStatus.DISMISSED, SuggestionStatus.OPEN),
                List.of(RuleStage.PROMOTED, RuleStage.DISCARDED, RuleStage.DISCARDED, RuleStage.PROPOSED));
        assertEquals(5, r.suggestionsTotal());
        assertEquals(0.75, r.suggestionAdoption(), 1e-9);
        assertEquals(4, r.rulesTotal());
        assertEquals(1, r.rulesPromoted());
        assertEquals(2, r.rulesDiscarded());
        assertEquals(1, r.rulesInProgress());
        assertEquals(1.0 / 3.0, r.rulePromotion(), 1e-9);
    }

    @Test
    void listasVaziasNaoQuebram() {
        ReviewMetrics.Result r = ReviewMetrics.compute(List.of(), List.of());
        assertEquals(0, r.suggestionsTotal());
        assertNull(r.suggestionAdoption());
    }
}
