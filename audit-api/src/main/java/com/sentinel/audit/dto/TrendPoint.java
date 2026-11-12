package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.sentinel.audit.model.Severity;

import java.time.LocalDateTime;
import java.util.Map;

/** Um ponto da linha do tempo de score de um sistema. */
public record TrendPoint(@Schema(description = "Auditoria do ponto.") Long auditId, @Schema(description = "Branch auditada.") String branch, @Schema(description = "Quando a auditoria terminou.") LocalDateTime completedAt,
                         @Schema(description = "Nota geral.") Double overall, @Schema(description = "Nota de segurança.") Double security, @Schema(description = "Nota de qualidade.") Double quality, @Schema(description = "Nota de arquitetura.") Double architecture,
                         @Schema(description = "Nota de dependências.") Double dependencies, @Schema(description = "Nota de observabilidade.") Double observability, @Schema(description = "Nota de desempenho.") Double performance,
                         @Schema(description = "Quantidade de achados por severidade.") Map<Severity, Long> findings) {}
