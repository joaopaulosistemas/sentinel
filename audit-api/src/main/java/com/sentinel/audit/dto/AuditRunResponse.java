package com.sentinel.audit.dto;

import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.Severity;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO de resposta representando uma execução de auditoria com seu resultado.
 *
 * @param progress Só vem preenchido enquanto a auditoria está em execução.
 * @param profile Perfil de regras do sistema (o efetivo, "default" quando não há).
 * @param totalFindings Achados ativos (sem os suprimidos).
 * @param newFindings Novos desde a auditoria anterior; nulo quando não há anterior ou não foi calculado.
 * @param resolvedFindings Resolvidos desde a auditoria anterior; nulo quando não há anterior ou não foi calculado.
 */
public record AuditRunResponse(
        Long id,
        Long systemId,
        String systemName,
        String branch,
        String baseRef,
        AuditStatus status,
        RiskScoreDto riskScore,
        Map<Severity, Long> findingsSummary,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage,
        AuditProgressDto progress,
        String triggeredBy,
        String profile,
        String commitSha,
        Long linesOfCode,
        Long totalFindings,
        Integer newFindings,
        Integer resolvedFindings,
        Long previousAuditId,
        Double scoreDelta) {
}
