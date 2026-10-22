package com.sentinel.audit.resource;

import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.FindingMapper;
import com.sentinel.audit.dto.CreateAuditRequest;
import com.sentinel.audit.dto.FindingResponse;
import com.sentinel.audit.dto.RiskScoreDto;
import com.sentinel.audit.insights.AuditInsights;
import com.sentinel.audit.insights.InsightsService;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.service.AuditRequestService;
import com.sentinel.audit.service.FindingLifecycleService;
import com.sentinel.audit.service.ReportService;
import com.sentinel.audit.service.AuditProgressTracker;
import com.sentinel.audit.model.AuditStatus;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

    @Inject
    AuditProgressTracker progressTracker;

    @Inject
    InsightsService insightsService;

    @Inject
    AuditRunRepository auditRunRepository;

    @Inject
    FindingRepository findingRepository;

    @Inject
    ReportService reportService;

    @Inject
    FindingLifecycleService lifecycleService;

    @Inject
    AuditRequestService requestService;

    /**
     * Dispara uma nova auditoria de forma assíncrona.
     * Retorna 202 Accepted com o estado inicial da execução.
     */
    @POST
    @Transactional
    @Operation(summary = "Criar auditoria", description = "Dispara uma nova execução de auditoria de forma assíncrona")
    public Response createAudit(@Valid CreateAuditRequest request) {
        AuditRun run = requestService.request(request.getSystemId(), request.getBranch(), request.getBaseRef(), request.getTriggeredBy());

        return Response.accepted(toResponse(run)).build();
    }

    /**
     * Busca o estado atual de uma execução de auditoria.
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar auditoria", description = "Retorna o estado atual de uma execução de auditoria")
    public AuditRunResponse getAudit(@PathParam("id") Long id) {
        AuditRun run = auditRunRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));
        return toResponse(run);
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

        auditRunRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));

        List<Finding> findings = findingRepository.findByAuditRunId(id);

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
        return lifecycleService.diff(id);
    }

    /**
     * Gera e retorna um relatório HTML da auditoria.
     */
    @GET
    @Path("/{id}/report")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Relatório HTML", description = "Gera um relatório HTML completo da auditoria")
    public Response getReport(@PathParam("id") Long id) {
        AuditRun run = auditRunRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));

        List<Finding> findings = findingRepository.findByAuditRunId(id);
        String html = reportService.generateHtmlReport(run, findings);

        return Response.ok(html, MediaType.TEXT_HTML).build();
    }

    /**
     * SBOM (CycloneDX 1.5, JSON) das dependências encontradas na auditoria.
     */
    @GET
    @Path("/{id}/sbom")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "SBOM CycloneDX", description = "Lista de materiais de software da auditoria (CycloneDX 1.5)")
    public Response getSbom(@PathParam("id") Long id) {
        AuditRun run = auditRunRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));
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
        AuditRun run = auditRunRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));
        return insightsService.forRun(run);
    }

    private AuditRunResponse toResponse(AuditRun run) {
        AuditRunResponse dto = new AuditRunResponse();
        dto.setId(run.getId());
        dto.setSystemName(run.getSystem().getName());
        dto.setBranch(run.getBranch());
        dto.setBaseRef(run.getBaseRef());
        dto.setStatus(run.getStatus());
        dto.setStartedAt(run.getStartedAt());
        dto.setCompletedAt(run.getCompletedAt());
        dto.setErrorMessage(run.getErrorMessage());

        if (run.getRiskScore() != null) {
            dto.setRiskScore(new RiskScoreDto(
                    run.getRiskScore().getOverall(),
                    run.getRiskScore().getSecurity(),
                    run.getRiskScore().getQuality(),
                    run.getRiskScore().getArchitecture(),
                    run.getRiskScore().getDependencies(),
                    run.getRiskScore().getObservability(),
                    run.getRiskScore().getPerformance()
            ));
        }

        dto.setFindingsSummary(findingRepository.countBySeverityForRun(run.getId()));
        if (run.getStatus() == AuditStatus.RUNNING || run.getStatus() == AuditStatus.PENDING) {
            progressTracker.snapshot(run.getId()).ifPresent(dto::setProgress);
        }
        return dto;
    }
}
