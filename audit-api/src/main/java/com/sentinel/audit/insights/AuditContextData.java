package com.sentinel.audit.insights;

import java.util.List;

/** O que foi observado durante a auditoria além dos findings: o mapa do projeto e a situação de cada analisador. */
public record AuditContextData(ProjectMap projectMap, List<AnalyzerRunInfo> analyzerRuns) {
}
