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
     * {@link #countBySeverityForRun} para várias execuções de uma vez (uma consulta em vez de uma por execução).
     * Toda execução pedida aparece no resultado, com zero nas severidades sem findings.
     */
    public Map<Long, Map<Severity, Long>> countBySeverityForRuns(java.util.Collection<Long> auditRunIds) {
        Map<Long, Map<Severity, Long>> result = new java.util.LinkedHashMap<>();
        for (Long id : auditRunIds) {
            Map<Severity, Long> zero = new EnumMap<>(Severity.class);
            for (Severity severity : Severity.values()) {
                zero.put(severity, 0L);
            }
            result.put(id, zero);
        }
        if (auditRunIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = getEntityManager()
                .createQuery("select f.auditRun.id, f.severity, count(f) from Finding f where f.auditRun.id in :ids and f.status <> :suppressed group by f.auditRun.id, f.severity",
                        Object[].class)
                .setParameter("ids", auditRunIds)
                .setParameter("suppressed", com.sentinel.audit.model.FindingStatus.SUPPRESSED)
                .getResultList();
        for (Object[] row : rows) {
            result.get((Long) row[0]).put((Severity) row[1], (Long) row[2]);
        }
        return result;
    }

    /** Os findings de várias execuções de uma vez, agrupados pelo id da execução (uma consulta). */
    public Map<Long, List<Finding>> findByAuditRunIds(java.util.Collection<Long> auditRunIds) {
        Map<Long, List<Finding>> result = new java.util.HashMap<>();
        if (auditRunIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = getEntityManager()
                .createQuery("select f.auditRun.id, f from Finding f where f.auditRun.id in :ids", Object[].class)
                .setParameter("ids", auditRunIds)
                .getResultList();
        for (Object[] row : rows) {
            result.computeIfAbsent((Long) row[0], k -> new java.util.ArrayList<>()).add((Finding) row[1]);
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

    /** Regras que apareceram numa execução, com quantas vezes: base para a tela de ajustes saber o que listar. */
    public List<Object[]> ruleCountsForRun(Long auditRunId) {
        return getEntityManager()
                .createQuery("select f.ruleId, count(f) from Finding f where f.auditRun.id = :id and f.ruleId is not null group by f.ruleId order by count(f) desc",
                        Object[].class)
                .setParameter("id", auditRunId)
                .setMaxResults(300)
                .getResultList();
    }
}
