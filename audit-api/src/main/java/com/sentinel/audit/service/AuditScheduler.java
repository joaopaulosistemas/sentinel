package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dispara auditorias periódicas dos sistemas que têm agendamento e recupera auditorias que ficaram
 * "em andamento" por causa de um reinício da aplicação.
 * <p>
 * Limitação: o agendamento roda em memória e assume <b>uma única instância</b> da API. Com várias réplicas,
 * cada uma dispararia a auditoria; nesse caso desligue em todas menos uma ({@code SCHEDULER_ENABLED=false}).
 */
@ApplicationScoped
public class AuditScheduler {

    private static final Logger LOG = Logger.getLogger(AuditScheduler.class);

    @ConfigProperty(name = "sentinel.audit.scheduler.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    AuditedSystemRepository systemRepository;

    @Inject
    AuditRunRepository auditRunRepository;

    @Inject
    AuditRequestService requestService;

    /** O executor das auditorias vive na memória: o que estava rodando antes do start não vai terminar. */
    void onStart(@Observes StartupEvent event) {
        try {
            int recovered = recoverInterruptedRuns();
            if (recovered > 0) {
                LOG.warnf("%d auditoria(s) interrompida(s) por reinício foram marcadas como FAILED", recovered);
            }
        } catch (RuntimeException e) {
            LOG.warnf("Não foi possível recuperar auditorias interrompidas: %s", e.getMessage());
        }
    }

    @Transactional
    int recoverInterruptedRuns() {
        List<AuditRun> active = auditRunRepository.findActive();
        for (AuditRun run : active) {
            run.setStatus(AuditStatus.FAILED);
            run.setCompletedAt(LocalDateTime.now());
            run.setErrorMessage("Interrompida: a aplicação foi reiniciada durante a auditoria");
        }
        return active.size();
    }

    @Scheduled(every = "5m", delayed = "1m", identity = "sentinel-audit-scheduler",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if (enabled) {
            runDue(LocalDateTime.now());
        }
    }

    /** @return quantas auditorias foram disparadas */
    int runDue(LocalDateTime now) {
        int started = 0;
        for (AuditedSystem system : systemRepository.listAll()) {
            try {
                if (system.getAuditIntervalHours() == null || system.getAuditIntervalHours() <= 0) {
                    continue;
                }
                List<AuditRun> runs = auditRunRepository.findBySystemId(system.getId());   // mais recente primeiro
                LocalDateTime lastStart = runs.isEmpty() ? null : runs.get(0).getStartedAt();
                if (isDue(system.getAuditIntervalHours(), lastStart, now)
                        && requestService.requestIfIdle(system.getId(), system.effectiveScheduledBranch(), "scheduler").isPresent()) {
                    started++;
                    LOG.infof("Auditoria agendada disparada para o sistema %s", system.getName());
                }
            } catch (RuntimeException e) {
                LOG.warnf("Falha ao agendar auditoria do sistema %s: %s", system.getId(), e.getMessage());
            }
        }
        return started;
    }

    /** Venceu o intervalo desde a última auditoria (de qualquer status, para não insistir em loop numa falha)? */
    static boolean isDue(int intervalHours, LocalDateTime lastStart, LocalDateTime now) {
        return lastStart == null || !lastStart.plusHours(intervalHours).isAfter(now);
    }
}
