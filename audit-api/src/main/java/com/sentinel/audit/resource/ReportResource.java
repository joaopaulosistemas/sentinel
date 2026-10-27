package com.sentinel.audit.resource;

import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.service.FindingLifecycleService;
import com.sentinel.audit.service.PortfolioReportService;
import com.sentinel.audit.service.PortfolioReportService.Format;
import com.sentinel.audit.service.PortfolioReportService.SystemSnapshot;
import com.sentinel.audit.service.ReportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Relatórios que juntam todos os sistemas. */
@Path("/api/v1/reports")
@Tag(name = "Relatórios", description = "Relatórios consolidados")
public class ReportResource {

    @Inject
    AuditedSystemRepository systemRepository;

    @Inject
    AuditRunRepository auditRunRepository;

    @Inject
    FindingRepository findingRepository;

    @Inject
    FindingLifecycleService lifecycleService;

    @Inject
    PortfolioReportService portfolio;

    @GET
    @Path("/portfolio")
    @Operation(summary = "Relatório de todos os sistemas",
            description = "type=executivo|tecnico|comparativo, format=html|pdf|csv; days limita o período; systemId restringe a um sistema")
    public Response portfolio(@QueryParam("systemId") Long systemId,
                              @QueryParam("type") String type,
                              @QueryParam("format") String format,
                              @QueryParam("days") Integer days) {
        ReportService.Type kind;
        Format fmt;
        try {
            kind = ReportService.Type.parse(type);
            fmt = Format.parse(format);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        Integer period = days == null || days <= 0 ? null : days;
        LocalDateTime since = period == null ? null : LocalDateTime.now().minusDays(period);
        List<SystemSnapshot> systems = new ArrayList<>();
        // Última auditoria, contagens e (no relatório técnico) achados de todos os sistemas em poucas consultas.
        Map<Long, AuditRun> latestBySystem = auditRunRepository.findLatestCompletedBySystem();
        List<Long> runIds = latestBySystem.values().stream().map(AuditRun::getId).toList();
        Map<Long, Map<com.sentinel.audit.model.Severity, Long>> countsByRun = findingRepository.countBySeverityForRuns(runIds);
        Map<Long, List<com.sentinel.audit.model.Finding>> findingsByRun =
                kind == ReportService.Type.TECNICO ? findingRepository.findByAuditRunIds(runIds) : Map.of();
        for (AuditedSystem system : systemRepository.listAll()) {
            if (systemId != null && !systemId.equals(system.getId())) {
                continue;
            }
            AuditRun run = latestBySystem.get(system.getId());
            if (run == null) {
                continue;
            }
            var counts = countsByRun.get(run.getId());
            List<TrendPoint> points = lifecycleService.trend(system.getId(), 100).stream()
                    .filter(p -> since == null || (p.completedAt() != null && p.completedAt().isAfter(since)))
                    .toList();
            systems.add(new SystemSnapshot(system.getName(), system.getStack() == null ? null : system.getStack().name(),
                    run.getId(), run.getCompletedAt(), run.getRiskScore() == null ? null : run.getRiskScore().getOverall(),
                    counts.getOrDefault(com.sentinel.audit.model.Severity.CRITICAL, 0L),
                    counts.getOrDefault(com.sentinel.audit.model.Severity.HIGH, 0L),
                    kind == ReportService.Type.TECNICO ? findingsByRun.getOrDefault(run.getId(), List.of()) : List.of(),
                    points));
        }
        if (systems.isEmpty()) {
            throw new NotFoundException("Nenhum sistema tem auditoria concluída para compor o relatório.");
        }
        byte[] body = portfolio.render(kind, fmt, systems, period, AuditResource.TARGET_SCORE);
        String name = "todos-os-sistemas-" + kind.name().toLowerCase(Locale.ROOT);
        return switch (fmt) {
            case PDF -> Response.ok(body, "application/pdf").header("Content-Disposition", "inline; filename=\"" + name + ".pdf\"").build();
            case CSV -> Response.ok(body, "text/csv; charset=UTF-8").header("Content-Disposition", "attachment; filename=\"" + name + ".csv\"").build();
            case HTML -> Response.ok(body, MediaType.TEXT_HTML + "; charset=UTF-8").build();
        };
    }
}
