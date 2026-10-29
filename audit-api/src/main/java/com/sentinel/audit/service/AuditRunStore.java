package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.RiskScore;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.FindingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * O que a auditoria lê e grava no banco ao longo do fluxo: marcar como rodando, cancelada ou falha, gravar os
 * achados e o resultado, guardar SBOM e contexto, carregar a base de comparação e avisar o fim. Cada método
 * transacional fica aqui para o orquestrador cuidar só da ordem das etapas.
 */
@ApplicationScoped
public class AuditRunStore {

    private static final Logger LOG = Logger.getLogger(AuditRunStore.class);

    /** Tempo máximo de espera pelo commit do registro da auditoria. */
    private static final Duration RUN_VISIBILITY_TIMEOUT = Duration.ofSeconds(15);
    private static final long RUN_VISIBILITY_POLL_MILLIS = 200L;


    @Inject
    SecretBox secretBox;

    @Inject
    FindingRepository findingRepository;

    @Inject
    AuditRunRepository auditRunRepository;

    @Inject
    RuleQualityService ruleQualityService;

    @Inject
    FindingLifecycleService lifecycle;

    @Inject
    NotificationService notifications;

    @Transactional
    boolean markCancelledByUser(Long auditRunId) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null || (run.getStatus() != AuditStatus.PENDING && run.getStatus() != AuditStatus.RUNNING)) {
            return false;
        }
        run.setStatus(AuditStatus.CANCELLED);
        run.setCompletedAt(LocalDateTime.now());
        run.setErrorMessage("Cancelada pelo usuário");
        return true;
    }

    /** Garante o estado final quando a parada veio do pedido do usuário; nunca lança exceção. */
    void markCancelled(Long auditRunId) {
        try {
            markCancelledByUser(auditRunId);
        } catch (Exception e) {
            LOG.errorf(e, "Não foi possível marcar a auditoria %d como CANCELLED", auditRunId);
        }
    }

    @Transactional
    void storeContext(Long auditRunId, String json) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run != null) {
            run.setContextJson(json);
        }
    }

    @Transactional
    void storeSbom(Long auditRunId, String sbom) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run != null) {
            run.setSbomJson(sbom);
        }
    }

    /** O aviso é secundário: nada que dê errado nele pode mudar o resultado da auditoria. */
    void notifySafely(Long auditRunId) {
        try {
            notifications.notifyFinished(auditRunId);
        } catch (RuntimeException e) {
            LOG.warnf("Falha ao notificar a auditoria %d: %s", auditRunId, e.getMessage());
        }
    }

    /** Sem a qualidade das regras a confiança ainda sai (pelos demais sinais): nada aqui pode derrubar a auditoria. */
    java.util.Map<String, RuleQualityService.RuleQuality> loadRuleQualitySafely() {
        try {
            return ruleQualityService.byRule();
        } catch (RuntimeException e) {
            LOG.warnf("Não foi possível carregar a qualidade das regras: %s", e.getMessage());
            return java.util.Map.of();
        }
    }

    /** Falha ao buscar a base de comparação não pode derrubar a auditoria: segue sem "novos" e sem supressões. */
    FindingLifecycleService.Baseline loadBaselineSafely(Long auditRunId) {
        try {
            return lifecycle.loadBaseline(auditRunId);
        } catch (RuntimeException e) {
            LOG.warnf("Não foi possível carregar a base de comparação da auditoria %d: %s", auditRunId, e.getMessage());
            return FindingLifecycleService.Baseline.empty();
        }
    }

    /**
     * Marca a auditoria como RUNNING e devolve os dados necessários para o restante do fluxo.
     * <p>
     * O disparo da auditoria ocorre de dentro da transação que cria o registro; por isso, esta
     * thread pode rodar antes do commit. Aguarda (com limite de tempo) o registro ficar visível.
     */
    AuditOrchestrationService.AuditContext markRunning(Long auditRunId) {
        long deadline = System.nanoTime() + RUN_VISIBILITY_TIMEOUT.toNanos();
        while (true) {
            AuditOrchestrationService.AuditContext context = tryMarkRunning(auditRunId);
            if (context != null) {
                return context;
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(
                        "Auditoria " + auditRunId + " não encontrada no banco após "
                                + RUN_VISIBILITY_TIMEOUT.toSeconds() + "s");
            }
            try {
                Thread.sleep(RUN_VISIBILITY_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrompido aguardando a auditoria " + auditRunId, e);
            }
        }
    }

    @Transactional
    AuditOrchestrationService.AuditContext tryMarkRunning(Long auditRunId) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null) {
            return null;
        }
        if (run.getStatus() == AuditStatus.CANCELLED) {
            throw new AuditCancelledException(auditRunId);
        }
        run.setStatus(AuditStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        // Leituras do sistema (lazy) acontecem aqui, dentro da transação.
        return new AuditOrchestrationService.AuditContext(
                run.getSystem().getRepoUrl(),
                run.getBranch(),
                run.getSystem().getStack(),
                run.getSystem().getName(),
                run.getSystem().effectiveProfile(),
                run.getBaseRef(),
                secretBox.decrypt(run.getSystem().getGitTokenEnc()),
                run.getSystem().getGitTokenHosts(),
                run.getSystem().getGitHistoryDepth(),
                run.getSystem().getId(),
                run.getSystem().effectiveSourceType(),
                run.getSystem().getLocalPath(),
                run.getSystem().effectiveLocalSnapshot());
    }

    @Transactional
    void persistResultsAndComplete(Long auditRunId, List<Finding> findings, RiskScore riskScore, long linesOfCode, String commitSha) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null) {
            throw new IllegalStateException("Auditoria " + auditRunId + " não encontrada ao persistir resultados");
        }
        if (run.getStatus() == AuditStatus.CANCELLED) {
            // O usuário cancelou no último instante: nada do que foi analisado deve ser gravado.
            throw new AuditCancelledException(auditRunId);
        }

        for (Finding finding : findings) {
            RunText.fitColumns(finding);
            finding.setAuditRun(run);
            findingRepository.persist(finding);
        }

        run.setRiskScore(riskScore);
        run.setLinesOfCode(linesOfCode);
        run.setCommitSha(commitSha);
        run.setStatus(AuditStatus.COMPLETED);
        run.setCompletedAt(LocalDateTime.now());
    }

    /**
     * Marca a auditoria como FAILED. Nunca lança exceção: já estamos tratando uma falha
     * e não podemos mascará-la com outra.
     */
    void markFailed(Long auditRunId, Throwable cause) {
        try {
            updateStatusFailed(auditRunId, RunText.describe(cause));
        } catch (Exception e) {
            LOG.errorf(e, "Não foi possível marcar a auditoria %d como FAILED", auditRunId);
        }
    }

    @Transactional
    void updateStatusFailed(Long auditRunId, String reason) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run != null) {
            run.setStatus(AuditStatus.FAILED);
            run.setCompletedAt(LocalDateTime.now());
            run.setErrorMessage(RunText.error(reason));
        }
    }
}
