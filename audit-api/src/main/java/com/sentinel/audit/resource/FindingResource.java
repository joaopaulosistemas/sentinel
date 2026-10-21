package com.sentinel.audit.resource;

import com.sentinel.audit.dto.SuppressRequest;
import com.sentinel.audit.dto.SuppressionResponse;
import com.sentinel.audit.service.FindingLifecycleService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;

/** Ações sobre findings individuais. */
@Path("/api/v1/findings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Findings", description = "Ações sobre findings")
public class FindingResource {

    @Inject
    FindingLifecycleService lifecycleService;

    /**
     * Retira o finding do score (falso positivo, risco aceito ou "não será corrigido"), com justificativa.
     * A decisão vale para o sistema: auditorias futuras com o mesmo problema já nascem suprimidas até o prazo.
     */
    @POST
    @Path("/{id}/suppress")
    @Operation(summary = "Suprimir finding",
            description = "Marca como falso positivo/risco aceito com justificativa obrigatória e prazo opcional")
    public SuppressionResponse suppress(@PathParam("id") Long id, @Valid SuppressRequest request) {
        return SuppressionResponse.from(lifecycleService.suppress(id, request), LocalDateTime.now());
    }
}
