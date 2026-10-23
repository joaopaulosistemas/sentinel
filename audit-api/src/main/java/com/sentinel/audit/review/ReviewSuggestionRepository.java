package com.sentinel.audit.review;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ReviewSuggestionRepository implements PanacheRepository<ReviewSuggestion> {

    public List<ReviewSuggestion> forSystem(Long systemId) {
        return list("systemId = ?1 order by createdAt desc", systemId);
    }

    public long openCount(Long systemId) {
        return count("systemId = ?1 and status = ?2", systemId, SuggestionStatus.OPEN);
    }
}
