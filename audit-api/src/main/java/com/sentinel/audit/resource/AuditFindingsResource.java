package com.sentinel.audit.resource;

import com.sentinel.audit.dto.FindingMapper;
import com.sentinel.audit.dto.FindingResponse;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.RunCatalog;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

/** Achados de uma auditoria. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/audits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Audits", description = "Execução e consulta de auditorias de código")
public class AuditFindingsResource {

    @Inject
    RunCatalog runCatalog;

    @Inject
    FindingCatalog findingCatalog;

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
