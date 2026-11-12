package com.sentinel.audit.resource;

import jakarta.annotation.security.PermitAll;
import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.CreateAuditRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
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
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

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

    /** Meta de nota da organização, a mesma usada pelo dashboard. */
    static final double TARGET_SCORE = 75;

    @Inject
    AuditRunViews runViews;

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
    @APIResponse(responseCode = "202", description = "Auditoria aceita; roda de forma assíncrona",
                content = {@Content(mediaType = "application/json", schema = @Schema(implementation = AuditRunResponse.class))})
    public Response createAudit(@Valid CreateAuditRequest request) {
        AuditRunResponse created = runViews.request(request.getSystemId(), request.getBranch(), request.getBaseRef(),
                actor(request.getTriggeredBy()));
        return Response.accepted(created).build();
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
        return runViews.recent(limit);
    }

    /**
     * Cancela uma auditoria pendente ou em execução.
     */
    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Cancelar auditoria", description = "Interrompe uma auditoria pendente ou em execução; 409 se ela já terminou")
    @APIResponse(responseCode = "202", description = "Cancelamento pedido",
                content = {@Content(mediaType = "application/json", schema = @Schema(implementation = AuditRunResponse.class))})
    @APIResponse(responseCode = "409", description = "A auditoria já terminou e não pode ser cancelada",
                content = {@Content(mediaType = "application/json", schema = @Schema(ref = "ApiError"))})
    public Response cancel(@PathParam("id") Long id) {
        return runViews.cancel(id);
    }

    /**
     * Busca o estado atual de uma execução de auditoria.
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar auditoria", description = "Retorna o estado atual de uma execução de auditoria")
    public AuditRunResponse getAudit(@PathParam("id") Long id) {
        return runViews.detail(id);
    }
}
