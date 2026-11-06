package com.sentinel.audit.resource;

import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.service.AuditOrchestrationService;
import com.sentinel.audit.service.AuditRequestService;
import com.sentinel.audit.service.AuditRunMapper;
import com.sentinel.audit.service.RunCatalog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/** O que o endpoint de auditorias devolve: pedir, listar, buscar e cancelar, já no formato da resposta. */
@ApplicationScoped
public class AuditRunViews {

    static final int MAX_PAGE_SIZE = 50;

    @Inject
    AuditRunMapper runMapper;

    @Inject
    RunCatalog runCatalog;

    @Inject
    AuditRequestService requestService;

    @Inject
    AuditOrchestrationService orchestration;

    /** Pede uma auditoria (assíncrona) e devolve o estado inicial dela. */
    AuditRunResponse request(Long systemId, String branch, String baseRef, String actor) {
        AuditRun run = requestService.request(systemId, branch, baseRef, actor);
        return runMapper.toResponse(run);
    }

    /** As últimas auditorias, da mais nova para a mais antiga; o tamanho fica entre 1 e {@link #MAX_PAGE_SIZE}. */
    List<AuditRunResponse> recent(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        return runCatalog.recent(size).stream().map(runMapper::toResponse).toList();
    }

    /** O estado atual de uma auditoria, com o detalhe completo. */
    AuditRunResponse detail(Long id) {
        AuditRun run = runCatalog.require(id);
        return runMapper.toResponse(run, true);
    }

    /** Cancela: 202 com o estado depois do pedido, ou 409 se a auditoria já terminou. */
    Response cancel(Long id) {
        AuditRun run = runCatalog.require(id);
        if (!orchestration.cancel(run.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("message", "A auditoria já terminou e não pode ser cancelada"))
                    .build();
        }
        return Response.accepted(runMapper.toResponse(runCatalog.findOrNull(id))).build();
    }
}
