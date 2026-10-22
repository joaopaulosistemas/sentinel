package com.sentinel.audit.resource;

import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.FindingMapper;
import com.sentinel.audit.dto.FixPlanDto;
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
import com.sentinel.audit.service.AuditOrchestrationService;
import com.sentinel.audit.service.AuditRequestService;
import com.sentinel.audit.service.FindingLifecycleService;
import com.sentinel.audit.service.FixPlanner;
import com.sentinel.audit.service.ScoreTrend;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.service.ScoringService;
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

    /** Meta de nota da organização, a mesma usada pelo dashboard. */
    static final double TARGET_SCORE = 75;

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
    ScoringService scoringService;

    @Inject
    FindingLifecycleService lifecycleService;

    @Inject
    AuditRequestService requestService;

    @Inject
    AuditOrchestrationService orchestration;

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
     * Últimas auditorias de todos os sistemas (mais nova primeiro), com o andamento das que estão em execução.
     */
    @GET
    @Operation(summary = "Listar auditorias recentes", description = "Últimas auditorias de todos os sistemas, da mais nova para a mais antiga")
    public List<AuditRunResponse> listRecent(@QueryParam("limit") @DefaultValue("8") int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return auditRunRepository.findRecent(size).stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Cancela uma auditoria pendente ou em execução.
     */
    @POST
    @Path("/{id}/cancel")
    @Operation(summary = "Cancelar auditoria", description = "Interrompe uma auditoria pendente ou em execução; 409 se ela já terminou")
    public Response cancel(@PathParam("id") Long id) {
        AuditRun run = auditRunRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));
        if (!orchestration.cancel(run.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(java.util.Map.of("message", "A auditoria já terminou e não pode ser cancelada"))
                    .build();
        }
        return Response.accepted(toResponse(auditRunRepository.findById(id))).build();
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
        AuditRun run = auditRunRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));
        ReportService.Type kind;
        try {
            kind = ReportService.Type.parse(type);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        List<Finding> findings = findingRepository.findByAuditRunId(id);
        String html = switch (kind) {
            case TECNICO -> reportService.generateHtmlReport(run, findings);
            case EXECUTIVO -> reportService.generateExecutiveReport(run, findings,
                    FixPlanner.plan(FindingLifecycleService.active(findings), run.getLinesOfCode(), scoringService),
                    ScoreTrend.delta(auditRunRepository.findBySystemId(run.getSystem().getId())), TARGET_SCORE);
            case COMPARATIVO -> {
                java.time.LocalDateTime since = days == null || days <= 0 ? null : java.time.LocalDateTime.now().minusDays(days);
                List<TrendPoint> points = lifecycleService.trend(run.getSystem().getId(), 100).stream()
                        .filter(p -> since == null || (p.completedAt() != null && p.completedAt().isAfter(since)))
                        .collect(Collectors.toList());
                yield reportService.generateComparativeReport(run, points, days == null || days <= 0 ? null : days);
            }
        };
        return Response.ok(html, MediaType.TEXT_HTML).build();
    }

    /**
     * Achados ativos da auditoria em CSV, para abrir em planilha.
     */
    @GET
    @Path("/{id}/findings.csv")
    @Produces("text/csv")
    @Operation(summary = "Achados em CSV", description = "Achados ativos da auditoria, prontos para planilha")
    public Response getFindingsCsv(@PathParam("id") Long id) {
        auditRunRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));
        String csv = reportService.generateCsv(findingRepository.findByAuditRunId(id));
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

    /**
     * O que corrigir primeiro: problemas por regra, com quanto a nota sobe ao resolver cada um.
     */
    @GET
    @Path("/{id}/fix-plan")
    @Operation(summary = "Plano de correção", description = "Problemas ordenados pelo ganho de nota, com a orientação de correção")
    public FixPlanDto getFixPlan(@PathParam("id") Long id) {
        AuditRun run = auditRunRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Auditoria não encontrada: " + id));
        List<Finding> active = FindingLifecycleService.active(findingRepository.findByAuditRunId(run.getId()));
        return FixPlanner.plan(active, run.getLinesOfCode(), scoringService);
    }

    private AuditRunResponse toResponse(AuditRun run) {
        AuditRunResponse dto = new AuditRunResponse();
        dto.setId(run.getId());
        dto.setSystemId(run.getSystem().getId());
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
