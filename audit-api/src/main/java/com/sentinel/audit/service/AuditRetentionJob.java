package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Apaga, uma vez por dia, as auditorias antigas (com seus achados) para o banco não crescer sem limite.
 * Desligada por padrão: {@code AUDIT_RETENTION_DAYS} acima de zero a liga. As {@code AUDIT_RETENTION_KEEP}
 * mais recentes de cada sistema ficam sempre. As supressões são do sistema e não são afetadas.
 */
@ApplicationScoped
public class AuditRetentionJob {

    private static final Logger LOG = Logger.getLogger(AuditRetentionJob.class);

    @ConfigProperty(name = "sentinel.audit.retention.days", defaultValue = "0")
    int days;

    @ConfigProperty(name = "sentinel.audit.retention.keep", defaultValue = "20")
    int keep;

    @Inject
    AuditedSystemRepository systemRepository;

    @Inject
    AuditRunRepository auditRunRepository;

    @Scheduled(cron = "{sentinel.audit.retention.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void purge() {
        if (days <= 0) {
            return;
        }
        int removed = 0;
        List<AuditedSystem> systems = systemRepository.listAll();
        // Uma consulta para todos os sistemas, agrupada aqui, em vez de uma consulta por sistema.
        Map<Long, List<AuditRun>> bySystem = auditRunRepository
                .findBySystemIds(systems.stream().map(AuditedSystem::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getSystem().getId()));
        LocalDateTime now = LocalDateTime.now();
        for (List<AuditRun> runs : bySystem.values()) {
            List<AuditRun> expired = AuditRetention.expired(runs, keep, days, now);
            expired.forEach(auditRunRepository::delete);
            removed += expired.size();
        }
        if (removed > 0) {
            LOG.infof("Retenção: %d auditoria(s) com mais de %d dias apagada(s)", removed, days);
        }
    }
}
