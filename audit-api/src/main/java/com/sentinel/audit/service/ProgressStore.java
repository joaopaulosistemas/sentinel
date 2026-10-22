package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditProgressSnapshot;
import com.sentinel.audit.model.AuditStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persistência do andamento das auditorias, compartilhada entre as réplicas da API. Cada gravação roda em
 * transação própria, para não depender da transação (ou da falha) de quem chamou.
 */
@ApplicationScoped
public class ProgressStore {

    @Inject
    EntityManager em;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void save(Long auditRunId, String payload) {
        AuditProgressSnapshot s = em.find(AuditProgressSnapshot.class, auditRunId);
        if (s == null) {
            s = new AuditProgressSnapshot();
            s.setAuditRunId(auditRunId);
            s.setPayload(payload);
            s.setUpdatedAt(LocalDateTime.now());
            em.persist(s);
        } else {
            s.setPayload(payload);
            s.setUpdatedAt(LocalDateTime.now());
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void delete(Long auditRunId) {
        em.createQuery("delete from AuditProgressSnapshot s where s.auditRunId = :id")
                .setParameter("id", auditRunId).executeUpdate();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<String> load(Long auditRunId) {
        AuditProgressSnapshot s = em.find(AuditProgressSnapshot.class, auditRunId);
        return s == null ? Optional.empty() : Optional.of(s.getPayload());
    }

    /** O usuário cancelou (em qualquer réplica) se o status da auditoria no banco já é CANCELLED. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean isCancelled(Long auditRunId) {
        return !em.createQuery("select r.id from AuditRun r where r.id = :id and r.status = :st", Long.class)
                .setParameter("id", auditRunId).setParameter("st", AuditStatus.CANCELLED)
                .setMaxResults(1).getResultList().isEmpty();
    }

    /** Auditorias cujo andamento foi atualizado depois de {@code since}: estão vivas em alguma réplica. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Set<Long> aliveSince(LocalDateTime since) {
        return em.createQuery("select s.auditRunId from AuditProgressSnapshot s where s.updatedAt >= :since", Long.class)
                .setParameter("since", since).getResultStream().collect(Collectors.toSet());
    }
}
