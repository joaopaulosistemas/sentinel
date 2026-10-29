package com.sentinel.audit.resource;

import jakarta.annotation.security.PermitAll;
import com.sentinel.audit.dto.FindingsOverviewDto;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.FindingsOverview;
import com.sentinel.audit.service.RunCatalog;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Visão de achados de todos os sistemas. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/findings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Findings", description = "Ações sobre findings")
public class FindingResource {

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
    public FindingsOverviewDto overview(@BeanParam OverviewQuery q) {
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
                new FindingsOverview.Filter(q.systemId, q.severity == null ? java.util.Set.of() : java.util.Set.copyOf(q.severity),
                        q.category, q.status, q.onlyNew, q.includeSuppressed), q.limit, q.offset);
    }

    /** Filtros e paginação de {@link #overview}. */
    public static class OverviewQuery {
        @QueryParam("systemId")
        public Long systemId;
        @QueryParam("severity")
        public List<Severity> severity;
        @QueryParam("category")
        public Category category;
        @QueryParam("status")
        public FindingStatus status;
        @QueryParam("onlyNew")
        @DefaultValue("false")
        public boolean onlyNew;
        @QueryParam("includeSuppressed")
        @DefaultValue("false")
        public boolean includeSuppressed;
        @QueryParam("limit")
        @DefaultValue("20")
        public int limit;
        @QueryParam("offset")
        @DefaultValue("0")
        public int offset;
    }
}
