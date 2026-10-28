package com.sentinel.audit.service;

import com.sentinel.audit.insights.AnalyzerRunInfo;
import com.sentinel.audit.insights.AuditContextData;
import com.sentinel.audit.insights.ProjectMap;
import com.sentinel.audit.insights.ProjectProfiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.analyzer.deps.DependencyExtractor;
import com.sentinel.audit.analyzer.deps.SbomWriter;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.model.RiskScore;
import com.sentinel.audit.model.SourceType;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.FindingRepository;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

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

    /** Tempo máximo de espera pelo commit do registro da auditoria. */
    private static final Duration RUN_VISIBILITY_TIMEOUT = Duration.ofSeconds(15);
    private static final long RUN_VISIBILITY_POLL_MILLIS = 200L;

    @Inject
    RuleQualityService ruleQualityService;

    @Inject
    IngestionService ingestionService;

    @Inject
    SecretBox secretBox;

    @Inject
    EvidenceStager evidenceStager;

    @Inject
    AnalyzerSet analyzerSet = new AnalyzerSet();

    @Inject
    AuditPolicyResolver policies = new AuditPolicyResolver();

    @Inject
    ScoringService scoringService;

    @Inject
    FindingLifecycleService lifecycle;

    @Inject
    NotificationService notifications;

    @Inject
    FindingRepository findingRepository;

    @Inject
    AuditRunRepository auditRunRepository;

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

    private void executeAudit(Long auditRunId) {
        Path clonedPath = null;
        AuditPolicy policy = null;
        try {
            AuditContext context = markRunning(auditRunId);
            progress.queued(auditRunId);
            checkNotCancelled(auditRunId);
            progress.phase(auditRunId, AuditProgressTracker.CLONE,
                    (context.local() ? "Copiando a " : "Clonando ") + context.sourceLabel() + " (" + context.branch() + ")");

            clonedPath = context.local()
                    ? ingestionService.snapshotLocal(context.localPath(), context.branch(), context.localSnapshot(), context.gitHistoryDepth())
                    : ingestionService.cloneRepository(context.repoUrl(), context.branch(), context.gitToken(), context.gitTokenHosts(), context.gitHistoryDepth());
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
            List<Finding> allFindings = policy.apply(FindingDeduplicator.apply(analysis.findings()));
            allFindings = onlyChangedFiles(allFindings, context, clonedPath);
            // O projeto pode declarar em .sentinel.yml o que não entra na nota (exclude) e o que aceitou (suppress).
            ProjectConfig.Result projectConfig = ProjectConfig.load(clonedPath).apply(allFindings, LocalDate.now());
            allFindings = projectConfig.findings();
            projectConfig.problems().forEach(problem -> LOG.warnf("Auditoria %d: %s", auditRunId, problem));

            // Marca o que é novo desde a auditoria anterior e retira do score o que foi suprimido.
            progress.phase(auditRunId, AuditProgressTracker.PONTUACAO,
                    "Análise concluída: " + allFindings.size() + " achados" + projectConfig.summary() + "; calculando a nota");
            FindingLifecycleService.apply(allFindings, loadBaselineSafely(auditRunId));
            FindingConfidence.assessAll(allFindings, loadRuleQualitySafely(), globalAuthOf(clonedPath),
                    GeneratedFiles.detect(clonedPath, allFindings));
            long linesOfCode = sizing.lines();
            RiskScore riskScore = scoringService.calculate(FindingLifecycleService.active(allFindings), linesOfCode);
            progress.phase(auditRunId, AuditProgressTracker.RELATORIO, "Nota " + riskScore.getOverall() + "; gravando o relatório");
            persistResultsAndComplete(auditRunId, allFindings, riskScore, linesOfCode, commitSha);
            saveSbomSafely(auditRunId, context, clonedPath);
            saveContextSafely(auditRunId, clonedPath, analysis.runs());

            LOG.infof("Auditoria %d concluída: %d findings, score %s",
                    auditRunId, allFindings.size(), riskScore.getOverall());
            notifySafely(auditRunId);

        } catch (AuditCancelledException e) {
            LOG.infof("Auditoria %d cancelada pelo usuário", auditRunId);
            markCancelled(auditRunId);
        } catch (Exception e) {
            LOG.errorf(e, "Auditoria %d falhou: %s", auditRunId, e.getMessage());
            markFailed(auditRunId, e);
            notifySafely(auditRunId);
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
        if (!markCancelledByUser(auditRunId)) {
            return false;
        }
        progress.requestCancel(auditRunId);
        return true;
    }

    @Transactional
    boolean markCancelledByUser(Long auditRunId) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null || (run.getStatus() != AuditStatus.PENDING && run.getStatus() != AuditStatus.RUNNING)) {
            return false;
        }
        run.setStatus(AuditStatus.CANCELLED);
        run.setCompletedAt(LocalDateTime.now());
        run.setErrorMessage("Cancelada pelo usuário");
        return true;
    }

    /** Garante o estado final quando a parada veio do pedido do usuário; nunca lança exceção. */
    void markCancelled(Long auditRunId) {
        try {
            markCancelledByUser(auditRunId);
        } catch (Exception e) {
            LOG.errorf(e, "Não foi possível marcar a auditoria %d como CANCELLED", auditRunId);
        }
    }

    /** O SBOM é um extra: se não der para gerar ou salvar, a auditoria continua COMPLETED. */
    private void saveSbomSafely(Long auditRunId, AuditContext context, Path projectPath) {
        try {
            String sbom = SbomWriter.toCycloneDx(context.systemName(), context.branch(),
                    DependencyExtractor.extract(projectPath), Instant.now(), UUID.randomUUID());
            storeSbom(auditRunId, sbom);
        } catch (RuntimeException e) {
            LOG.warnf("Não foi possível gerar/salvar o SBOM da auditoria %d: %s", auditRunId, e.getMessage());
        }
    }

    /** Mapa do projeto e situação dos analisadores: alimentam os insights; se falhar, a auditoria continua COMPLETED. */
    private void saveContextSafely(Long auditRunId, Path projectPath, List<AnalyzerRunInfo> runs) {
        try {
            ProjectMap map = ProjectProfiler.profile(projectPath);
            storeContext(auditRunId, new ObjectMapper().writeValueAsString(new AuditContextData(map, runs)));
        } catch (Exception e) {
            LOG.warnf("Não foi possível salvar o mapa do projeto da auditoria %d: %s", auditRunId, e.getMessage());
        }
    }

    @Transactional
    void storeContext(Long auditRunId, String json) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run != null) {
            run.setContextJson(json);
        }
    }

    @Transactional
    void storeSbom(Long auditRunId, String sbom) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run != null) {
            run.setSbomJson(sbom);
        }
    }

    /** O aviso é secundário: nada que dê errado nele pode mudar o resultado da auditoria. */
    private void notifySafely(Long auditRunId) {
        try {
            notifications.notifyFinished(auditRunId);
        } catch (RuntimeException e) {
            LOG.warnf("Falha ao notificar a auditoria %d: %s", auditRunId, e.getMessage());
        }
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

    /** Sem a qualidade das regras a confiança ainda sai (pelos demais sinais): nada aqui pode derrubar a auditoria. */
    /** Barreira de autenticação global do projeto (filtro, Spring Security, política do Quarkus), ou nulo. */
    private static String globalAuthOf(Path clonedPath) {
        return clonedPath == null ? null : com.sentinel.audit.analyzer.structure.GlobalAuth.detect(clonedPath);
    }

    private java.util.Map<String, RuleQualityService.RuleQuality> loadRuleQualitySafely() {
        try {
            return ruleQualityService.byRule();
        } catch (RuntimeException e) {
            LOG.warnf("Não foi possível carregar a qualidade das regras: %s", e.getMessage());
            return java.util.Map.of();
        }
    }

    /** Falha ao buscar a base de comparação não pode derrubar a auditoria: segue sem "novos" e sem supressões. */
    private FindingLifecycleService.Baseline loadBaselineSafely(Long auditRunId) {
        try {
            return lifecycle.loadBaseline(auditRunId);
        } catch (RuntimeException e) {
            LOG.warnf("Não foi possível carregar a base de comparação da auditoria %d: %s", auditRunId, e.getMessage());
            return FindingLifecycleService.Baseline.empty();
        }
    }

    /**
     * Marca a auditoria como RUNNING e devolve os dados necessários para o restante do fluxo.
     * <p>
     * O disparo da auditoria ocorre de dentro da transação que cria o registro; por isso, esta
     * thread pode rodar antes do commit. Aguarda (com limite de tempo) o registro ficar visível.
     */
    AuditContext markRunning(Long auditRunId) {
        long deadline = System.nanoTime() + RUN_VISIBILITY_TIMEOUT.toNanos();
        while (true) {
            AuditContext context = tryMarkRunning(auditRunId);
            if (context != null) {
                return context;
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(
                        "Auditoria " + auditRunId + " não encontrada no banco após "
                                + RUN_VISIBILITY_TIMEOUT.toSeconds() + "s");
            }
            try {
                Thread.sleep(RUN_VISIBILITY_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrompido aguardando a auditoria " + auditRunId, e);
            }
        }
    }

    @Transactional
    AuditContext tryMarkRunning(Long auditRunId) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null) {
            return null;
        }
        if (run.getStatus() == AuditStatus.CANCELLED) {
            throw new AuditCancelledException(auditRunId);
        }
        run.setStatus(AuditStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        // Leituras do sistema (lazy) acontecem aqui, dentro da transação.
        return new AuditContext(
                run.getSystem().getRepoUrl(),
                run.getBranch(),
                run.getSystem().getStack(),
                run.getSystem().getName(),
                run.getSystem().effectiveProfile(),
                run.getBaseRef(),
                secretBox.decrypt(run.getSystem().getGitTokenEnc()),
                run.getSystem().getGitTokenHosts(),
                run.getSystem().getGitHistoryDepth(),
                run.getSystem().getId(),
                run.getSystem().effectiveSourceType(),
                run.getSystem().getLocalPath(),
                run.getSystem().effectiveLocalSnapshot());
    }

    @Transactional
    void persistResultsAndComplete(Long auditRunId, List<Finding> findings, RiskScore riskScore, long linesOfCode, String commitSha) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null) {
            throw new IllegalStateException("Auditoria " + auditRunId + " não encontrada ao persistir resultados");
        }
        if (run.getStatus() == AuditStatus.CANCELLED) {
            // O usuário cancelou no último instante: nada do que foi analisado deve ser gravado.
            throw new AuditCancelledException(auditRunId);
        }

        for (Finding finding : findings) {
            RunText.fitColumns(finding);
            finding.setAuditRun(run);
            findingRepository.persist(finding);
        }

        run.setRiskScore(riskScore);
        run.setLinesOfCode(linesOfCode);
        run.setCommitSha(commitSha);
        run.setStatus(AuditStatus.COMPLETED);
        run.setCompletedAt(LocalDateTime.now());
    }

    /**
     * Marca a auditoria como FAILED. Nunca lança exceção: já estamos tratando uma falha
     * e não podemos mascará-la com outra.
     */
    void markFailed(Long auditRunId, Throwable cause) {
        try {
            updateStatusFailed(auditRunId, RunText.describe(cause));
        } catch (Exception e) {
            LOG.errorf(e, "Não foi possível marcar a auditoria %d como FAILED", auditRunId);
        }
    }

    @Transactional
    void updateStatusFailed(Long auditRunId, String reason) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run != null) {
            run.setStatus(AuditStatus.FAILED);
            run.setCompletedAt(LocalDateTime.now());
            run.setErrorMessage(RunText.error(reason));
        }
    }
}
