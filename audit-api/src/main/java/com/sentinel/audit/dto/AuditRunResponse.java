package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.Severity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO de resposta representando uma execução de auditoria com seu resultado.
 *
 * @param progress Só vem preenchido enquanto a auditoria está em execução.
 * @param profile Perfil de regras do sistema (o efetivo, "default" quando não há).
 * @param totalFindings Achados ativos (sem os suprimidos).
 * @param newFindings Novos desde a auditoria anterior; nulo quando não há anterior ou não foi calculado.
 * @param incomplete Verdadeiro quando algum analisador falhou ou terminou parcialmente: a nota pode estar otimista.
 * @param incompleteReasons Quais analisadores comprometeram o resultado (vazio quando completo).
 * @param resolvedFindings Resolvidos desde a auditoria anterior; nulo quando não há anterior ou não foi calculado.
 */
public record AuditRunResponse(
        @Schema(description = "Identificador da auditoria.") Long id,
        @Schema(description = "Sistema auditado.") Long systemId,
        @Schema(description = "Nome do sistema auditado.") String systemName,
        @Schema(description = "Branch auditada.") String branch,
        @Schema(description = "Branch base, quando é auditoria de PR (só entram achados dos arquivos alterados em relação a ela).") String baseRef,
        @Schema(description = "Situação da auditoria.") AuditStatus status,
        @Schema(description = "Notas de risco, geral e por dimensão.") RiskScoreDto riskScore,
        @Schema(description = "Quantidade de achados por severidade.") Map<Severity, Long> findingsSummary,
        @Schema(description = "Quando a auditoria começou.") LocalDateTime startedAt,
        @Schema(description = "Quando terminou; ausente enquanto não termina.") LocalDateTime completedAt,
        @Schema(description = "Motivo da falha, quando a auditoria falhou.") String errorMessage,
        @Schema(description = "Andamento da auditoria; só vem preenchido enquanto ela está em execução.") AuditProgressDto progress,
        @Schema(description = "Quem ou o que pediu a auditoria.") String triggeredBy,
        @Schema(description = "Perfil de regras efetivo do sistema (default quando não há).") String profile,
        @Schema(description = "Commit auditado.") String commitSha,
        @Schema(description = "Linhas de código analisadas.") Long linesOfCode,
        @Schema(description = "Achados ativos (sem os suprimidos).") Long totalFindings,
        @Schema(description = "Achados novos desde a auditoria anterior; nulo quando não há anterior ou não foi calculado.") Integer newFindings,
        @Schema(description = "Achados resolvidos desde a auditoria anterior; nulo quando não há anterior ou não foi calculado.") Integer resolvedFindings,
        @Schema(description = "Auditoria anterior usada na comparação.") Long previousAuditId,
        @Schema(description = "Variação da nota geral em relação à auditoria anterior.") Double scoreDelta,
        @Schema(description = "Verdadeiro quando algum analisador falhou ou terminou parcialmente: a nota pode estar otimista.") boolean incomplete,
        @Schema(description = "Analisadores que comprometeram o resultado; vazio quando completo.") List<String> incompleteReasons) {
}
