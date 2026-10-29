package com.sentinel.audit.resource;

import jakarta.annotation.security.PermitAll;
import com.sentinel.audit.dto.BatchSuppressRequest;
import com.sentinel.audit.dto.BatchSuppressResponse;
import com.sentinel.audit.dto.FindingsOverviewDto;
import com.sentinel.audit.dto.SuppressRequest;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.FindingsOverview;
import com.sentinel.audit.dto.SuppressionResponse;
import com.sentinel.audit.service.FindingLifecycleService;
import com.sentinel.audit.service.RunCatalog;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Ações sobre findings individuais. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/findings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Findings", description = "Ações sobre findings")
public class FindingResource {

    @Inject
    FindingLifecycleService lifecycleService;

    @Inject
    SystemCatalog systemCatalog;

    @Inject
    RunCatalog runCatalog;

    @Inject
    FindingCatalog findingCatalog;

    /**
     * Achados de todos os sistemas juntos, da última auditoria concluída de cada um.
     */
    @GET
    @Operation(summary = "Findings de todos os sistemas",
            description = "Última auditoria concluída de cada sistema, com filtros, resumo por sistema e paginação")
    public FindingsOverviewDto overview(
            @QueryParam("systemId") Long systemId,
            @QueryParam("severity") List<Severity> severity,
            @QueryParam("category") Category category,
            @QueryParam("status") FindingStatus status,
            @QueryParam("onlyNew") @DefaultValue("false") boolean onlyNew,
            @QueryParam("includeSuppressed") @DefaultValue("false") boolean includeSuppressed,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        List<FindingsOverview.Source> sources = new ArrayList<>();
        // Última auditoria de cada sistema e os achados delas em duas consultas (antes eram duas por sistema).
        Map<Long, AuditRun> latestBySystem = runCatalog.latestCompletedBySystem();
        Map<Long, List<Finding>> findingsByRun = findingCatalog.byRuns(
                latestBySystem.values().stream().map(AuditRun::getId).toList());
        for (AuditedSystem system : systemCatalog.all()) {
            AuditRun run = latestBySystem.get(system.getId());
            if (run == null) {
                continue;
            }
            sources.add(new FindingsOverview.Source(system.getId(), system.getName(), run.getId(),
                    findingsByRun.getOrDefault(run.getId(), List.of()),
                    system.getStack() == null ? null : system.getStack().name(), run.getCompletedAt()));
        }
        return FindingsOverview.build(sources,
                new FindingsOverview.Filter(systemId, severity == null ? java.util.Set.of() : java.util.Set.copyOf(severity), category, status, onlyNew, includeSuppressed), limit, offset);
    }

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
