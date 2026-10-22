package com.sentinel.audit.resource;

import com.sentinel.audit.dto.BatchSuppressRequest;
import com.sentinel.audit.dto.BatchSuppressResponse;
import com.sentinel.audit.dto.FindingsOverviewDto;
import com.sentinel.audit.dto.SuppressRequest;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.service.FindingsOverview;
import com.sentinel.audit.dto.SuppressionResponse;
import com.sentinel.audit.service.FindingLifecycleService;
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
import java.util.ArrayList;
import java.util.List;

/** Ações sobre findings individuais. */
@Path("/api/v1/findings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Findings", description = "Ações sobre findings")
public class FindingResource {

    @Inject
    FindingLifecycleService lifecycleService;

    @Inject
    AuditedSystemRepository systemRepository;

    @Inject
    AuditRunRepository auditRunRepository;

    @Inject
    FindingRepository findingRepository;

    /**
     * Achados de todos os sistemas juntos, da última auditoria concluída de cada um.
     */
    @GET
    @Operation(summary = "Findings de todos os sistemas",
            description = "Última auditoria concluída de cada sistema, com filtros, resumo por sistema e paginação")
    public FindingsOverviewDto overview(
            @QueryParam("systemId") Long systemId,
            @QueryParam("severity") Severity severity,
            @QueryParam("category") Category category,
            @QueryParam("status") FindingStatus status,
            @QueryParam("onlyNew") @DefaultValue("false") boolean onlyNew,
            @QueryParam("includeSuppressed") @DefaultValue("false") boolean includeSuppressed,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        List<FindingsOverview.Source> sources = new ArrayList<>();
        for (AuditedSystem system : systemRepository.listAll()) {
            List<AuditRun> latest = auditRunRepository.findLatestCompleted(system.getId(), 1);
            if (latest.isEmpty()) {
                continue;
            }
            AuditRun run = latest.get(0);
            sources.add(new FindingsOverview.Source(system.getId(), system.getName(), run.getId(),
                    findingRepository.findByAuditRunId(run.getId())));
        }
        return FindingsOverview.build(sources,
                new FindingsOverview.Filter(systemId, severity, category, status, onlyNew, includeSuppressed), limit, offset);
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
