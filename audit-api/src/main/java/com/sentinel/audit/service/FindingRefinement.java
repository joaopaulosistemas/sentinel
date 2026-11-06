package com.sentinel.audit.service;

import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.FindingDeduplicator;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * O que acontece com os achados entre a análise e a nota: deduplicação, política do perfil, auditoria de PR (só
 * arquivos alterados), {@code .sentinel.yml} do projeto, marca de "novo" e confiança de cada achado.
 */
final class FindingRefinement {

    private static final Logger LOG = Logger.getLogger(FindingRefinement.class);

    private final AuditRunStore store;
    private final AuditProgressTracker progress;
    private final IngestionService ingestionService;

    FindingRefinement(AuditRunStore store, AuditProgressTracker progress, IngestionService ingestionService) {
        this.store = store;
        this.progress = progress;
        this.ingestionService = ingestionService;
    }

    /** Deduplica, aplica a política e o .sentinel.yml, marca o que é novo e avalia a confiança de cada achado. */
    List<Finding> refine(Long auditRunId, AuditOrchestrationService.AuditContext context, Path clonedPath,
                         AnalyzerRunner.Analysis analysis, AuditPolicy policy) {
        List<Finding> allFindings = policy.apply(FindingDeduplicator.apply(analysis.findings()));
        allFindings = onlyChangedFiles(allFindings, context, clonedPath);
        // O projeto pode declarar em .sentinel.yml o que não entra na nota (exclude) e o que aceitou (suppress).
        ProjectConfig.Result projectConfig = applyProjectConfig(auditRunId, clonedPath, allFindings);
        allFindings = projectConfig.findings();

        // Marca o que é novo desde a auditoria anterior e retira do score o que foi suprimido.
        progress.phase(auditRunId, AuditProgressTracker.PONTUACAO,
                "Análise concluída: " + allFindings.size() + " achados" + projectConfig.summary() + "; calculando a nota");
        FindingLifecycleService.apply(allFindings, store.loadBaselineSafely(auditRunId));
        FindingConfidence.assessAll(allFindings, store.loadRuleQualitySafely(), globalAuthOf(clonedPath),
                GeneratedFiles.detect(clonedPath, allFindings));
        return allFindings;
    }

    private ProjectConfig.Result applyProjectConfig(Long auditRunId, Path clonedPath, List<Finding> findings) {
        ProjectConfig.Result result = ProjectConfig.load(clonedPath).apply(findings, LocalDate.now());
        result.problems().forEach(problem -> LOG.warnf("Auditoria %d: %s", auditRunId, problem));
        return result;
    }

    /**
     * Auditoria de PR: mantém só achados em arquivos alterados em relação ao branch base. Se não for possível saber o
     * que mudou, mantém tudo (é o lado seguro: nada é escondido).
     */
    private List<Finding> onlyChangedFiles(List<Finding> findings, AuditOrchestrationService.AuditContext context,
                                           Path clonedPath) {
        if (context.baseRef() == null || context.baseRef().isBlank()) {
            return findings;
        }
        if (context.local() && context.localSnapshot() == LocalSnapshot.TREE) {
            LOG.warnf("Auditoria de PR contra '%s': a cópia 'pasta como está' não tem histórico para comparar; todos os achados foram mantidos",
                    context.baseRef());
            return findings;
        }
        ChangedFiles.Result changed = context.local()
                ? ingestionService.changedFilesLocal(context.localPath(), clonedPath, context.baseRef())
                : ingestionService.changedFiles(context.repoUrl(), clonedPath, context.baseRef(), context.gitToken(), context.gitTokenHosts());
        if (changed.files() == null) {
            LOG.warnf("Auditoria de PR: não foi possível comparar com '%s' (%s); todos os achados foram mantidos",
                    context.baseRef(), changed.note());
            return findings;
        }
        List<Finding> kept = ChangedFiles.filter(findings, changed.files());
        LOG.infof("Auditoria de PR contra '%s': %d arquivo(s) alterado(s); %d de %d achados mantidos%s",
                context.baseRef(), changed.files().size(), kept.size(), findings.size(),
                changed.approximate() ? " (comparação aproximada: sem ancestral comum no histórico baixado)" : "");
        return kept;
    }

    /** Barreira de autenticação global do projeto (filtro, Spring Security, política do Quarkus), ou nulo. */
    private static String globalAuthOf(Path clonedPath) {
        return clonedPath == null ? null : com.sentinel.audit.analyzer.structure.GlobalAuth.detect(clonedPath);
    }
}
