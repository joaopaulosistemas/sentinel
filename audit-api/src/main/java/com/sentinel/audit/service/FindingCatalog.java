package com.sentinel.audit.service;

import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.repository.FindingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Consultas de achados para a camada de recursos. */
@ApplicationScoped
public class FindingCatalog {

    @Inject
    FindingRepository repository;

    public List<Finding> byRun(Long auditRunId) {
        return repository.findByAuditRunId(auditRunId);
    }

    public Map<Long, List<Finding>> byRuns(Collection<Long> auditRunIds) {
        return repository.findByAuditRunIds(auditRunIds);
    }

    public Map<Severity, Long> countBySeverity(Long auditRunId) {
        return repository.countBySeverityForRun(auditRunId);
    }

    public Map<Long, Map<Severity, Long>> countBySeverityForRuns(Collection<Long> auditRunIds) {
        return repository.countBySeverityForRuns(auditRunIds);
    }

    /** Pares (regra, quantidade) da auditoria, como devolvidos pelo repositório. */
    public List<Object[]> ruleCounts(Long auditRunId) {
        return repository.ruleCountsForRun(auditRunId);
    }
}
