package com.sentinel.audit.actions;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PendingActionRepository implements PanacheRepository<PendingAction> {

    public List<PendingAction> recent(Long systemId, int limit) {
        return find("systemId = ?1 order by createdAt desc", systemId).page(0, limit).list();
    }

    public long pendingCount(Long systemId) {
        return count("systemId = ?1 and status = ?2", systemId, PendingStatus.PENDING);
    }

    public boolean hasPending(Long systemId, PendingKind kind, String payload) {
        return count("systemId = ?1 and kind = ?2 and status = ?3 and payload = ?4",
                systemId, kind, PendingStatus.PENDING, payload) > 0;
    }
}
