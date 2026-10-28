package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.AnalyzerResult;
import com.sentinel.audit.analyzer.CodeAnalyzer;
import com.sentinel.audit.analyzer.evidence.EvidenceAnalyzer;
import com.sentinel.audit.dto.AuditProgressDto;
import com.sentinel.audit.insights.AnalyzerRunInfo;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Escolhe os analisadores de uma auditoria, dispara os que valem em paralelo e espera cada um (atendendo ao
 * cancelamento). Uma falha individual vira resultado vazio com o erro anotado, nunca derruba a auditoria.
 */
final class AnalyzerRunner {

    private static final int PARTIAL_NOTE_MAX_CHARS = 300;
    private static final int ERROR_NOTE_MAX_CHARS = 200;


    private static final Logger LOG = Logger.getLogger(AnalyzerRunner.class);
    private static final long CANCEL_POLL_MILLIS = 500L;

    private final AnalyzerSet analyzerSet;
    private final AuditProgressTracker progress;
    private final ExecutorService executor;

    AnalyzerRunner(AnalyzerSet analyzerSet, AuditProgressTracker progress, ExecutorService executor) {
        this.analyzerSet = analyzerSet;
        this.progress = progress;
        this.executor = executor;
    }

    /**
     * Executa em paralelo os analisadores que suportam a stack, isolando falhas individuais.
     */
    Analysis run(Path projectPath, AuditRun runRef, Stack stack, AuditPolicy policy) {
        List<CodeAnalyzer> candidates = analyzerSet.all();
        // Analisador desligado para este sistema sai antes da escolha, para não "substituir" outro por engano.
        List<CodeAnalyzer> analyzers = selectAnalyzers(candidates.stream().filter(c -> !policy.analyzerDisabled(c.getName())).toList());

        List<AnalyzerRunInfo> runs = skippedBeforeRun(candidates, analyzers, policy);
        Long runId = runRef.getId();
        List<CodeAnalyzer> started = new ArrayList<>();
        List<CompletableFuture<Outcome>> futures = new ArrayList<>();
        List<AuditProgressDto.AnalyzerProgress> plan = new ArrayList<>();
        for (AnalyzerRunInfo r : runs) {
            plan.add(new AuditProgressDto.AnalyzerProgress(r.name(), r.status(), 0, 0, r.note()));
        }
        for (CodeAnalyzer analyzer : analyzers) {
            AnalyzerRunInfo skipped = skipReason(analyzer, stack, projectPath);
            if (skipped != null) {
                runs.add(skipped);
                plan.add(new AuditProgressDto.AnalyzerProgress(skipped.name(), skipped.status(), 0, 0, skipped.note()));
                continue;
            }
            started.add(analyzer);
            plan.add(new AuditProgressDto.AnalyzerProgress(analyzer.getName(), "ESPERANDO", 0, 0, null));
            futures.add(startAnalyzer(analyzer, projectPath, runRef, policy));
        }

        progress.planned(runId, plan);
        return collectOutcomes(runId, started, futures, runs);
    }

    /** Analisadores que nem chegam a rodar: desligados no sistema, sem a ferramenta ou cobertos por outro. */
    private static List<AnalyzerRunInfo> skippedBeforeRun(List<CodeAnalyzer> candidates, List<CodeAnalyzer> analyzers, AuditPolicy policy) {
        List<AnalyzerRunInfo> runs = new ArrayList<>();
        for (CodeAnalyzer c : candidates) {
            if (policy.analyzerDisabled(c.getName())) {
                runs.add(new AnalyzerRunInfo(c.getName(), "DESLIGADO", 0, 0, "desligado nos ajustes deste sistema"));
            } else if (!c.isAvailable()) {
                runs.add(new AnalyzerRunInfo(c.getName(), "INDISPONIVEL", 0, 0, "ferramenta ausente neste ambiente"));
            } else if (!analyzers.contains(c)) {
                runs.add(new AnalyzerRunInfo(c.getName(), "SUBSTITUIDO", 0, 0, "outro analisador cobre o mesmo"));
            }
        }
        return runs;
    }

    /** Motivo de um analisador escolhido não rodar nesta stack ou sem evidência; nulo se ele deve rodar. */
    private static AnalyzerRunInfo skipReason(CodeAnalyzer analyzer, Stack stack, Path projectPath) {
        if (stack != null && !analyzer.supports(stack)) {
            LOG.infof("Analisador %s ignorado: não suporta a stack %s", analyzer.getName(), stack);
            return new AnalyzerRunInfo(analyzer.getName(), "NAO_SUPORTADO", 0, 0, "não se aplica à stack " + stack);
        }
        if (analyzer instanceof EvidenceAnalyzer ev && !ev.hasEvidence(projectPath)) {
            return new AnalyzerRunInfo(analyzer.getName(), "SEM_EVIDENCIA", 0, 0,
                    "nenhum arquivo em " + EvidenceAnalyzer.DIR + " (dump do banco, estatísticas, cobertura ou histograma de memória)");
        }
        return null;
    }

    /** Dispara um analisador em paralelo; uma falha dele vira resultado vazio com o erro anotado. */
    private CompletableFuture<Outcome> startAnalyzer(CodeAnalyzer analyzer, Path projectPath, AuditRun runRef, AuditPolicy policy) {
        Long runId = runRef.getId();
        return CompletableFuture
                .supplyAsync(() -> {
                    progress.analyzerStarted(runId, analyzer.getName());
                    AnalyzerResult r = analyzer.analyze(projectPath, runRef, policy);
                    progress.analyzerFinished(runId, analyzer.getName(), r.partial() ? "PARCIAL" : "OK", r.findings().size(),
                            r.duration() == null ? 0 : r.duration().toMillis(),
                            r.partial() ? RunText.truncate(r.partialNote(), PARTIAL_NOTE_MAX_CHARS) : null, severityCounts(r.findings()));
                    return new Outcome(r, null);
                }, executor)
                .exceptionally(error -> {
                    LOG.errorf(error, "Analisador %s falhou; seguindo sem seus resultados", analyzer.getName());
                    progress.analyzerFinished(runId, analyzer.getName(), "FALHOU", 0, 0, RunText.truncate(error.getMessage(), ERROR_NOTE_MAX_CHARS));
                    return new Outcome(new AnalyzerResult(List.of(), analyzer.getName(), Duration.ZERO),
                            error.getMessage());
                });
    }

    /** Espera cada analisador na ordem em que foi disparado e junta achados e situação de cada um. */
    private Analysis collectOutcomes(Long runId, List<CodeAnalyzer> started, List<CompletableFuture<Outcome>> futures,
                                     List<AnalyzerRunInfo> runs) {
        List<Finding> allFindings = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Outcome outcome = awaitOutcome(runId, futures.get(i), futures);
            AnalyzerResult result = outcome.result();
            allFindings.addAll(result.findings());
            String status = outcome.error() != null ? "FALHOU" : result.partial() ? "PARCIAL" : "OK";
            String note = outcome.error() != null ? RunText.truncate(outcome.error(), ERROR_NOTE_MAX_CHARS)
                    : result.partial() ? RunText.truncate(result.partialNote(), PARTIAL_NOTE_MAX_CHARS) : null;
            runs.add(new AnalyzerRunInfo(started.get(i).getName(), status,
                    result.findings().size(), result.duration() == null ? 0 : result.duration().toMillis(), note));
        }
        return new Analysis(allFindings, runs);
    }

    /** Espera o analisador, mas acorda a cada meio segundo para atender um pedido de cancelamento. */
    private Outcome awaitOutcome(Long runId, CompletableFuture<Outcome> future, List<CompletableFuture<Outcome>> all) {
        while (true) {
            try {
                return future.get(CANCEL_POLL_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                progress.heartbeat(runId);
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

    /** Achados por severidade (nome da severidade → quantidade), para o resultado parcial na tela. */
    private static java.util.Map<String, Integer> severityCounts(List<Finding> findings) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Finding f : findings) {
            if (f.getSeverity() != null) {
                counts.merge(f.getSeverity().name(), 1, Integer::sum);
            }
        }
        return counts;
    }

    record Outcome(AnalyzerResult result, String error) {}

    record Analysis(List<Finding> findings, List<AnalyzerRunInfo> runs) {}

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
}
