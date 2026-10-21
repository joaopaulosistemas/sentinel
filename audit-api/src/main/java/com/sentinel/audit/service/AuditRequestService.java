package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Cria execuções de auditoria. É o ponto único usado pela API manual, pelo agendador e pelo webhook de push.
 * A execução só começa depois do commit da transação que cria o registro (observer AFTER_SUCCESS).
 */
@ApplicationScoped
public class AuditRequestService {

    /** Uma auditoria PENDING/RUNNING mais antiga que isto é considerada travada e não bloqueia novas. */
    static final long STALE_AFTER_HOURS = 2;

    @Inject
    AuditedSystemRepository systemRepository;

    @Inject
    AuditRunRepository auditRunRepository;

    @Inject
    Event<AuditCreated> auditCreatedEvent;

    /** Cria e dispara uma auditoria (sempre). */
    @Transactional
    public AuditRun request(Long systemId, String branch, String triggeredBy) {
        return request(systemId, branch, null, triggeredBy);
    }

    /** Como {@link #request(Long, String, String)}; com {@code baseRef} a auditoria é de PR (só arquivos alterados). */
    @Transactional
    public AuditRun request(Long systemId, String branch, String baseRef, String triggeredBy) {
        AuditedSystem system = systemRepository.findByIdOptional(systemId)
                .orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + systemId));
        AuditRun run = new AuditRun();
        run.setSystem(system);
        run.setBranch(branch);
        run.setBaseRef(baseRef == null || baseRef.isBlank() ? null : baseRef.trim());
        run.setTriggeredBy(triggeredBy);
        run.setStatus(AuditStatus.PENDING);
        run.setStartedAt(LocalDateTime.now());
        auditRunRepository.persist(run);
        auditCreatedEvent.fire(new AuditCreated(run.getId()));
        return run;
    }

    /**
     * Cria a auditoria só se não houver outra em andamento para o mesmo sistema e branch
     * (evita fila de auditorias quando chegam vários pushes seguidos).
     */
    @Transactional
    public Optional<AuditRun> requestIfIdle(Long systemId, String branch, String triggeredBy) {
        if (hasActiveRun(systemId, branch, LocalDateTime.now())) {
            return Optional.empty();
        }
        return Optional.of(request(systemId, branch, triggeredBy));
    }

    boolean hasActiveRun(Long systemId, String branch, LocalDateTime now) {
        return auditRunRepository.findActive().stream()
                .filter(r -> r.getSystem() != null && Objects.equals(r.getSystem().getId(), systemId))
                .filter(r -> Objects.equals(r.getBranch(), branch))
                .anyMatch(r -> r.getStartedAt() == null || r.getStartedAt().isAfter(now.minusHours(STALE_AFTER_HOURS)));
    }
}
