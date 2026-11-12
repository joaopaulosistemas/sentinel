package com.sentinel.audit.dto;

/**
 * Scores de risco por dimensão.
 */
public record RiskScoreDto(@Schema(description = "Nota geral.") Double overall, @Schema(description = "Nota de segurança.") Double security, @Schema(description = "Nota de qualidade.") Double quality, @Schema(description = "Nota de arquitetura.") Double architecture,
                           @Schema(description = "Nota de dependências.") Double dependencies, @Schema(description = "Nota de observabilidade.") Double observability, @Schema(description = "Nota de desempenho.") Double performance) {
}
