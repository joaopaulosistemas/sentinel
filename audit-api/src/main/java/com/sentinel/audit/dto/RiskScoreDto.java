package com.sentinel.audit.dto;

/**
 * Scores de risco por dimensão.
 */
public record RiskScoreDto(Double overall, Double security, Double quality, Double architecture,
                           Double dependencies, Double observability, Double performance) {
}
