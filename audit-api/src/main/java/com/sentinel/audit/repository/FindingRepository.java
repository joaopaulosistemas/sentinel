package com.sentinel.audit.repository;

import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Repositório Panache para a entidade {@link Finding}.
 * Provê consultas especializadas por severidade e por execução de auditoria.
 */
@ApplicationScoped
public class FindingRepository implements PanacheRepository<Finding> {

    /**
     * Busca findings de uma execução filtrando por severidade.
     *
     * @param auditRunId identificador da execução
     * @param severity   severidade desejada
     * @return lista de findings com a severidade informada
     */
    public List<Finding> findByAuditRunAndSeverity(Long auditRunId, Severity severity) {
        return list("auditRun.id = ?1 AND severity = ?2", auditRunId, severity);
    }

    /**
     * Conta os findings de uma execução agrupados por severidade.
     *
     * @param auditRunId identificador da execução
     * @return mapa de severidade para quantidade de findings
     */
    public Map<Severity, Long> countBySeverityForRun(Long auditRunId) {
        Map<Severity, Long> result = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            result.put(severity, 0L);
        }
        // Uma única consulta agrupada, em vez de uma contagem por severidade.
        List<Object[]> rows = getEntityManager()
                .createQuery("select f.severity, count(f) from Finding f where f.auditRun.id = :id and f.status <> :suppressed group by f.severity",
                        Object[].class)
                .setParameter("id", auditRunId)
                .setParameter("suppressed", com.sentinel.audit.model.FindingStatus.SUPPRESSED)
                .getResultList();
        for (Object[] row : rows) {
            result.put((Severity) row[0], (Long) row[1]);
        }
        return result;
    }

    /**
     * Busca todos os findings de uma execução de auditoria.
     *
     * @param auditRunId identificador da execução
     * @return lista de findings da execução
     */
    public List<Finding> findByAuditRunId(Long auditRunId) {
        return list("auditRun.id = ?1", auditRunId);
    }
}
