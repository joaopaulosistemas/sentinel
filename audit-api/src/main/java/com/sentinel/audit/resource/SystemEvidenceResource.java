package com.sentinel.audit.resource;

import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.service.EvidenceLibrary;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Evidências de execução do sistema (dump do banco, estatísticas, cobertura, histograma de memória). */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/systems/{id}/evidence")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Systems", description = "Gerenciamento de sistemas auditados")
public class SystemEvidenceResource {

    public record EvidenceList(boolean enabled, List<EvidenceLibrary.EvidenceFile> files) { }

    @Inject
    SystemCatalog systemCatalog;

    @Inject
    EvidenceLibrary library;

    @GET
    @Operation(summary = "Evidências entregues", description = "Arquivos na pasta de evidências do sistema; enabled=false se o servidor não tem a pasta configurada")
    public EvidenceList list(@PathParam("id") Long id) {
        AuditedSystem s = systemCatalog.require(id);
        return new EvidenceList(library.enabled(), library.list(s.getName(), s.getRepoUrl()));
    }

    @PUT
    @Path("/{name}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Entregar uma evidência", description = "Grava (ou substitui) o arquivo na pasta de evidências do sistema; vale a partir da próxima auditoria")
    public Response put(@PathParam("id") Long id, @PathParam("name") String name, InputStream body) {
        AuditedSystem s = systemCatalog.require(id);
        try {
            return Response.ok(library.save(s.getName(), s.getRepoUrl(), name, body)).build();
        } catch (IllegalStateException e) {
            return error(Response.Status.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    @DELETE
    @Path("/{name}")
    @Operation(summary = "Remover uma evidência")
    public Response delete(@PathParam("id") Long id, @PathParam("name") String name) {
        AuditedSystem s = systemCatalog.require(id);
        try {
            if (!library.delete(s.getName(), s.getRepoUrl(), name)) {
                throw new NotFoundException("Evidência não encontrada");
            }
        } catch (IllegalArgumentException e) {
            return error(Response.Status.BAD_REQUEST, e.getMessage());
        }
        return Response.noContent().build();
    }

    private static Response error(Response.Status status, String message) {
        return Response.status(status).entity(java.util.Map.of("message", message)).build();
    }
}
