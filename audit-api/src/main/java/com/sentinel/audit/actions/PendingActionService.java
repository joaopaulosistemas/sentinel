package com.sentinel.audit.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinel.audit.dto.SuppressRequest;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.SuppressionReason;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.service.AuditRequestService;
import com.sentinel.audit.service.FindingLifecycleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Pedidos do Claude (iniciar auditoria, suprimir achado) que só viram ação depois da aprovação de uma pessoa
 * no dashboard. O Claude cria o pedido; quem executa é {@link #approve}, e só ele.
 */
@ApplicationScoped
public class PendingActionService {

    /** Teto de pedidos recentes devolvidos por consulta. */
    private static final int MAX_RECENT = 50;
    private static final int MAX_SUMMARY_CHARS = 600;


    @Inject
    PendingActionRepository repository;
    @Inject
    AuditedSystemRepository systems;
    @Inject
    AuditRunRepository runs;
    @Inject
    FindingRepository findings;
    @Inject
    AuditRequestService requestService;
    @Inject
    FindingLifecycleService lifecycle;
    @Inject
    ObjectMapper mapper;

    // ---- pedidos feitos pelo Claude ----

    @Transactional
    public PendingAction requestStartAudit(Long systemId, String branch) {
        AuditedSystem system = system(systemId);
        String effective = branch == null || branch.isBlank()
                ? (system.getScheduledBranch() == null || system.getScheduledBranch().isBlank() ? "main" : system.getScheduledBranch())
                : branch.strip();
        List<String> problems = PendingActionRules.checkBranch(effective);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        ObjectNode payload = mapper.createObjectNode().put("branch", effective);
        return create(system, PendingKind.START_AUDIT, "Iniciar uma auditoria de " + system.getName() + " na branch " + effective, payload);
    }

    @Transactional
    public PendingAction requestSuppress(Long systemId, Long findingId, String reason, String justification) {
        AuditedSystem system = system(systemId);
        SuppressionReason r;
        try {
            r = SuppressionReason.valueOf(reason == null ? "" : reason.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Motivo inválido: use FALSE_POSITIVE.", e);
        }
        List<String> problems = PendingActionRules.checkSuppression(r, justification);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        Finding f = ownFinding(systemId, findingId);
        ObjectNode payload = mapper.createObjectNode().put("findingId", findingId).put("reason", r.name())
                .put("justification", justification.strip());
        String what = (f.getRuleId() == null ? "achado" : f.getRuleId()) + " em " + (f.getFilePath() == null ? "?" : f.getFilePath());
        return create(system, PendingKind.SUPPRESS_FINDING,
                "Suprimir como falso positivo por " + PendingActionRules.SUPPRESSION_DAYS + " dias: " + what
                        + " (" + (f.getTitle() == null ? "sem título" : f.getTitle()) + ")", payload);
    }

    // ---- decisões das pessoas ----

    @Transactional
    public PendingAction approve(Long systemId, Long id) {
        PendingAction a = decidable(systemId, id);
        JsonNode p;
        try {
            p = mapper.readTree(a.getPayload());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Pedido ilegível.", e);
        }
        if (a.getKind() == PendingKind.START_AUDIT) {
            Optional<AuditRun> run = requestService.requestIfIdle(systemId, p.path("branch").asText(), "claude-aprovado");
            if (run.isEmpty()) {
                throw new IllegalArgumentException("Já há uma auditoria em andamento neste sistema e branch.");
            }
            a.setResultAuditRunId(run.get().getId());
            a.setResultNote("Auditoria #" + run.get().getId() + " iniciada.");
        } else {
            long findingId = p.path("findingId").asLong();
            ownFinding(systemId, findingId);
            lifecycle.suppress(findingId, new SuppressRequest(SuppressionReason.FALSE_POSITIVE, p.path("justification").asText(),
                    "Claude (aprovado por uma pessoa)", LocalDateTime.now().plusDays(PendingActionRules.SUPPRESSION_DAYS)));
            a.setResultNote("Achado suprimido por " + PendingActionRules.SUPPRESSION_DAYS + " dias.");
        }
        a.setStatus(PendingStatus.APPROVED);
        a.setDecidedAt(LocalDateTime.now());
        return a;
    }

    @Transactional
    public PendingAction reject(Long systemId, Long id) {
        PendingAction a = decidable(systemId, id);
        a.setStatus(PendingStatus.REJECTED);
        a.setDecidedAt(LocalDateTime.now());
        a.setResultNote("Recusado por uma pessoa.");
        return a;
    }

    // ---- consulta ----

    /** Lista os pedidos recentes, marcando como vencidos os que passaram da validade sem decisão. */
    @Transactional
    public List<PendingAction> recent(Long systemId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<PendingAction> list = repository.recent(systemId, Math.max(1, Math.min(limit, MAX_RECENT)));
        for (PendingAction a : list) {
            if (a.getStatus() == PendingStatus.PENDING && PendingActionRules.expired(a.getExpiresAt(), now)) {
                a.setStatus(PendingStatus.EXPIRED);
                a.setDecidedAt(now);
                a.setResultNote("Perdeu a validade sem decisão.");
            }
        }
        return list;
    }

    @Transactional
    public void purge(Long systemId) {
        repository.delete("systemId", systemId);
    }

    // ---- internos ----

    private PendingAction create(AuditedSystem system, PendingKind kind, String summary, ObjectNode payload) {
        Long systemId = system.getId();
        recent(systemId, MAX_RECENT);                                   // vence o que expirou antes de contar
        if (repository.pendingCount(systemId) >= PendingActionRules.MAX_PENDING_PER_SYSTEM) {
            throw new IllegalArgumentException("Há " + PendingActionRules.MAX_PENDING_PER_SYSTEM
                    + " pedidos esperando aprovação: uma pessoa precisa decidir antes de novos pedidos.");
        }
        String json = payload.toString();
        if (repository.hasPending(systemId, kind, json)) {
            throw new IllegalArgumentException("Já existe um pedido igual esperando aprovação.");
        }
        PendingAction a = new PendingAction();
        a.setSystemId(systemId);
        a.setKind(kind);
        a.setSummary(summary.length() > MAX_SUMMARY_CHARS ? summary.substring(0, MAX_SUMMARY_CHARS) : summary);
        a.setPayload(json);
        a.setExpiresAt(PendingActionRules.expiresAt(a.getCreatedAt()));
        repository.persist(a);
        return a;
    }

    private PendingAction decidable(Long systemId, Long id) {
        PendingAction a = repository.findById(id);
        if (a == null || !a.getSystemId().equals(systemId)) {
            throw new IllegalArgumentException("Pedido não encontrado.");
        }
        String why = PendingActionRules.cannotDecide(a.getStatus(), a.getExpiresAt(), LocalDateTime.now());
        if (why != null) {
            throw new IllegalArgumentException(why);
        }
        return a;
    }

    /** O achado precisa ser da última auditoria concluída do próprio sistema. */
    private Finding ownFinding(Long systemId, Long findingId) {
        Finding f = findingId == null ? null : findings.findById(findingId);
        List<AuditRun> latest = runs.findLatestCompleted(systemId, 1);
        if (f == null || f.getAuditRun() == null || latest.isEmpty() || !latest.get(0).getId().equals(f.getAuditRun().getId())) {
            throw new IllegalArgumentException("Achado não encontrado na última auditoria deste sistema.");
        }
        return f;
    }

    private AuditedSystem system(Long id) {
        return systems.findByIdOptional(id).orElseThrow(() -> new IllegalArgumentException("Sistema não encontrado."));
    }
}
