package com.sentinel.audit.service;

import com.sentinel.audit.pack.FindingDeduplicator;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.model.RiskScore;
import com.sentinel.audit.model.SourceType;
import com.sentinel.audit.model.Stack;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serviço de orquestração do fluxo completo de auditoria.
 * <p>
 * Coordena o clone do repositório, a execução paralela dos analyzers,
 * o cálculo do score e a persistência dos resultados.
 * <p>
 * Pontos de atenção tratados nesta classe:
 * <ul>
 *   <li>Nenhuma entidade JPA é usada fora de transação (evita {@code LazyInitializationException}).</li>
 *   <li>A execução aguarda o registro da auditoria estar visível no banco, pois o disparo
 *       acontece de dentro da transação que o cria.</li>
 *   <li>Falha em um analisador não derruba a auditoria inteira.</li>
 *   <li>Campos de texto são truncados ao tamanho das colunas antes de persistir.</li>
 *   <li>Só são executados analisadores que suportam a stack do sistema.</li>
 * </ul>
 */
@ApplicationScoped
public class AuditOrchestrationService {

    private static final Logger LOG = Logger.getLogger(AuditOrchestrationService.class);

    @Inject
    IngestionService ingestionService;

    @Inject
    EvidenceStager evidenceStager;

    @Inject
    AuditRunStore store = new AuditRunStore();

    @Inject
    AnalyzerSet analyzerSet = new AnalyzerSet();

    @Inject
    AuditPolicyResolver policies = new AuditPolicyResolver();

    @Inject
    ScoringService scoringService;

    /** Andamento mostrado na tela; o valor inicial serve a testes que montam o serviço sem CDI. */
    @Inject
    AuditProgressTracker progress = new AuditProgressTracker();

    /** Executor para a orquestração e para os analisadores (tarefas bloqueantes de I/O). */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /**
     * Dados mínimos da auditoria, lidos dentro de uma transação e seguros para uso fora dela.
     */
    record AuditContext(String repoUrl, String branch, Stack stack, String systemName, String profile, String baseRef,
                        String gitToken, String gitTokenHosts, Integer gitHistoryDepth, Long systemId,
                        SourceType sourceType, String localPath, LocalSnapshot localSnapshot) {

        boolean local() {
            return sourceType == SourceType.LOCAL_PATH;
        }

        /** Como a origem aparece para o usuário (mensagens e andamento): a URL ou a pasta local. */
        String sourceLabel() {
            return local() ? "pasta local " + localPath : repoUrl;
        }
    }

    /**
     * Inicia a auditoria somente depois do commit da transação que a criou.
     */
    void onAuditCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) AuditCreated event) {
        runAudit(event.auditRunId());
    }

    /**
     * Executa a auditoria de forma assíncrona para o {@link AuditRun} informado.
     * <p>
     * Fluxo:
     * <ol>
     *   <li>Atualiza status para RUNNING</li>
     *   <li>Clona o repositório</li>
     *   <li>Executa os analyzers em paralelo</li>
     *   <li>Persiste os findings</li>
     *   <li>Calcula e persiste o RiskScore</li>
     *   <li>Atualiza status para COMPLETED (ou FAILED em caso de erro)</li>
     *   <li>Remove o diretório clonado</li>
     * </ol>
     *
     * @param auditRunId identificador da execução de auditoria
     * @return {@link CompletableFuture} que completa quando a auditoria termina; em caso de
     *         falha, completa excepcionalmente (e o status da auditoria fica FAILED)
     */
    public CompletableFuture<Void> runAudit(Long auditRunId) {
        return CompletableFuture.runAsync(() -> executeAudit(auditRunId), executor)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOG.errorf(error, "Auditoria %d terminou com erro", auditRunId);
                    }
                });
    }

    /** Clona o repositório (ou copia a pasta local) e devolve onde ficou. */
    private Path fetchSource(Long auditRunId, AuditContext context) {
        progress.phase(auditRunId, AuditProgressTracker.CLONE,
                (context.local() ? "Copiando a " : "Clonando ") + context.sourceLabel() + " (" + context.branch() + ")");
        return context.local()
                ? ingestionService.snapshotLocal(context.localPath(), context.branch(), context.localSnapshot(), context.gitHistoryDepth())
                : ingestionService.cloneRepository(context.repoUrl(), context.branch(), context.gitToken(), context.gitTokenHosts(), context.gitHistoryDepth());
    }

    private ProjectConfig.Result applyProjectConfig(Long auditRunId, Path clonedPath, List<Finding> findings) {
        ProjectConfig.Result result = ProjectConfig.load(clonedPath).apply(findings, LocalDate.now());
        result.problems().forEach(problem -> LOG.warnf("Auditoria %d: %s", auditRunId, problem));
        return result;
    }

    /** Deduplica, aplica a política e o .sentinel.yml, marca o que é novo e avalia a confiança de cada achado. */
    private List<Finding> refineFindings(Long auditRunId, AuditContext context, Path clonedPath,
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

    private void executeAudit(Long auditRunId) {
        Path clonedPath = null;
        AuditPolicy policy = null;
        try {
            AuditContext context = store.markRunning(auditRunId);
            progress.queued(auditRunId);
            checkNotCancelled(auditRunId);
            clonedPath = fetchSource(auditRunId, context);
            checkNotCancelled(auditRunId);
            SourceSizer.Stats sizing = SourceSizer.stats(clonedPath);
            String commitSha = ingestionService.headCommit(clonedPath);
            progress.sourceStats(auditRunId, context.sourceLabel(), sizing.files(), sizing.lines());
            evidenceStager.stage(context.systemName(), context.local() ? context.localPath() : context.repoUrl(), clonedPath);

            // Referência leve apenas para os analisadores vincularem os findings;
            // o vínculo definitivo com a entidade gerenciada é refeito ao persistir.
            AuditRun runRef = new AuditRun();
            runRef.setId(auditRunId);

            policy = policies.forAudit(context.profile(), context.systemId());
            progress.phase(auditRunId, AuditProgressTracker.ANALISE, "Repositório clonado; iniciando a análise");
            AnalyzerRunner.Analysis analysis = new AnalyzerRunner(analyzerSet, progress, executor)
                    .run(clonedPath, runRef, context.stack(), policy);
            checkNotCancelled(auditRunId);
            List<Finding> allFindings = refineFindings(auditRunId, context, clonedPath, analysis, policy);
            long linesOfCode = sizing.lines();
            RiskScore riskScore = scoringService.calculate(FindingLifecycleService.active(allFindings), linesOfCode);
            progress.phase(auditRunId, AuditProgressTracker.RELATORIO, "Nota " + riskScore.getOverall() + "; gravando o relatório");
            store.persistResultsAndComplete(auditRunId, allFindings, riskScore, linesOfCode, commitSha);
            AuditExtras.saveSbom(store, auditRunId, context, clonedPath);
            AuditExtras.saveContext(store, auditRunId, clonedPath, analysis.runs());

            LOG.infof("Auditoria %d concluída: %d findings, score %s",
                    auditRunId, allFindings.size(), riskScore.getOverall());
            store.notifySafely(auditRunId);

        } catch (AuditCancelledException e) {
            LOG.infof("Auditoria %d cancelada pelo usuário", auditRunId);
            store.markCancelled(auditRunId);
        } catch (Exception e) {
            LOG.errorf(e, "Auditoria %d falhou: %s", auditRunId, e.getMessage());
            store.markFailed(auditRunId, e);
            store.notifySafely(auditRunId);
            throw new IllegalStateException("Auditoria " + auditRunId + " falhou", e);
        } finally {
            progress.finish(auditRunId);
            policies.cleanup(policy);
            if (clonedPath != null) {
                ingestionService.cleanupDirectory(clonedPath);
            }
        }
    }

    private void checkNotCancelled(Long auditRunId) {
        if (progress.isCancelRequested(auditRunId)) {
            throw new AuditCancelledException(auditRunId);
        }
    }

    /**
     * Cancela uma auditoria pendente ou em execução. O status muda na hora (a tela já mostra "Cancelada") e a
     * orquestração para na próxima verificação, descartando o que ainda estava sendo analisado.
     *
     * @return {@code false} se a auditoria não existe mais em andamento (já terminou ou nunca existiu)
     */
    public boolean cancel(Long auditRunId) {
        if (!store.markCancelledByUser(auditRunId)) {
            return false;
        }
        progress.requestCancel(auditRunId);
        return true;
    }

    /**
     * Auditoria de PR: mantém só achados em arquivos alterados em relação ao branch base. Se não for possível saber o
     * que mudou, mantém tudo (é o lado seguro: nada é escondido).
     */
    private List<Finding> onlyChangedFiles(List<Finding> findings, AuditContext context, Path clonedPath) {
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
