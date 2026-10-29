package com.sentinel.audit.resource;

import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.FixPlanDto;
import com.sentinel.audit.dto.ScoreExplanationDto;
import com.sentinel.audit.insights.AuditInsights;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Leituras analíticas de uma auditoria: comparação com a anterior, insights, explicação da nota e plano de correção. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/audits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Audits", description = "Análises de auditorias")
public class AuditAnalysisResource {

    @Inject
    AuditReports reports;

    @Inject
    AuditStatusViews statusViews;


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
