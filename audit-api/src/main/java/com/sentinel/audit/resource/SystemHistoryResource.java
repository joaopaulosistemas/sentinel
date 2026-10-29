package com.sentinel.audit.resource;

import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.SuppressionResponse;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/** Histórico de um sistema: tendência da nota, supressões e auditorias. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/systems")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Systems", description = "Gerenciamento de sistemas auditados")
public class SystemHistoryResource {

    @Inject
    SystemCatalog systemCatalog;

    @Inject
    SystemViews views;


    /**
     * Evolução do score do sistema ao longo das auditorias concluídas (mais antiga primeiro).
     */
    @GET
    @Path("/{id}/trend")
    @Operation(summary = "Tendência do score", description = "Score e contagem por severidade das últimas auditorias concluídas")
    public List<TrendPoint> trend(@PathParam("id") Long id, @QueryParam("limit") @DefaultValue("30") int limit) {
        systemCatalog.require(id);
        return views.trend(id, limit);
    }

    /**
     * Supressões (falsos positivos / riscos aceitos) registradas para o sistema.
     */
    @GET
    @Path("/{id}/suppressions")
    @Operation(summary = "Listar supressões")
    public List<SuppressionResponse> suppressions(@PathParam("id") Long id) {
        systemCatalog.require(id);
        return views.suppressions(id);
    }

    /**
     * Remove uma supressão: o finding volta a contar no score.
     */
    @DELETE
    @Path("/{id}/suppressions/{suppressionId}")
    @Operation(summary = "Remover supressão")
    public Response removeSuppression(@PathParam("id") Long id, @PathParam("suppressionId") Long suppressionId) {
        views.removeSuppression(id, suppressionId);
        return Response.noContent().build();
    }

    /**
     * Lista todas as execuções de auditoria de um sistema.
     */
    @GET
    @Path("/{id}/audits")
    @Operation(summary = "Listar auditorias do sistema", description = "Retorna todas as execuções de auditoria de um sistema")
    public List<AuditRunResponse> listAudits(@PathParam("id") Long id) {
        systemCatalog.require(id);

        return views.audits(id);
    }
}
