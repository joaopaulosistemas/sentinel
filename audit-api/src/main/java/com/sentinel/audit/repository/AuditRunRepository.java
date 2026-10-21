package com.sentinel.audit.repository;

import com.sentinel.audit.model.AuditRun;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import com.sentinel.audit.model.AuditStatus;

/**
 * Repositório Panache para a entidade {@link AuditRun}.
 */
@ApplicationScoped
public class AuditRunRepository implements PanacheRepository<AuditRun> {

    /**
     * Busca todas as execuções de auditoria de um sistema específico,
     * ordenadas pela data de início decrescente.
     *
     * @param systemId identificador do sistema
     * @return lista de execuções do sistema
     */
    public List<AuditRun> findBySystemId(Long systemId) {
        return list("system.id = ?1 ORDER BY startedAt DESC", systemId);
    }

    /** Auditoria COMPLETA e CONCLUÍDA imediatamente anterior (mesmo sistema e branch; auditorias de PR nunca servem de base) à de id {@code beforeId}. */
    public AuditRun findPreviousCompleted(Long systemId, String branch, Long beforeId) {
        if (branch == null) {
            return find("system.id = ?1 AND branch IS NULL AND baseRef IS NULL AND status = ?2 AND id < ?3 ORDER BY id DESC",
                    systemId, AuditStatus.COMPLETED, beforeId).firstResult();
        }
        return find("system.id = ?1 AND branch = ?2 AND baseRef IS NULL AND status = ?3 AND id < ?4 ORDER BY id DESC",
                systemId, branch, AuditStatus.COMPLETED, beforeId).firstResult();
    }

    /** As últimas auditorias concluídas do sistema, da mais nova para a mais antiga. */
    public List<AuditRun> findLatestCompleted(Long systemId, int limit) {
        return find("system.id = ?1 AND baseRef IS NULL AND status = ?2 ORDER BY completedAt DESC", systemId, AuditStatus.COMPLETED)
                .page(0, limit).list();
    }

    /** Auditorias que ainda constam como PENDING ou RUNNING. */
    public List<AuditRun> findActive() {
        return list("status = ?1 OR status = ?2", AuditStatus.PENDING, AuditStatus.RUNNING);
    }
}
