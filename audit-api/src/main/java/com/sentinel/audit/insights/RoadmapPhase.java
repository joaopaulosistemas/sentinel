package com.sentinel.audit.insights;

import java.util.List;

/** Uma fase do plano de ação; {@code items} traz os mais importantes e {@code total} quantos há na fase. */
public record RoadmapPhase(int number, String name, String goal, int total, List<DebtItem> items) {
}
