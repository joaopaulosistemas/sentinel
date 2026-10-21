package com.sentinel.audit.insights;

import java.util.List;
import java.util.Map;

/** Visão de auditoria completa: resumo executivo, áreas, dívida técnica, prioridades, plano e limites da verificação. */
public record AuditInsights(String executiveSummary, String verdict, Map<String, Integer> bySeverity, int activeFindings,
                            List<AreaReport> areas, List<DebtItem> debtTable, List<DebtItem> topProblems,
                            List<DebtItem> topImprovements, List<DebtItem> quickWins, List<DebtItem> criticalRisks,
                            List<RoadmapPhase> roadmap, List<String> unverified, List<AnalyzerRunInfo> analyzers,
                            ProjectMap projectMap) {
}
