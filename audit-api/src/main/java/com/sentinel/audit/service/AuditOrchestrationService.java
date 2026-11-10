package com.sentinel.audit.service;

import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    /** Quantas auditorias rodam ao mesmo tempo; as demais esperam em PENDING. */
    @ConfigProperty(name = "sentinel.audit.max-concurrent", defaultValue = "2")
    int maxConcurrent = 2;

    private AuditSlots slots;

    private synchronized AuditSlots slots() {
        if (slots == null) {
            slots = new AuditSlots(maxConcurrent);
        }
        return slots;
    }

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
                ? ingestionService.snapshotLocal(context.localPath(), context.branch(), context.localSnapshot(), context.gitHistoryDepth(),
                        (done, total) -> progress.copyProgress(auditRunId, done, total))
                : ingestionService.cloneRepository(context.repoUrl(), context.branch(), context.gitToken(), context.gitTokenHosts(), context.gitHistoryDepth());
    }

    /** O que a execução de uma auditoria precisa liberar no fim, aconteça o que acontecer. */
    private static final class RunState {
        Path clonedPath;
        AuditPolicy policy;
        boolean holdingSlot;
    }

    private void executeAudit(Long auditRunId) {
        RunState state = new RunState();
        try {
            runPipeline(auditRunId, state);
        } catch (AuditCancelledException e) {
            LOG.infof("Auditoria %d cancelada pelo usuário", auditRunId);
            store.markCancelled(auditRunId);
        } catch (Exception e) {
            LOG.errorf(e, "Auditoria %d falhou: %s", auditRunId, e.getMessage());
            store.markFailed(auditRunId, e);
            store.notifySafely(auditRunId);
            throw new IllegalStateException("Auditoria " + auditRunId + " falhou", e);
        } finally {
            release(auditRunId, state);
        }
    }

    private void release(Long auditRunId, RunState state) {
        if (state.holdingSlot) {
            slots().release();
        }
        progress.finish(auditRunId);
        policies.cleanup(state.policy);
        if (state.clonedPath != null) {
            ingestionService.cleanupDirectory(state.clonedPath);
        }
    }

    private void runPipeline(Long auditRunId, RunState state) throws Exception {
        slots().acquire(auditRunId, () -> progress.isCancelRequested(auditRunId));
        state.holdingSlot = true;
        AuditContext context = store.markRunning(auditRunId);
        progress.queued(auditRunId);
        checkNotCancelled(auditRunId);
        state.clonedPath = fetchSource(auditRunId, context);
        checkNotCancelled(auditRunId);
        Path clonedPath = state.clonedPath;
        SourceSizer.Stats sizing = SourceSizer.stats(clonedPath);
        String commitSha = ingestionService.headCommit(clonedPath);
        progress.sourceStats(auditRunId, context.sourceLabel(), sizing.files(), sizing.lines());
        evidenceStager.stage(context.systemName(), context.local() ? context.localPath() : context.repoUrl(), clonedPath);

        // Referência leve apenas para os analisadores vincularem os findings;
        // o vínculo definitivo com a entidade gerenciada é refeito ao persistir.
        AuditRun runRef = new AuditRun();
        runRef.setId(auditRunId);

        state.policy = policies.forAudit(context.profile(), context.systemId());
        progress.phase(auditRunId, AuditProgressTracker.ANALISE, "Repositório clonado; iniciando a análise");
        AnalyzerRunner.Analysis analysis = new AnalyzerRunner(analyzerSet, progress, executor)
                .run(clonedPath, runRef, context.stack(), state.policy);
        checkNotCancelled(auditRunId);
        List<Finding> allFindings = new FindingRefinement(store, progress, ingestionService)
                .refine(auditRunId, context, clonedPath, analysis, state.policy);
        persistAndNotify(auditRunId, new AuditResult(context, clonedPath, sizing, commitSha, analysis, allFindings));
    }

    /** O que a auditoria produziu, pronto para gravar: achados, nota, SBOM e contexto. */
    private record AuditResult(AuditContext context, Path clonedPath, SourceSizer.Stats sizing, String commitSha,
                               AnalyzerRunner.Analysis analysis, List<Finding> findings) { }

    private void persistAndNotify(Long auditRunId, AuditResult result) {
        long linesOfCode = result.sizing().lines();
        RiskScore riskScore = scoringService.calculate(FindingLifecycleService.active(result.findings()), linesOfCode);
        progress.phase(auditRunId, AuditProgressTracker.RELATORIO, "Nota " + riskScore.getOverall() + "; gravando o relatório");
        store.persistResultsAndComplete(auditRunId, result.findings(), riskScore, linesOfCode, result.commitSha());
        AuditExtras.saveSbom(store, auditRunId, result.context(), result.clonedPath());
        AuditExtras.saveContext(store, auditRunId, result.clonedPath(), result.analysis().runs());

        LOG.infof("Auditoria %d concluída: %d findings, score %s",
                auditRunId, result.findings().size(), riskScore.getOverall());
        store.notifySafely(auditRunId);
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
}
