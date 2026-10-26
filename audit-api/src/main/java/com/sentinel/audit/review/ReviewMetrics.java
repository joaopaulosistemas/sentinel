package com.sentinel.audit.review;

import java.util.List;

/**
 * Quanto o Claude acerta, medido pelo que as pessoas decidiram: sugestões tratadas ou dispensadas e regras
 * promovidas ou descartadas. Não entra na nota; serve para decidir se vale continuar confiando no Claude.
 */
public final class ReviewMetrics {

    private ReviewMetrics() {}

    public record Result(int suggestionsTotal, int suggestionsOpen, int suggestionsAddressed, int suggestionsDismissed,
                         Double suggestionAdoption, int rulesTotal, int rulesInProgress, int rulesPromoted,
                         int rulesDiscarded, Double rulePromotion) {}

    public static Result compute(List<SuggestionStatus> suggestions, List<RuleStage> rules) {
        int open = 0, addressed = 0, dismissed = 0;
        for (SuggestionStatus s : suggestions) {
            switch (s) {
                case OPEN -> open++;
                case ADDRESSED -> addressed++;
                case DISMISSED -> dismissed++;
            }
        }
        int promoted = 0, discarded = 0, progress = 0;
        for (RuleStage r : rules) {
            if (r == RuleStage.PROMOTED) {
                promoted++;
            } else if (r == RuleStage.DISCARDED) {
                discarded++;
            } else {
                progress++;
            }
        }
        return new Result(suggestions.size(), open, addressed, dismissed, ratio(addressed, addressed + dismissed),
                rules.size(), progress, promoted, discarded, ratio(promoted, promoted + discarded));
    }

    /** Proporção entre 0 e 1, ou {@code null} quando ainda não há decisão para medir. */
    static Double ratio(int part, int decided) {
        return decided == 0 ? null : (double) part / decided;
    }
}
