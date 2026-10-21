package com.sentinel.audit.dto;

import java.util.List;

/**
 * Comparação de uma auditoria com a anterior (mesmo sistema e branch).
 *
 * @param hasBaseline  {@code false} na primeira auditoria: não há o que comparar
 */
public record AuditDiffResponse(Long auditId, Long baseAuditId, boolean hasBaseline,
                                Double overall, Double baseOverall, Double scoreDelta,
                                List<FindingResponse> newFindings, List<FindingResponse> resolvedFindings,
                                int persistingCount) {}
