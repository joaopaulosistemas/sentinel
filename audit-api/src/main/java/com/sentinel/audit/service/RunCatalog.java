package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.repository.AuditRunRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Acesso às auditorias para a camada de recursos; a regra "auditoria inexistente = 404" fica num lugar só. */
@ApplicationScoped
public class RunCatalog {

    @Inject
    AuditRunRepository repository;

    /** A auditoria, ou {@link NotFoundException} (404) com a mensagem padrão. */
    public AuditRun require(Long id) {
        return find(id).orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));
    }

    /** Falha com 404 se a auditoria não existir; para endpoints que só precisam validar o id. */
    public void requireExists(Long id) {
        require(id);
    }

    public Optional<AuditRun> find(Long id) {
        return repository.findByIdOptional(id);
    }

    /** A auditoria ou {@code null}; para quando ela acabou de ser criada na mesma transação. */
    public AuditRun findOrNull(Long id) {
        return repository.findById(id);
    }

    public List<AuditRun> recent(int limit) {
        return repository.findRecent(limit);
    }

    public List<AuditRun> bySystem(Long systemId) {
        return repository.findBySystemId(systemId);
    }

    /** Auditorias concluídas mais recentes do sistema (a mais nova primeiro). */
    public List<AuditRun> latestCompleted(Long systemId, int limit) {
        return repository.findLatestCompleted(systemId, limit);
    }

    /** A última auditoria concluída de cada sistema, por id do sistema. */
    public Map<Long, AuditRun> latestCompletedBySystem() {
        return repository.findLatestCompletedBySystem();
    }
}
