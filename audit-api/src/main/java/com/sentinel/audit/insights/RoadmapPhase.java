package com.sentinel.audit.insights;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

/** Uma fase do plano de ação; {@code items} traz os mais importantes e {@code total} quantos há na fase. */
public record RoadmapPhase(@Schema(description = "Número da fase.") int number, @Schema(description = "Nome.") String name, @Schema(description = "Objetivo.") String goal, @Schema(description = "Quantos itens há na fase.") int total, @Schema(description = "Os itens mais importantes da fase.") List<DebtItem> items) {
}
