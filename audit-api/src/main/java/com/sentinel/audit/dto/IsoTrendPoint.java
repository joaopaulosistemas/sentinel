package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/** Um ponto da evolução das características ISO/IEC 25010 de um sistema (perfil padrão). */
public record IsoTrendPoint(@Schema(description = "Auditoria do ponto.") Long auditId,
                            @Schema(description = "Branch auditada.") String branch,
                            @Schema(description = "Quando a auditoria terminou.") LocalDateTime completedAt,
                            @Schema(description = "Nota ponderada (perfil padrão).") Double notaPonderada,
                            @Schema(description = "Nota por característica avaliada (id -> nota).") Map<String, Double> notas) {}
