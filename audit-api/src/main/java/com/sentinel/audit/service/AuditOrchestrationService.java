package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.AnalyzerResult;
import com.sentinel.audit.analyzer.ArchitectureAnalyzer;
import com.sentinel.audit.analyzer.ArchitecturePolicyAnalyzer;
import com.sentinel.audit.analyzer.CleanCodeAnalyzer;
import com.sentinel.audit.analyzer.CodeAnalyzer;
import com.sentinel.audit.analyzer.EslintAnalyzer;
import com.sentinel.audit.analyzer.GitleaksAnalyzer;
import com.sentinel.audit.analyzer.HygieneAnalyzer;
import com.sentinel.audit.analyzer.evidence.EvidenceAnalyzer;
import com.sentinel.audit.dto.AuditProgressDto;
import com.sentinel.audit.insights.AnalyzerRunInfo;
import com.sentinel.audit.insights.AuditContextData;
import com.sentinel.audit.insights.ProjectMap;
import com.sentinel.audit.insights.ProjectProfiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.analyzer.PmdAnalyzer;
import com.sentinel.audit.analyzer.SarifAnalyzer;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.PackRegistry;
import com.sentinel.audit.analyzer.HistorySecretsAnalyzer;
import com.sentinel.audit.analyzer.OWASPDependencyAnalyzer;
import com.sentinel.audit.analyzer.SecretsAnalyzer;
import com.sentinel.audit.analyzer.SemgrepAnalyzer;
import com.sentinel.audit.analyzer.TrivyAnalyzer;
import com.sentinel.audit.analyzer.deps.DependencyExtractor;
import com.sentinel.audit.analyzer.deps.SbomWriter;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.RiskScore;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

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
    private static final long CANCEL_POLL_MILLIS = 500L;

    // Limites das colunas da entidade Finding
    private static final int MAX_TITLE        = 500;
    private static final int MAX_DESCRIPTION  = 2000;
    private static final int MAX_FILE_PATH    = 1000;
    private static final int MAX_SNIPPET      = 2000;
    private static final int MAX_RULE_ID      = 255;
    private static final int MAX_TOOL_SOURCE  = 100;
    private static final int MAX_CVE_ID       = 50;
    private static final int MAX_ERROR_MESSAGE = 1000;

    @Inject
    IngestionService ingestionService;

    @Inject
    SemgrepAnalyzer semgrepAnalyzer;

    @Inject
    OWASPDependencyAnalyzer dependencyAnalyzer;

    @Inject
    HistorySecretsAnalyzer historyAnalyzer;

    @Inject
    TrivyAnalyzer trivyAnalyzer;

    @Inject
    GitleaksAnalyzer gitleaksAnalyzer;

    @Inject
    ArchitectureAnalyzer architectureAnalyzer;

    @Inject
    CleanCodeAnalyzer cleanCodeAnalyzer;

    @Inject
    PmdAnalyzer pmdAnalyzer;

    @Inject
    EslintAnalyzer eslintAnalyzer;

    @Inject
    SarifAnalyzer sarifAnalyzer;

    @Inject
    ArchitecturePolicyAnalyzer architecturePolicyAnalyzer;

    @Inject
    HygieneAnalyzer hygieneAnalyzer;

    @Inject
    EvidenceAnalyzer evidenceAnalyzer;

    @Inject
    EvidenceStager evidenceStager;

    @Inject
    SecretsAnalyzer secretsAnalyzer;

    @Inject
    PackRegistry packRegistry;

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
    record AuditContext(String repoUrl, String branch, Stack stack, String systemName, String profile, String baseRef) {}

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
        try {
            AuditContext context = markRunning(auditRunId);
            progress.queued(auditRunId);
            checkNotCancelled(auditRunId);
            progress.phase(auditRunId, AuditProgressTracker.CLONE, "Clonando " + context.repoUrl() + " (" + context.branch() + ")");

            clonedPath = ingestionService.cloneRepository(context.repoUrl(), context.branch(), null);
            checkNotCancelled(auditRunId);
            evidenceStager.stage(context.systemName(), context.repoUrl(), clonedPath);

            // Referência leve apenas para os analisadores vincularem os findings;
            // o vínculo definitivo com a entidade gerenciada é refeito ao persistir.
            AuditRun runRef = new AuditRun();
            runRef.setId(auditRunId);

            AuditPolicy policy = resolvePolicy(context.profile());
            progress.phase(auditRunId, AuditProgressTracker.ANALISE, "Repositório clonado; iniciando a análise");
            Analysis analysis = runAnalyzers(clonedPath, runRef, context.stack(), policy);
            checkNotCancelled(auditRunId);
            List<Finding> allFindings = policy.apply(FindingDeduplicator.apply(analysis.findings()));
            allFindings = onlyChangedFiles(allFindings, context, clonedPath);

            // Marca o que é novo desde a auditoria anterior e retira do score o que foi suprimido.
            progress.phase(auditRunId, AuditProgressTracker.PONTUACAO, "Análise concluída: " + allFindings.size() + " achados; calculando a nota");
            FindingLifecycleService.apply(allFindings, loadBaselineSafely(auditRunId));
            long linesOfCode = SourceSizer.countLines(clonedPath);
            RiskScore riskScore = scoringService.calculate(FindingLifecycleService.active(allFindings), linesOfCode);
            progress.phase(auditRunId, AuditProgressTracker.RELATORIO, "Nota " + riskScore.getOverall() + "; gravando o relatório");
            persistResultsAndComplete(auditRunId, allFindings, riskScore, linesOfCode);
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
            if (clonedPath != null) {
                ingestionService.cleanupDirectory(clonedPath);
            }
        }
    }

    /**
     * Executa em paralelo os analisadores que suportam a stack, isolando falhas individuais.
     */
    private Analysis runAnalyzers(Path projectPath, AuditRun runRef, Stack stack, AuditPolicy policy) {
        List<CodeAnalyzer> candidates = Stream.of(
                semgrepAnalyzer, dependencyAnalyzer, architectureAnalyzer, secretsAnalyzer, historyAnalyzer,
                trivyAnalyzer, gitleaksAnalyzer, cleanCodeAnalyzer,
                pmdAnalyzer, eslintAnalyzer, sarifAnalyzer, architecturePolicyAnalyzer, hygieneAnalyzer,
                evidenceAnalyzer)
                .filter(Objects::nonNull).toList();
        List<CodeAnalyzer> analyzers = selectAnalyzers(candidates);

        List<AnalyzerRunInfo> runs = new ArrayList<>();
        for (CodeAnalyzer c : candidates) {
            if (!c.isAvailable()) {
                runs.add(new AnalyzerRunInfo(c.getName(), "INDISPONIVEL", 0, 0, "ferramenta ausente neste ambiente"));
            } else if (!analyzers.contains(c)) {
                runs.add(new AnalyzerRunInfo(c.getName(), "SUBSTITUIDO", 0, 0, "outro analisador cobre o mesmo"));
            }
        }

        Long runId = runRef.getId();
        List<CodeAnalyzer> started = new ArrayList<>();
        List<CompletableFuture<Outcome>> futures = new ArrayList<>();
        List<AuditProgressDto.AnalyzerProgress> plan = new ArrayList<>();
        for (AnalyzerRunInfo r : runs) {
            plan.add(new AuditProgressDto.AnalyzerProgress(r.name(), r.status(), 0, 0, r.note()));
        }
        for (CodeAnalyzer analyzer : analyzers) {
            if (stack != null && !analyzer.supports(stack)) {
                LOG.infof("Analisador %s ignorado: não suporta a stack %s", analyzer.getName(), stack);
                runs.add(new AnalyzerRunInfo(analyzer.getName(), "NAO_SUPORTADO", 0, 0, "não se aplica à stack " + stack));
                plan.add(new AuditProgressDto.AnalyzerProgress(analyzer.getName(), "NAO_SUPORTADO", 0, 0, "não se aplica à stack " + stack));
                continue;
            }
            if (analyzer instanceof EvidenceAnalyzer ev && !ev.hasEvidence(projectPath)) {
                runs.add(new AnalyzerRunInfo(analyzer.getName(), "SEM_EVIDENCIA", 0, 0,
                        "nenhum arquivo em " + EvidenceAnalyzer.DIR + " (dump do banco, estatísticas, cobertura ou histograma de memória)"));
                plan.add(new AuditProgressDto.AnalyzerProgress(analyzer.getName(), "SEM_EVIDENCIA", 0, 0,
                        "nenhum arquivo em " + EvidenceAnalyzer.DIR));
                continue;
            }
            started.add(analyzer);
            plan.add(new AuditProgressDto.AnalyzerProgress(analyzer.getName(), "ESPERANDO", 0, 0, null));
            futures.add(CompletableFuture
                    .supplyAsync(() -> {
                        progress.analyzerStarted(runId, analyzer.getName());
                        AnalyzerResult r = analyzer.analyze(projectPath, runRef, policy);
                        progress.analyzerFinished(runId, analyzer.getName(), "OK", r.findings().size(),
                                r.duration() == null ? 0 : r.duration().toMillis(), null);
                        return new Outcome(r, null);
                    }, executor)
                    .exceptionally(error -> {
                        LOG.errorf(error, "Analisador %s falhou; seguindo sem seus resultados", analyzer.getName());
                        progress.analyzerFinished(runId, analyzer.getName(), "FALHOU", 0, 0, truncate(error.getMessage(), 200));
                        return new Outcome(new AnalyzerResult(List.of(), analyzer.getName(), Duration.ZERO),
                                error.getMessage());
                    }));
        }

        progress.planned(runId, plan);
        List<Finding> allFindings = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Outcome outcome = awaitOutcome(runId, futures.get(i), futures);
            AnalyzerResult result = outcome.result();
            allFindings.addAll(result.findings());
            runs.add(new AnalyzerRunInfo(started.get(i).getName(), outcome.error() == null ? "OK" : "FALHOU",
                    result.findings().size(), result.duration() == null ? 0 : result.duration().toMillis(),
                    outcome.error() == null ? null : truncate(outcome.error(), 200)));
        }
        return new Analysis(allFindings, runs);
    }

    /** Espera o analisador, mas acorda a cada meio segundo para atender um pedido de cancelamento. */
    private Outcome awaitOutcome(Long runId, CompletableFuture<Outcome> future, List<CompletableFuture<Outcome>> all) {
        while (true) {
            try {
                return future.get(CANCEL_POLL_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                if (progress.isCancelRequested(runId)) {
                    all.forEach(f -> f.cancel(true));
                    throw new AuditCancelledException(runId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrompido aguardando os analisadores da auditoria " + runId, e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException("Falha inesperada aguardando um analisador", e.getCause());
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

    private record Outcome(AnalyzerResult result, String error) {}

    private record Analysis(List<Finding> findings, List<AnalyzerRunInfo> runs) {}

    /** Perfil apagado/renomeado depois de cadastrado não pode derrubar a auditoria: cai no perfil padrão, com aviso. */
    AuditPolicy resolvePolicy(String profile) {
        try {
            return packRegistry.policyFor(profile);
        } catch (IllegalArgumentException e) {
            LOG.warnf("%s; usando o perfil padrão", e.getMessage());
            return packRegistry.policyFor(null);
        }
    }

    /**
     * Mantém só os analisadores disponíveis e retira os que outro, também disponível, já cobre por inteiro
     * (ex.: com o Gitleaks no PATH, os analisadores internos de segredos não rodam).
     */
    static List<CodeAnalyzer> selectAnalyzers(List<CodeAnalyzer> all) {
        List<CodeAnalyzer> available = all.stream().filter(CodeAnalyzer::isAvailable).toList();
        List<CodeAnalyzer> selected = new ArrayList<>();
        for (CodeAnalyzer a : available) {
            boolean replaced = available.stream().anyMatch(other -> other != a
                    && other.replaces().stream().anyMatch(type -> type.isInstance(a)));
            if (replaced) {
                LOG.infof("Analisador %s não será executado: outro disponível já cobre o que ele faz", a.getName());
            } else {
                selected.add(a);
            }
        }
        return selected;
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
        ChangedFiles.Result changed = ingestionService.changedFiles(context.repoUrl(), clonedPath, context.baseRef());
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
                run.getBaseRef());
    }

    @Transactional
    void persistResultsAndComplete(Long auditRunId, List<Finding> findings, RiskScore riskScore, long linesOfCode) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null) {
            throw new IllegalStateException("Auditoria " + auditRunId + " não encontrada ao persistir resultados");
        }
        if (run.getStatus() == AuditStatus.CANCELLED) {
            // O usuário cancelou no último instante: nada do que foi analisado deve ser gravado.
            throw new AuditCancelledException(auditRunId);
        }

        for (Finding finding : findings) {
            truncateToColumnLimits(finding);
            finding.setAuditRun(run);
            findingRepository.persist(finding);
        }

        run.setRiskScore(riskScore);
        run.setLinesOfCode(linesOfCode);
        run.setStatus(AuditStatus.COMPLETED);
        run.setCompletedAt(LocalDateTime.now());
    }

    /**
     * Marca a auditoria como FAILED. Nunca lança exceção: já estamos tratando uma falha
     * e não podemos mascará-la com outra.
     */
    void markFailed(Long auditRunId, Throwable cause) {
        try {
            updateStatusFailed(auditRunId, describe(cause));
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
            run.setErrorMessage(truncate(reason, MAX_ERROR_MESSAGE));
        }
    }

    /** Resume a causa raiz da exceção em uma frase curta, sem stack trace nem segredos. */
    private static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        return root.getClass().getSimpleName() + ": " + message;
    }

    /**
     * Garante que nenhum texto estoure o tamanho da coluna — um único valor longo
     * (ex.: mensagem do Semgrep) faria a transação inteira falhar.
     */
    private void truncateToColumnLimits(Finding f) {
        f.setTitle(truncate(f.getTitle(), MAX_TITLE));
        f.setDescription(truncate(f.getDescription(), MAX_DESCRIPTION));
        f.setFilePath(truncate(f.getFilePath(), MAX_FILE_PATH));
        f.setCodeSnippet(truncate(f.getCodeSnippet(), MAX_SNIPPET));
        f.setRuleId(truncate(f.getRuleId(), MAX_RULE_ID));
        f.setToolSource(truncate(f.getToolSource(), MAX_TOOL_SOURCE));
        f.setCveId(truncate(f.getCveId(), MAX_CVE_ID));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }
}
