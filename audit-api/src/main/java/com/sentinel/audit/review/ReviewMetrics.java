package com.sentinel.audit.review;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

/**
 * Quanto o Claude acerta, medido pelo que as pessoas decidiram: sugestões tratadas ou dispensadas e regras
 * promovidas ou descartadas. Não entra na nota; serve para decidir se vale continuar confiando no Claude.
 */
public final class ReviewMetrics {

    private ReviewMetrics() {}

    public record Result(@Schema(description = "Sugestões feitas.") int suggestionsTotal, @Schema(description = "Sugestões abertas.") int suggestionsOpen, @Schema(description = "Sugestões tratadas.") int suggestionsAddressed, @Schema(description = "Sugestões dispensadas.") int suggestionsDismissed,
                         @Schema(description = "Tratadas dividido por tratadas mais dispensadas (0 a 1); nulo enquanto nenhuma foi decidida.") Double suggestionAdoption, @Schema(description = "Regras propostas.") int rulesTotal, @Schema(description = "Regras ainda no funil.") int rulesInProgress, @Schema(description = "Regras promovidas.") int rulesPromoted,
                         @Schema(description = "Regras descartadas.") int rulesDiscarded, @Schema(description = "Promovidas dividido por promovidas mais descartadas (0 a 1); nulo enquanto nenhuma foi decidida.") Double rulePromotion) {}

    public static Result compute(List<SuggestionStatus> suggestions, List<RuleStage> rules) {
        int open = 0;
        int addressed = 0;
        int dismissed = 0;
        for (SuggestionStatus s : suggestions) {
            switch (s) {
                case OPEN -> open++;
                case ADDRESSED -> addressed++;
                case DISMISSED -> dismissed++;
                default -> { }
            }
        }
        int promoted = 0;
        int discarded = 0;
        int progress = 0;
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
