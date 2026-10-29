package com.sentinel.audit.resource;

import jakarta.annotation.security.PermitAll;
import com.sentinel.audit.service.AuditRunMapper;
import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.FindingMapper;
import com.sentinel.audit.dto.CreateAuditRequest;
import com.sentinel.audit.dto.FindingResponse;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.AuditOrchestrationService;
import com.sentinel.audit.service.AuditRequestService;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.RunCatalog;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Endpoint REST para execução e consulta de auditorias.
 */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/audits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Audits", description = "Execução e consulta de auditorias de código")
public class AuditResource {

    private static final int MAX_PAGE_SIZE = 50;


    @Inject
    AuditRunMapper runMapper;


    /** Meta de nota da organização, a mesma usada pelo dashboard. */
    static final double TARGET_SCORE = 75;

    @Inject
    RunCatalog runCatalog;

    @Inject
    FindingCatalog findingCatalog;

    @Inject
    AuditRequestService requestService;

    @Inject
    AuditOrchestrationService orchestration;

    @Inject
    AuditStatusViews statusViews;

    @Context
    SecurityContext securityContext;

    @GET
    @Path("/{id}/freshness")
    @Operation(summary = "Auditoria desatualizada?", description = "Compara o commit auditado com a ponta atual da branch no remoto (git ls-remote)")
    public AuditStatusViews.Freshness freshness(@PathParam("id") Long id) {
        return statusViews.freshness(id);
    }

    /**
     * Dispara uma nova auditoria de forma assíncrona.
     * Retorna 202 Accepted com o estado inicial da execução.
     */
    @POST
    @Transactional
    @Operation(summary = "Criar auditoria", description = "Dispara uma nova execução de auditoria de forma assíncrona")
    public Response createAudit(@Valid CreateAuditRequest request) {
        AuditRun run = requestService.request(request.getSystemId(), request.getBranch(), request.getBaseRef(), actor(request.getTriggeredBy()));

        return Response.accepted(runMapper.toResponse(run)).build();
    }

    /** Quem pediu: o usuário autenticado, se houver; senão o que o cliente informou; senão "dashboard". */
    private String actor(String requested) {
        java.security.Principal principal = securityContext == null ? null : securityContext.getUserPrincipal();
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        return requested == null || requested.isBlank() ? "dashboard" : requested.strip();
    }

    /**
     * Últimas auditorias de todos os sistemas (mais nova primeiro), com o andamento das que estão em execução.
     */
    @GET
    @Operation(summary = "Listar auditorias recentes", description = "Últimas auditorias de todos os sistemas, da mais nova para a mais antiga")
    public List<AuditRunResponse> listRecent(@QueryParam("limit") @DefaultValue("8") int limit) {
        int size = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        return runCatalog.recent(size).stream().map(runMapper::toResponse).collect(Collectors.toList());
    }

    /**
     * Cancela uma auditoria pendente ou em execução.
     */
    @POST
    @Path("/{id}/cancel")
    @Operation(summary = "Cancelar auditoria", description = "Interrompe uma auditoria pendente ou em execução; 409 se ela já terminou")
    public Response cancel(@PathParam("id") Long id) {
        AuditRun run = runCatalog.require(id);
        if (!orchestration.cancel(run.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(java.util.Map.of("message", "A auditoria já terminou e não pode ser cancelada"))
                    .build();
        }
        return Response.accepted(runMapper.toResponse(runCatalog.findOrNull(id))).build();
    }

    /**
     * Busca o estado atual de uma execução de auditoria.
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar auditoria", description = "Retorna o estado atual de uma execução de auditoria")
    public AuditRunResponse getAudit(@PathParam("id") Long id) {
        AuditRun run = runCatalog.require(id);
        return runMapper.toResponse(run, true);
    }

    /**
     * Lista os findings de uma execução, com filtros opcionais por severidade, categoria e status.
     */
    @GET
    @Path("/{id}/findings")
    @Operation(summary = "Listar findings", description = "Retorna os findings de uma auditoria com filtros opcionais")
    public List<FindingResponse> getFindings(@PathParam("id") Long id, @BeanParam FindingsQuery q) {

        runCatalog.require(id);

        List<Finding> findings = findingCatalog.byRun(id);

        return findings.stream()
                .filter(f -> q.severity == null || f.getSeverity() == q.severity)
                .filter(f -> q.category == null || f.getCategory() == q.category)
                .filter(f -> q.status == null || f.getStatus() == q.status)
                // suprimidos só aparecem se pedidos (includeSuppressed ou status=SUPPRESSED)
                .filter(f -> q.includeSuppressed || q.status == FindingStatus.SUPPRESSED || f.getStatus() != FindingStatus.SUPPRESSED)
                .filter(f -> !q.onlyNew || Boolean.TRUE.equals(f.getIsNew()))
                .map(FindingMapper::toResponse)
                .collect(Collectors.toList());
    }

    /** Filtros opcionais de {@link #getFindings}. */
    public static class FindingsQuery {
        @QueryParam("severity")
        public Severity severity;
        @QueryParam("category")
        public Category category;
        @QueryParam("status")
        public FindingStatus status;
        @QueryParam("includeSuppressed")
        @DefaultValue("false")
        public boolean includeSuppressed;
        @QueryParam("onlyNew")
        @DefaultValue("false")
        public boolean onlyNew;
    }
}
