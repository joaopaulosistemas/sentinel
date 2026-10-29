package com.sentinel.audit.dto;

import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.model.SourceType;

import com.sentinel.audit.model.Stack;
import java.time.LocalDateTime;

/**
 * DTO de resposta resumida de um sistema auditado, incluindo dados da última auditoria.
 *
 * @param scoreDelta Nota da última auditoria completa menos a anterior; nulo se houver menos de duas.
 * @param auditCount Quantas auditorias o sistema já tem, em qualquer situação (concluída, em execução ou com falha).
 * @param sourceType GIT_URL ou LOCAL_PATH.
 * @param localPath Pasta do projeto (relativa à raiz do servidor) quando a origem é LOCAL_PATH.
 * @param gitTokenSet Há token Git salvo? O token em si nunca é devolvido.
 * @param webhookSecretSet Há segredo de webhook salvo? O segredo nunca é devolvido.
 */
public record SystemSummaryResponse(
        Long id,
        String name,
        Stack stack,
        String team,
        Double lastAuditScore,
        LocalDateTime lastAuditDate,
        Long openCritical,
        Long openHigh,
        Integer auditIntervalHours,
        String scheduledBranch,
        Boolean auditOnPush,
        String profile,
        Double scoreDelta,
        Integer auditCount,
        String repoUrl,
        String description,
        SourceType sourceType,
        String localPath,
        LocalSnapshot localSnapshot,
        Boolean gitTokenSet,
        String gitTokenHosts,
        Boolean webhookSecretSet,
        Integer gitHistoryDepth,
        Boolean notifyUrlSet,
        String notifyFormat,
        Double notifyMinScoreDrop,
        String notifyMinSeverity,
        String notifyPublicUrl) {
}
