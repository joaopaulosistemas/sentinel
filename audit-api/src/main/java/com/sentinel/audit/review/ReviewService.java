package com.sentinel.audit.review;

import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.SuppressionReason;
import com.sentinel.audit.repository.AuditedSystemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Revisão do Claude: sugestões e regras propostas. Nada aqui toca em achado ou nota; a regra só chega ao motor
 * depois de passar pelo funil, e mesmo assim em modo sombra até ser promovida.
 */
@ApplicationScoped
public class ReviewService {

    private static final int TITLE_MAX_CHARS = 200;
    private static final int TEXT_MAX_CHARS = 2000;
    private static final int RULE_REF_MAX_CHARS = 255;
    private static final int FILE_PATH_MAX_CHARS = 500;
    private static final int NOTE_MAX_CHARS = 500;


    static final int MAX_OPEN_SUGGESTIONS = 200;
    static final int MAX_ACTIVE_PROPOSALS = 30;

    @Inject
    ReviewSuggestionRepository suggestions;
    @Inject
    RuleProposalRepository proposals;
    @Inject
    AuditedSystemRepository systems;
    @Inject
    EntityManager em;

    @ConfigProperty(name = "sentinel.review.min-precision", defaultValue = "0.90")
    double minPrecision;
    @ConfigProperty(name = "sentinel.review.min-hits", defaultValue = "5")
    int minHits;

    /** Proposta com a evidência de observação (zerada antes de começar). */
    public record ProposalView(RuleProposal proposal, RuleFunnel.Evidence evidence, boolean sharedEligible,
                               double minPrecision, int minHits) {}

    // ---- sugestões ----

    /** O que o cliente sugeriu: título, detalhe, regra e local relacionados. */
    public record Suggestion(String title, String detail, String ruleRef, String file, Integer line) { }

    @Transactional
    public ReviewSuggestion addSuggestion(Long systemId, Suggestion in) {
        if (in.title() == null || in.title().isBlank()) {
            throw new IllegalArgumentException("Informe o título da sugestão.");
        }
        if (suggestions.openCount(systemId) >= MAX_OPEN_SUGGESTIONS) {
            throw new IllegalArgumentException("Há " + MAX_OPEN_SUGGESTIONS + " sugestões abertas: resolva ou dispense algumas antes.");
        }
        ReviewSuggestion s = new ReviewSuggestion();
        s.setSystemId(systemId);
        s.setTitle(cut(in.title().strip(), TITLE_MAX_CHARS));
        s.setDetail(in.detail() == null ? null : cut(in.detail().strip(), TEXT_MAX_CHARS));
        s.setRuleRef(cutOrNull(in.ruleRef(), RULE_REF_MAX_CHARS));
        s.setFilePath(cutOrNull(in.file(), FILE_PATH_MAX_CHARS));
        s.setLineNumber(in.line() != null && in.line() > 0 ? in.line() : null);
        suggestions.persist(s);
        return s;
    }

    private static String cutOrNull(String value, int max) {
        return value == null || value.isBlank() ? null : cut(value.strip(), max);
    }

    public List<ReviewSuggestion> suggestions(Long systemId) {
        return suggestions.forSystem(systemId);
    }

    @Transactional
    public ReviewSuggestion setSuggestionStatus(Long systemId, Long id, SuggestionStatus status) {
        ReviewSuggestion s = suggestions.findById(id);
        if (s == null || !s.getSystemId().equals(systemId)) {
            throw new IllegalArgumentException("Sugestão não encontrada.");
        }
        s.setStatus(status);
        return s;
    }

    // ---- regras propostas ----

    @Transactional
    public RuleProposal propose(Long systemId, String slug, String title, String rationale, String yaml) {
        List<String> problems = RuleDraft.validate(slug, yaml);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Informe o título da regra.");
        }
        if (proposals.bySlug(systemId, slug) != null) {
            throw new IllegalArgumentException("Já existe uma regra '" + slug + "' neste sistema.");
        }
        if (proposals.activeCount(systemId) >= MAX_ACTIVE_PROPOSALS) {
            throw new IllegalArgumentException("Há " + MAX_ACTIVE_PROPOSALS + " regras em andamento: promova ou descarte algumas antes.");
        }
        RuleProposal p = new RuleProposal();
        p.setSystemId(systemId);
        p.setSlug(slug);
        p.setTitle(cut(title.strip(), TITLE_MAX_CHARS));
        p.setRationale(rationale == null ? null : cut(rationale.strip(), TEXT_MAX_CHARS));
        p.setSemgrepYaml(yaml);
        proposals.persist(p);
        return p;
    }

    public List<ProposalView> proposals(Long systemId) {
        return proposals.forSystem(systemId).stream().map(this::view).toList();
    }

    public ProposalView view(RuleProposal p) {
        RuleFunnel.Evidence e = evidence(p);
        boolean eligible = RuleFunnel.sharedEligible(p.getStage(), (int) proposals.promotedCopies(p.getId()));
        return new ProposalView(p, e, eligible, minPrecision, minHits);
    }

    @Transactional
    public RuleProposal review(Long systemId, Long id) {
        RuleProposal p = own(systemId, id);
        check(RuleFunnel.review(p.getStage()));
        p.setStage(RuleStage.REVIEWED);
        return p;
    }

    /** O teste de amostra roda fora (no Claude Code); aqui fica só o resultado e a anotação da pessoa. */
    @Transactional
    public RuleProposal sampleTest(Long systemId, Long id, boolean passed, String note) {
        RuleProposal p = own(systemId, id);
        check(RuleFunnel.sampleTest(p.getStage()));
        p.setSampleNote(note == null ? null : cut(note.strip(), NOTE_MAX_CHARS));
        if (passed) {
            p.setStage(RuleStage.SAMPLE_PASSED);
        }
        return p;
    }

    @Transactional
    public RuleProposal startObservation(Long systemId, Long id) {
        RuleProposal p = own(systemId, id);
        check(RuleFunnel.startObservation(p.getStage()));
        p.setStage(RuleStage.OBSERVING);
        p.setObservationStartedAt(LocalDateTime.now());
        return p;
    }

    @Transactional
    public RuleProposal promote(Long systemId, Long id, String note) {
        RuleProposal p = own(systemId, id);
        check(RuleFunnel.promote(p.getStage(), evidence(p), minPrecision, minHits));
        p.setStage(RuleStage.PROMOTED);
        p.setDecidedAt(LocalDateTime.now());
        p.setDecisionNote(note == null ? null : cut(note.strip(), NOTE_MAX_CHARS));
        return p;
    }

    @Transactional
    public RuleProposal discard(Long systemId, Long id, String note) {
        RuleProposal p = own(systemId, id);
        check(RuleFunnel.discard(p.getStage()));
        p.setStage(RuleStage.DISCARDED);
        p.setDecidedAt(LocalDateTime.now());
        p.setDecisionNote(note == null ? null : cut(note.strip(), NOTE_MAX_CHARS));
        return p;
    }

    /** Copia uma regra promovida para outro sistema, já pronta para observação lá. É a prova de que ela funciona fora de onde nasceu. */
    @Transactional
    public RuleProposal copyTo(Long systemId, Long id, Long targetSystemId) {
        RuleProposal origin = own(systemId, id);
        if (origin.getStage() != RuleStage.PROMOTED) {
            throw new IllegalArgumentException("Só uma regra promovida pode ser copiada para outro sistema.");
        }
        if (targetSystemId == null || targetSystemId.equals(systemId) || systems.findByIdOptional(targetSystemId).isEmpty()) {
            throw new IllegalArgumentException("Escolha outro sistema.");
        }
        if (proposals.bySlug(targetSystemId, origin.getSlug()) != null) {
            throw new IllegalArgumentException("O sistema de destino já tem uma regra '" + origin.getSlug() + "'.");
        }
        RuleProposal copy = new RuleProposal();
        copy.setSystemId(targetSystemId);
        copy.setSlug(origin.getSlug());
        copy.setTitle(origin.getTitle());
        copy.setRationale(origin.getRationale());
        copy.setSemgrepYaml(origin.getSemgrepYaml());
        copy.setCopiedFromId(origin.getId());
        copy.setStage(RuleStage.OBSERVING);
        copy.setObservationStartedAt(LocalDateTime.now());
        proposals.persist(copy);
        return copy;
    }

    @Transactional
    public void purge(Long systemId) {
        suggestions.delete("systemId", systemId);
        proposals.delete("systemId", systemId);
    }

    /** Regras que rodam nas auditorias do sistema (em observação ou promovidas). */
    public List<RuleProposal> runningRules(Long systemId) {
        return proposals.running(systemId);
    }

    // ---- evidência ----

    RuleFunnel.Evidence evidence(RuleProposal p) {
        LocalDateTime since = p.getObservationStartedAt();
        if (since == null) {
            return new RuleFunnel.Evidence(0, 0, 0);
        }
        String id = RuleDraft.semgrepId(p.getSlug());
        String suffix = "%." + id;
        long hits = em.createQuery("select count(distinct f.fingerprint) from Finding f where f.auditRun.system.id = :sid "
                        + "and f.auditRun.status = :done and f.auditRun.completedAt >= :since and (f.ruleId = :rid or f.ruleId like :suffix)", Long.class)
                .setParameter("sid", p.getSystemId()).setParameter("done", AuditStatus.COMPLETED)
                .setParameter("since", since).setParameter("rid", id).setParameter("suffix", suffix).getSingleResult();
        long fps = em.createQuery("select count(distinct s.fingerprint) from FindingSuppression s where s.system.id = :sid "
                        + "and s.reason = :fp and s.createdAt >= :since and (s.ruleId = :rid or s.ruleId like :suffix)", Long.class)
                .setParameter("sid", p.getSystemId()).setParameter("fp", SuppressionReason.FALSE_POSITIVE)
                .setParameter("since", since).setParameter("rid", id).setParameter("suffix", suffix).getSingleResult();
        long audits = em.createQuery("select count(r) from AuditRun r where r.system.id = :sid and r.status = :done "
                        + "and r.baseRef is null and r.completedAt >= :since", Long.class)
                .setParameter("sid", p.getSystemId()).setParameter("done", AuditStatus.COMPLETED)
                .setParameter("since", since).getSingleResult();
        return new RuleFunnel.Evidence((int) hits, (int) Math.min(fps, hits), (int) audits);
    }

    private RuleProposal own(Long systemId, Long id) {
        RuleProposal p = proposals.findById(id);
        if (p == null || !p.getSystemId().equals(systemId)) {
            throw new IllegalArgumentException("Regra não encontrada.");
        }
        return p;
    }

    private static void check(RuleFunnel.Verdict v) {
        if (!v.ok()) {
            throw new IllegalArgumentException(v.reason());
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
