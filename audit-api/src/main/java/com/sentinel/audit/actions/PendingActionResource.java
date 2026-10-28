package com.sentinel.audit.actions;

import com.sentinel.audit.service.SystemCatalog;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.function.Supplier;

/** Pedidos do Claude esperando uma pessoa: lista, aprova (e só então executa) ou recusa. */
@Path("/api/v1/pending-actions/{id}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Pedidos do Claude", description = "Ações pedidas pelo Claude que dependem de aprovação")
public class PendingActionResource {

    /** Quantos pedidos recentes a tela mostra por sistema. */
    private static final int RECENT_LIMIT = 30;


    public record PendingDto(Long id, String kind, String summary, String status, String createdAt, String expiresAt,
                             String decidedAt, String resultNote, Long auditRunId) {}

    @Inject
    SystemCatalog systemCatalog;
    @Inject
    PendingActionService service;

    @GET
    @Operation(summary = "Pedidos recentes do Claude neste sistema")
    public List<PendingDto> list(@PathParam("id") Long id) {
        requireSystem(id);
        return service.recent(id, RECENT_LIMIT).stream().map(PendingActionResource::dto).toList();
    }

    @POST
    @Path("/{actionId}/approve")
    @Operation(summary = "Aprovar o pedido: é neste ponto que a ação acontece")
    public List<PendingDto> approve(@PathParam("id") Long id, @PathParam("actionId") Long actionId) {
        requireSystem(id);
        return decide(id, () -> service.approve(id, actionId));
    }

    @POST
    @Path("/{actionId}/reject")
    @Operation(summary = "Recusar o pedido")
    public List<PendingDto> reject(@PathParam("id") Long id, @PathParam("actionId") Long actionId) {
        requireSystem(id);
        return decide(id, () -> service.reject(id, actionId));
    }

    private List<PendingDto> decide(Long id, Supplier<PendingAction> action) {
        try {
            action.get();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
        return service.recent(id, RECENT_LIMIT).stream().map(PendingActionResource::dto).toList();
    }

    static PendingDto dto(PendingAction a) {
        return new PendingDto(a.getId(), a.getKind().name(), a.getSummary(), a.getStatus().name(), a.getCreatedAt().toString(),
                a.getExpiresAt().toString(), a.getDecidedAt() == null ? null : a.getDecidedAt().toString(), a.getResultNote(),
                a.getResultAuditRunId());
    }

    private void requireSystem(Long id) {
        systemCatalog.requireExists(id);
    }
}
