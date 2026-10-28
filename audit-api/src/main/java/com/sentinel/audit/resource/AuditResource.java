package com.sentinel.audit.resource;

import com.sentinel.audit.service.AuditRunMapper;
import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.FindingMapper;
import com.sentinel.audit.dto.FixPlanDto;
import com.sentinel.audit.dto.ScoreExplanationDto;
import com.sentinel.audit.dto.CreateAuditRequest;
import com.sentinel.audit.dto.FindingResponse;
import com.sentinel.audit.insights.AuditInsights;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.AuditOrchestrationService;
import com.sentinel.audit.service.AuditRequestService;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.RunCatalog;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Endpoint REST para execução e consulta de auditorias.
 */
@Path("/api/v1/audits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Audits", description = "Execução e consulta de auditorias de código")
public class AuditResource {

    private static final int MAX_PAGE_SIZE = 50;


    @Inject
    AuditRunMapper runMapper;


    /** Meta de nota da organização, a mesma usada pelo dashboard. */
    static final double TARGET_SCORE = 75;

    @Inject
    RunCatalog runCatalog;

    @Inject
    FindingCatalog findingCatalog;

    @Inject
    AuditRequestService requestService;

    @Inject
    AuditOrchestrationService orchestration;

    @Inject
    AuditReports reports;

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
    public Response createAudit(@Valid CreateAuditRequest request) {
        AuditRun run = requestService.request(request.getSystemId(), request.getBranch(), request.getBaseRef(), actor(request.getTriggeredBy()));

        return Response.accepted(runMapper.toResponse(run)).build();
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
        int size = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        return runCatalog.recent(size).stream().map(runMapper::toResponse).collect(Collectors.toList());
    }

    /**
     * Cancela uma auditoria pendente ou em execução.
     */
    @POST
    @Path("/{id}/cancel")
    @Operation(summary = "Cancelar auditoria", description = "Interrompe uma auditoria pendente ou em execução; 409 se ela já terminou")
    public Response cancel(@PathParam("id") Long id) {
        AuditRun run = runCatalog.require(id);
        if (!orchestration.cancel(run.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(java.util.Map.of("message", "A auditoria já terminou e não pode ser cancelada"))
                    .build();
        }
        return Response.accepted(runMapper.toResponse(runCatalog.findOrNull(id))).build();
    }

    /**
     * Busca o estado atual de uma execução de auditoria.
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar auditoria", description = "Retorna o estado atual de uma execução de auditoria")
    public AuditRunResponse getAudit(@PathParam("id") Long id) {
        AuditRun run = runCatalog.require(id);
        return runMapper.toResponse(run, true);
    }

    /**
     * Lista os findings de uma execução, com filtros opcionais por severidade, categoria e status.
     */
    @GET
    @Path("/{id}/findings")
    @Operation(summary = "Listar findings", description = "Retorna os findings de uma auditoria com filtros opcionais")
    public List<FindingResponse> getFindings(
            @PathParam("id") Long id,
            @QueryParam("severity") Severity severity,
            @QueryParam("category") Category category,
            @QueryParam("status") FindingStatus status,
            @QueryParam("includeSuppressed") @DefaultValue("false") boolean includeSuppressed,
            @QueryParam("onlyNew") @DefaultValue("false") boolean onlyNew) {

        runCatalog.require(id);

        List<Finding> findings = findingCatalog.byRun(id);

        return findings.stream()
                .filter(f -> severity == null || f.getSeverity() == severity)
                .filter(f -> category == null || f.getCategory() == category)
                .filter(f -> status == null || f.getStatus() == status)
                // suprimidos só aparecem se pedidos (includeSuppressed ou status=SUPPRESSED)
                .filter(f -> includeSuppressed || status == FindingStatus.SUPPRESSED || f.getStatus() != FindingStatus.SUPPRESSED)
                .filter(f -> !onlyNew || Boolean.TRUE.equals(f.getIsNew()))
                .map(FindingMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Compara a auditoria com a anterior do mesmo sistema/branch: o que é novo, o que foi corrigido.
     */
    @GET
    @Path("/{id}/diff")
    @Operation(summary = "Comparar com a auditoria anterior",
            description = "Findings novos e corrigidos desde a auditoria anterior (mesmo sistema e branch)")
    public AuditDiffResponse getDiff(@PathParam("id") Long id) {
        return reports.diff(id);
    }

    /**
     * Gera e retorna um relatório HTML da auditoria. O tipo escolhe o conteúdo: técnico (padrão, com todos os
     * achados), executivo (uma página para quem decide) ou comparativo (evolução entre auditorias do sistema).
     */
    @GET
    @Path("/{id}/report")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Relatório HTML", description = "Relatório da auditoria: type=tecnico (padrão), executivo ou comparativo; days limita o período do comparativo")
    public Response getReport(@PathParam("id") Long id,
                              @QueryParam("type") String type,
                              @QueryParam("days") Integer days) {
        return Response.ok(reports.html(id, type, days), MediaType.TEXT_HTML).build();
    }

    /** Mesmos relatórios em PDF, gerado no servidor. */
    @GET
    @Path("/{id}/report.pdf")
    @Produces("application/pdf")
    @Operation(summary = "Relatório PDF", description = "Relatório da auditoria em PDF: type=tecnico (padrão), executivo ou comparativo; days limita o período do comparativo")
    public Response getReportPdf(@PathParam("id") Long id,
                                 @QueryParam("type") String type,
                                 @QueryParam("days") Integer days) {
        AuditReports.PdfFile pdf = reports.pdf(id, type, days);
        return Response.ok(pdf.bytes(), "application/pdf")
                .header("Content-Disposition", "inline; filename=\"" + pdf.filename() + "\"")
                .build();
    }

    /**
     * Achados ativos da auditoria em CSV, para abrir em planilha.
     */
    @GET
    @Path("/{id}/findings.csv")
    @Produces("text/csv")
    @Operation(summary = "Achados em CSV", description = "Achados ativos da auditoria, prontos para planilha")
    public Response getFindingsCsv(@PathParam("id") Long id) {
        String csv = reports.csv(id);
        return Response.ok("\uFEFF" + csv, "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"achados-auditoria-" + id + ".csv\"")
                .build();
    }

    /**
     * SBOM (CycloneDX 1.5, JSON) das dependências encontradas na auditoria.
     */
    @GET
    @Path("/{id}/sbom")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "SBOM CycloneDX", description = "Lista de materiais de software da auditoria (CycloneDX 1.5)")
    public Response getSbom(@PathParam("id") Long id) {
        AuditRun run = runCatalog.require(id);
        if (run.getSbomJson() == null) {
            throw new NotFoundException("Esta auditoria não tem SBOM (auditorias antigas ou falha na geração)");
        }
        return Response.ok(run.getSbomJson(), MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=\"sbom-auditoria-" + id + ".cdx.json\"")
                .build();
    }

    /**
     * Auditoria completa: resumo executivo, relatório por área, tabela de dívida técnica (P0–P3), quick wins,
     * plano em fases e o que não foi possível verificar.
     */
    @GET
    @Path("/{id}/insights")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Visão de auditoria completa",
            description = "Áreas, dívida técnica priorizada, quick wins, roadmap em fases e limites da verificação")
    public AuditInsights getInsights(@PathParam("id") Long id) {
        return statusViews.insights(id);
    }

    /**
     * De onde vem a nota, por dimensão e regra, com o teto por regra marcado. Com {@code without} (ids de regra
     * separados por vírgula) mostra como a nota ficaria sem elas: a simulação "e se".
     */
    @GET
    @Path("/{id}/score-explanation")
    @Operation(summary = "Explicação da nota", description = "Quanto cada regra tirou em cada dimensão; com ?without=regra1,regra2 simula a nota sem elas")
    public ScoreExplanationDto getScoreExplanation(@PathParam("id") Long id, @QueryParam("without") String without) {
        return reports.scoreExplanation(id, without);
    }

    /**
     * O que corrigir primeiro: problemas por regra, com quanto a nota sobe ao resolver cada um.
     */
    @GET
    @Path("/{id}/fix-plan")
    @Operation(summary = "Plano de correção", description = "Problemas ordenados pelo ganho de nota, com a orientação de correção")
    public FixPlanDto getFixPlan(@PathParam("id") Long id) {
        return reports.fixPlan(id);
    }
}
