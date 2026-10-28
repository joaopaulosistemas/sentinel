package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.repository.AuditedSystemRepository;
import com.sentinel.audit.repository.FindingSuppressionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Acesso aos sistemas cadastrados para a camada de recursos (REST/MCP). Concentra a regra "sistema inexistente =
 * 404 com a mesma mensagem", que antes se repetia em cada endpoint, e mantém os recursos longe do repositório.
 */
@ApplicationScoped
public class SystemCatalog {

    @Inject
    AuditedSystemRepository repository;

    @Inject
    FindingSuppressionRepository suppressionRepository;

    /** O sistema, ou {@link NotFoundException} (404) com a mensagem padrão. */
    public AuditedSystem require(Long id) {
        return find(id).orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + id));
    }

    /** Falha com 404 se o sistema não existir; para endpoints que só precisam validar o id. */
    public void requireExists(Long id) {
        require(id);
    }

    public Optional<AuditedSystem> find(Long id) {
        return repository.findByIdOptional(id);
    }

    /** O sistema ou {@code null} (id desconhecido não é erro para quem só quer reaproveitar dados salvos). */
    public AuditedSystem findOrNull(Long id) {
        return repository.findById(id);
    }

    public List<AuditedSystem> all() {
        return repository.listAll();
    }

    /** Sistemas cujo nome, em minúsculas, é exatamente {@code lowerName}. */
    public List<AuditedSystem> byLowerName(String lowerName) {
        return repository.list("lower(name) = ?1", lowerName);
    }

    public void add(AuditedSystem system) {
        repository.persist(system);
    }

    /** Remove o sistema; as supressões referenciam o sistema (FK) e saem antes. */
    public void remove(AuditedSystem system) {
        suppressionRepository.findBySystemId(system.getId()).forEach(suppressionRepository::delete);
        repository.delete(system);
    }
}
