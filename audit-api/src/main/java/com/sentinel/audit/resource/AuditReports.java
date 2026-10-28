package com.sentinel.audit.resource;

import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.FixPlanDto;
import com.sentinel.audit.dto.ScoreExplanationDto;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.FindingLifecycleService;
import com.sentinel.audit.service.FixPlanner;
import com.sentinel.audit.service.PdfReportService;
import com.sentinel.audit.service.ReportService;
import com.sentinel.audit.service.RunCatalog;
import com.sentinel.audit.service.ScoreTrend;
import com.sentinel.audit.service.ScoringService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * O que a API de auditorias monta a partir dos achados de uma auditoria: relatórios (HTML, PDF, CSV), plano de
 * correção, explicação da nota e comparação com a auditoria anterior. Fica fora do {@link AuditResource} para a
 * classe HTTP cuidar só de rotas e códigos de resposta.
 */
@ApplicationScoped
public class AuditReports {

    @Inject
    RunCatalog runCatalog;

    @Inject
    FindingCatalog findingCatalog;

    @Inject
    ReportService reportService;

    @Inject
    PdfReportService pdfReportService;

    @Inject
    ScoringService scoringService;

    @Inject
    FindingLifecycleService lifecycleService;

    /** PDF pronto para devolver, com o nome sugerido para o arquivo. */
    public record PdfFile(byte[] bytes, String filename) {
    }

    /** Relatório HTML; o tipo escolhe o conteúdo: técnico (padrão), executivo ou comparativo. */
    String html(Long id, String type, Integer days) {
        AuditRun run = runCatalog.require(id);
        ReportService.Type kind = parseType(type);
        List<Finding> findings = findingCatalog.byRun(id);
        return switch (kind) {
            case TECNICO -> reportService.generateHtmlReport(run, findings);
            case EXECUTIVO -> reportService.generateExecutiveReport(run, findings,
                    FixPlanner.plan(FindingLifecycleService.active(findings), run.getLinesOfCode(), scoringService),
                    ScoreTrend.delta(runCatalog.bySystem(run.getSystem().getId())), AuditResource.TARGET_SCORE);
            case COMPARATIVO -> reportService.generateComparativeReport(run, comparativePoints(run, days),
                    days == null || days <= 0 ? null : days);
        };
    }

    /** Os mesmos relatórios em PDF. */
    PdfFile pdf(Long id, String type, Integer days) {
        AuditRun run = runCatalog.require(id);
        ReportService.Type kind = parseType(type);
        List<Finding> findings = findingCatalog.byRun(id);
        byte[] pdf = switch (kind) {
            case TECNICO -> pdfReportService.technical(run, findings);
            case EXECUTIVO -> pdfReportService.executive(run, findings,
                    FixPlanner.plan(FindingLifecycleService.active(findings), run.getLinesOfCode(), scoringService),
                    ScoreTrend.delta(runCatalog.bySystem(run.getSystem().getId())), AuditResource.TARGET_SCORE);
            case COMPARATIVO -> pdfReportService.comparative(run, comparativePoints(run, days),
                    days == null || days <= 0 ? null : days);
        };
        return new PdfFile(pdf, "auditoria-" + id + "-" + kind.name().toLowerCase(Locale.ROOT) + ".pdf");
    }

    /** Achados ativos em CSV, para abrir em planilha. */
    String csv(Long id) {
        runCatalog.require(id);
        return reportService.generateCsv(findingCatalog.byRun(id));
    }

    ScoreExplanationDto scoreExplanation(Long id, String without) {
        AuditRun run = runCatalog.require(id);
        List<Finding> active = FindingLifecycleService.active(findingCatalog.byRun(run.getId()));
        List<String> removed = without == null || without.isBlank() ? List.of() : List.of(without.split(","));
        return scoringService.explainWithout(active, run.getLinesOfCode(), removed);
    }

    FixPlanDto fixPlan(Long id) {
        AuditRun run = runCatalog.require(id);
        List<Finding> active = FindingLifecycleService.active(findingCatalog.byRun(run.getId()));
        return FixPlanner.plan(active, run.getLinesOfCode(), scoringService);
    }

    AuditDiffResponse diff(Long id) {
        return lifecycleService.diff(id);
    }

    private static ReportService.Type parseType(String type) {
        try {
            return ReportService.Type.parse(type);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    private List<TrendPoint> comparativePoints(AuditRun run, Integer days) {
        LocalDateTime since = days == null || days <= 0 ? null : LocalDateTime.now().minusDays(days);
        return lifecycleService.trend(run.getSystem().getId(), 100).stream()
                .filter(p -> since == null || (p.completedAt() != null && p.completedAt().isAfter(since)))
                .collect(Collectors.toList());
    }
}
