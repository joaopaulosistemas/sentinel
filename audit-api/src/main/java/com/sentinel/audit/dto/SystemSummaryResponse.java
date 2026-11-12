package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.model.SourceType;

import com.sentinel.audit.model.Stack;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de resposta resumida de um sistema auditado, incluindo dados da última auditoria.
 *
 * @param scoreDelta Nota da última auditoria completa menos a anterior; nulo se houver menos de duas.
 * @param auditCount Quantas auditorias o sistema já tem, em qualquer situação (concluída, em execução ou com falha).
 * @param sourceType GIT_URL ou LOCAL_PATH.
 * @param localPath Pasta do projeto (relativa à raiz do servidor) quando a origem é LOCAL_PATH.
 * @param gitTokenSet Há token Git salvo? O token em si nunca é devolvido.
 * @param webhookSecretSet Há segredo de webhook salvo? O segredo nunca é devolvido.
 * @param disabledSides Lados ("back", "front") desligados na última auditoria concluída; lista vazia quando os dois
 *                      estavam ligados, quando não há auditoria concluída ou quando ela é anterior a este campo.
 */
public record SystemSummaryResponse(
        @Schema(description = "Identificador do sistema.") Long id,
        @Schema(description = "Nome do sistema.") String name,
        @Schema(description = "Tecnologia principal.") Stack stack,
        @Schema(description = "Equipe responsável.") String team,
        @Schema(description = "Nota geral da última auditoria concluída.") Double lastAuditScore,
        @Schema(description = "Data da última auditoria concluída.") LocalDateTime lastAuditDate,
        @Schema(description = "Achados críticos ativos na última auditoria.") Long openCritical,
        @Schema(description = "Achados de severidade alta ativos na última auditoria.") Long openHigh,
        @Schema(description = "Intervalo da auditoria automática, em horas; 0 ou ausente = desligada.") Integer auditIntervalHours,
        @Schema(description = "Branch da auditoria automática.") String scheduledBranch,
        @Schema(description = "Se audita a cada push na branch.") Boolean auditOnPush,
        @Schema(description = "Perfil de regras do sistema.") String profile,
        @Schema(description = "Nota da última auditoria completa menos a anterior; nulo se houver menos de duas.") Double scoreDelta,
        @Schema(description = "Quantas auditorias o sistema já tem, em qualquer situação.") Integer auditCount,
        @Schema(description = "Endereço do repositório Git.") String repoUrl,
        @Schema(description = "Texto livre sobre o sistema.") String description,
        @Schema(description = "GIT_URL ou LOCAL_PATH.") SourceType sourceType,
        @Schema(description = "Pasta do projeto (relativa à raiz do servidor) quando a origem é LOCAL_PATH.") String localPath,
        @Schema(description = "O que se audita na pasta local.") LocalSnapshot localSnapshot,
        @Schema(description = "Há token Git salvo? O token nunca é devolvido.") Boolean gitTokenSet,
        @Schema(description = "Hosts que recebem o token Git.") String gitTokenHosts,
        @Schema(description = "Há segredo de webhook salvo? O segredo nunca é devolvido.") Boolean webhookSecretSet,
        @Schema(description = "Commits do histórico baixados.") Integer gitHistoryDepth,
        @Schema(description = "Há URL de notificação salva?") Boolean notifyUrlSet,
        @Schema(description = "Formato da notificação (slack, teams ou generic).") String notifyFormat,
        @Schema(description = "Queda mínima de nota para notificar.") Double notifyMinScoreDrop,
        @Schema(description = "Severidade mínima para notificar.") String notifyMinSeverity,
        @Schema(description = "Endereço público do Sentinel usado nos links das notificações.") String notifyPublicUrl,
        @Schema(description = "Lados (back, front) desligados na última auditoria concluída; vazio quando os dois estavam ligados ou não há auditoria concluída.") List<String> disabledSides) {
}
