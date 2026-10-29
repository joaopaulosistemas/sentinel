package com.sentinel.audit.resource;

import com.sentinel.audit.dto.BatchSuppressRequest;
import com.sentinel.audit.dto.BatchSuppressResponse;
import com.sentinel.audit.dto.SuppressRequest;
import com.sentinel.audit.dto.SuppressionResponse;
import com.sentinel.audit.service.FindingLifecycleService;
import jakarta.annotation.security.PermitAll;
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
import java.util.ArrayList;
import java.util.List;

/** Supressão de findings individuais ou em lote. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/findings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Findings", description = "Ações sobre findings")
public class FindingSuppressionResource {

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

    /**
     * Suprime vários findings com o mesmo motivo. Cada um é tratado separadamente: um inexistente
     * não impede os demais, e o retorno lista as falhas.
     */
    @POST
    @Path("/suppress-batch")
    @Operation(summary = "Suprimir findings em lote",
            description = "Mesmo motivo e justificativa para até " + BatchSuppressRequest.MAX_IDS + " findings")
    public BatchSuppressResponse suppressBatch(@Valid BatchSuppressRequest request) {
        return runBatch(request, lifecycleService);
    }

    static BatchSuppressResponse runBatch(BatchSuppressRequest request, FindingLifecycleService service) {
        List<Long> ids = request.findingIds().stream().distinct().toList();
        List<BatchSuppressResponse.Failure> failures = new ArrayList<>();
        int done = 0;
        for (Long id : ids) {
            try {
                service.suppress(id, request.toSingle());
                done++;
            } catch (jakarta.ws.rs.WebApplicationException e) {
                failures.add(new BatchSuppressResponse.Failure(id, e.getMessage()));
            }
        }
        return new BatchSuppressResponse(ids.size(), done, failures);
    }
}
