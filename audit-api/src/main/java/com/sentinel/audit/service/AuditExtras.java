package com.sentinel.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.analyzer.deps.DependencyExtractor;
import com.sentinel.audit.analyzer.deps.SbomWriter;
import com.sentinel.audit.insights.AnalyzerRunInfo;
import com.sentinel.audit.insights.AuditContextData;
import com.sentinel.audit.insights.ProjectMap;
import com.sentinel.audit.insights.ProjectProfiler;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Extras gravados depois da auditoria (SBOM e mapa do projeto): se falharem, a auditoria continua COMPLETED. */
final class AuditExtras {

    private static final Logger LOG = Logger.getLogger(AuditExtras.class);

    private AuditExtras() {
    }

    /** O SBOM é um extra: se não der para gerar ou salvar, a auditoria continua COMPLETED. */
    static void saveSbom(AuditRunStore store, Long auditRunId, AuditOrchestrationService.AuditContext context, Path projectPath) {
        try {
            String sbom = SbomWriter.toCycloneDx(context.systemName(), context.branch(),
                    DependencyExtractor.extract(projectPath), Instant.now(), UUID.randomUUID());
            store.storeSbom(auditRunId, sbom);
        } catch (RuntimeException e) {
            LOG.warnf("Não foi possível gerar/salvar o SBOM da auditoria %d: %s", auditRunId, e.getMessage());
        }
    }

    /** Mapa do projeto e situação dos analisadores: alimentam os insights; se falhar, a auditoria continua COMPLETED. */
    static void saveContext(AuditRunStore store, Long auditRunId, Path projectPath, List<AnalyzerRunInfo> runs) {
        try {
            ProjectMap map = ProjectProfiler.profile(projectPath);
            store.storeContext(auditRunId, new ObjectMapper().writeValueAsString(new AuditContextData(map, runs)));
        } catch (Exception e) {
            LOG.warnf("Não foi possível salvar o mapa do projeto da auditoria %d: %s", auditRunId, e.getMessage());
        }
    }
}
