package com.sentinel.audit.repository;

import com.sentinel.audit.model.FindingSuppression;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FindingSuppressionRepository implements PanacheRepository<FindingSuppression> {

    public List<FindingSuppression> findBySystemId(Long systemId) {
        return list("system.id = ?1 ORDER BY createdAt DESC", systemId);
    }

    public Optional<FindingSuppression> findBySystemAndFingerprint(Long systemId, String fingerprint) {
        return find("system.id = ?1 AND fingerprint = ?2", systemId, fingerprint).firstResultOptional();
    }

    /** Supressões ainda válidas (sem prazo ou com prazo no futuro). */
    public List<FindingSuppression> findActiveBySystemId(Long systemId, LocalDateTime now) {
        return list("system.id = ?1 AND (expiresAt IS NULL OR expiresAt > ?2)", systemId, now);
    }
}
