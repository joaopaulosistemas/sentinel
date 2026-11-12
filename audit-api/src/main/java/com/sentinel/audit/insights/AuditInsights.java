package com.sentinel.audit.insights;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** Visão de auditoria completa: resumo executivo, áreas, dívida técnica, prioridades, plano e limites da verificação. */
public record AuditInsights(@Schema(description = "Resumo executivo.") String executiveSummary, @Schema(description = "Veredito.") String verdict, @Schema(description = "Achados por severidade.") Map<String, Integer> bySeverity, @Schema(description = "Achados ativos.") int activeFindings,
                            @Schema(description = "Relatório por área.") List<AreaReport> areas, @Schema(description = "Tabela de dívida técnica.") List<DebtItem> debtTable, @Schema(description = "Maiores problemas.") List<DebtItem> topProblems,
                            @Schema(description = "Maiores melhorias.") List<DebtItem> topImprovements, @Schema(description = "Ganhos rápidos.") List<DebtItem> quickWins, @Schema(description = "Riscos críticos.") List<DebtItem> criticalRisks,
                            @Schema(description = "Plano de ação por fases.") List<RoadmapPhase> roadmap, @Schema(description = "O que não foi possível verificar.") List<String> unverified, @Schema(description = "Como cada analisador se saiu.") List<AnalyzerRunInfo> analyzers,
                            @Schema(description = "Mapa do projeto.") ProjectMap projectMap) {
}
