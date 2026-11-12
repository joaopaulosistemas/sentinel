package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

/**
 * Comparação de uma auditoria com a anterior (mesmo sistema e branch).
 *
 * @param hasBaseline  {@code false} na primeira auditoria: não há o que comparar
 */
public record AuditDiffResponse(@Schema(description = "Auditoria comparada.") Long auditId, @Schema(description = "Auditoria anterior usada como base.") Long baseAuditId, @Schema(description = "Falso na primeira auditoria: não há o que comparar.") boolean hasBaseline,
                                @Schema(description = "Nota geral desta auditoria.") Double overall, @Schema(description = "Nota geral da auditoria base.") Double baseOverall, @Schema(description = "Diferença entre as duas notas.") Double scoreDelta,
                                @Schema(description = "Achados que não existiam na auditoria base.") List<FindingResponse> newFindings, @Schema(description = "Achados da base que deixaram de existir.") List<FindingResponse> resolvedFindings,
                                @Schema(description = "Quantos achados continuam desde a base.") int persistingCount) {}
