package com.sentinel.audit.review;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class RuleProposalRepository implements PanacheRepository<RuleProposal> {

    public List<RuleProposal> forSystem(Long systemId) {
        return list("systemId = ?1 order by createdAt desc", systemId);
    }

    public RuleProposal bySlug(Long systemId, String slug) {
        return find("systemId = ?1 and slug = ?2", systemId, slug).firstResult();
    }

    public List<RuleProposal> running(Long systemId) {
        return list("systemId = ?1 and stage in (?2, ?3)", systemId, RuleStage.OBSERVING, RuleStage.PROMOTED);
    }

    public long activeCount(Long systemId) {
        return count("systemId = ?1 and stage not in (?2, ?3)", systemId, RuleStage.PROMOTED, RuleStage.DISCARDED);
    }

    /** Cópias desta regra, em outros sistemas, que já foram promovidas. */
    public long promotedCopies(Long originId) {
        return count("copiedFromId = ?1 and stage = ?2", originId, RuleStage.PROMOTED);
    }
}
