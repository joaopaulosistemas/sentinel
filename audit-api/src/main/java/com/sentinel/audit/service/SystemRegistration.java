package com.sentinel.audit.service;

import com.sentinel.audit.dto.CreateSystemRequest;
import com.sentinel.audit.dto.ScheduleRequest;
import com.sentinel.audit.model.AuditedSystem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Casos de uso do cadastro de sistemas: criar, editar, agendar, trocar o perfil e remover. A regra e a fronteira da
 * transação ficam aqui; o recurso REST só recebe o pedido e monta a resposta.
 */
@ApplicationScoped
public class SystemRegistration {

    @Inject
    SystemCatalog catalog;

    @Inject
    SystemWriter writer;

    @Inject
    SystemCleanup cleanup;

    @Transactional
    public AuditedSystem create(CreateSystemRequest request) {
        AuditedSystem system = new AuditedSystem();
        writer.apply(system, request, true);
        catalog.add(system);
        return system;
    }

    @Transactional
    public AuditedSystem update(Long id, CreateSystemRequest request) {
        AuditedSystem system = catalog.require(id);
        writer.apply(system, request, false);
        return system;
    }

    @Transactional
    public AuditedSystem delete(Long id) {
        AuditedSystem system = catalog.require(id);
        cleanup.purgeRelated(id);
        catalog.remove(system);
        return system;
    }

    /** Intervalo em horas (vazio ou até 0 desliga), branch auditada (vazia = padrão) e auditoria a cada push. */
    @Transactional
    public AuditedSystem updateSchedule(Long id, ScheduleRequest request) {
        AuditedSystem system = catalog.require(id);
        Integer hours = request.auditIntervalHours();
        system.setAuditIntervalHours(hours == null || hours <= 0 ? null : hours);
        system.setScheduledBranch(request.branch() == null || request.branch().isBlank() ? null : request.branch().trim());
        system.setAuditOnPush(Boolean.TRUE.equals(request.auditOnPush()));
        return system;
    }

    /** Perfil conhecido (ou vazio = default); um perfil desconhecido é {@link InvalidInputException}. */
    @Transactional
    public AuditedSystem updateProfile(Long id, String profile) {
        AuditedSystem system = catalog.require(id);
        system.setProfile(writer.validProfile(profile));
        return system;
    }
}
