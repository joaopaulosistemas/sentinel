package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.AnalyzerResult;
import com.sentinel.audit.analyzer.ArchitectureAnalyzer;
import com.sentinel.audit.analyzer.OWASPDependencyAnalyzer;
import com.sentinel.audit.analyzer.SecretsAnalyzer;
import com.sentinel.audit.analyzer.SemgrepAnalyzer;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.FindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do fluxo de orquestração com dublês simples (sem CDI/banco).
 */
class AuditOrchestrationServiceTest {

    @TempDir
    Path tempDir;

    private AuditOrchestrationService service;
    private AuditRun run;
    private final List<Finding> persisted = new ArrayList<>();
    private final List<Path> cleaned = new ArrayList<>();
    private final AtomicInteger architectureCalls = new AtomicInteger();
    private final AtomicInteger findByIdCalls = new AtomicInteger();
    private int invisibleCalls = 0;
    private RuntimeException cloneError;
    private RuntimeException secretsError;
    private FindingLifecycleService.Baseline baseline = FindingLifecycleService.Baseline.empty();
    private RuntimeException baselineError;
    private final List<Long> notified = new ArrayList<>();
    private RuntimeException notifyError;

    @BeforeEach
    void setUp() {
        AuditedSystem system = new AuditedSystem();
        system.setRepoUrl("https://github.com/org/repo.git");
        system.setStack(Stack.JAVA);
        run = new AuditRun();
        run.setId(7L);
        run.setBranch("main");
        run.setSystem(system);
        run.setStatus(AuditStatus.PENDING);

        service = new AuditOrchestrationService();
        service.scoringService = new ScoringService();
        service.evidenceStager = new EvidenceStager();
        service.evidenceAnalyzer = new com.sentinel.audit.analyzer.evidence.EvidenceAnalyzer();
        service.packRegistry = com.sentinel.audit.pack.TestPacks.registry();

        service.auditRunRepository = new AuditRunRepository() {
            @Override
            public AuditRun findById(Long id) {
                findByIdCalls.incrementAndGet();
                if (invisibleCalls > 0) {
                    invisibleCalls--;
                    return null; // registro ainda não commitado
                }
                return run;
            }
        };
        service.findingRepository = new FindingRepository() {
            @Override
            public void persist(Finding f) {
                persisted.add(f);
            }
        };
        service.lifecycle = new FindingLifecycleService() {
            @Override
            public Baseline loadBaseline(Long auditRunId) {
                if (baselineError != null) {
                    throw baselineError;
                }
                return baseline;
            }
        };
        service.notifications = new NotificationService() {
            @Override
            public void notifyFinished(Long auditRunId) {
                notified.add(auditRunId);
                if (notifyError != null) {
                    throw notifyError;
                }
            }
        };
        service.ingestionService = new IngestionService() {
            @Override
            public Path cloneRepository(String repoUrl, String branch, String token) {
                if (cloneError != null) {
                    throw cloneError;
                }
                return tempDir;
            }

            @Override
            public void cleanupDirectory(Path dir) {
                cleaned.add(dir);
            }
        };
        service.semgrepAnalyzer = new SemgrepAnalyzer() {
            @Override
            public AnalyzerResult analyze(Path p, AuditRun r, com.sentinel.audit.pack.AuditPolicy policy) {
                return result("Semgrep", finding(Category.SECURITY, Severity.HIGH, "x".repeat(600), "y".repeat(3000)));
            }
        };
        service.dependencyAnalyzer = new OWASPDependencyAnalyzer() {
            @Override
            public AnalyzerResult analyze(Path p, AuditRun r) {
                return result("OWASP-Dependency", finding(Category.DEPENDENCY, Severity.CRITICAL, "dep", "d"));
            }
        };
        service.architectureAnalyzer = new ArchitectureAnalyzer() {
            @Override
            public AnalyzerResult analyze(Path p, AuditRun r) {
                architectureCalls.incrementAndGet();
                return result("Architecture", finding(Category.ARCHITECTURE, Severity.LOW, "arch", "a"));
            }
        };
        service.secretsAnalyzer = new SecretsAnalyzer() {
            @Override
            public AnalyzerResult analyze(Path p, AuditRun r) {
                if (secretsError != null) {
                    throw secretsError;
                }
                return result("Secrets", finding(Category.SECURITY, Severity.CRITICAL, "secret", "s"));
            }
        };
    }

    private static AnalyzerResult result(String name, Finding... findings) {
        return new AnalyzerResult(List.of(findings), name, Duration.ZERO);
    }

    private static Finding finding(Category c, Severity s, String title, String description) {
        Finding f = new Finding();
        f.setCategory(c);
        f.setSeverity(s);
        f.setTitle(title);
        f.setDescription(description);
        f.setCveId("CWE-89: " + "z".repeat(100));   // estoura a coluna de 50 caracteres
        f.setRuleId("rule");
        return f;
    }

    @Test
    void auditoriaCompletaPersisteFindingsEAtualizaStatus() {
        service.runAudit(7L).join();

        assertEquals(AuditStatus.COMPLETED, run.getStatus());
        assertNotNull(run.getRiskScore());
        assertNotNull(run.getCompletedAt());
        assertEquals(4, persisted.size());
        assertTrue(persisted.stream().allMatch(f -> f.getAuditRun() == run), "findings ligados à entidade gerenciada");
        assertEquals(List.of(tempDir), cleaned, "o clone deve ser limpo");
    }

    @Test
    void camposLongosSaoTruncadosParaCaberNasColunas() {
        service.runAudit(7L).join();

        for (Finding f : persisted) {
            assertTrue(f.getTitle().length() <= 500);
            assertTrue(f.getDescription().length() <= 2000);
            assertTrue(f.getCveId().length() <= 50);
        }
        Finding semgrep = persisted.stream().filter(f -> f.getTitle().startsWith("xxx")).findFirst().get();
        assertEquals(500, semgrep.getTitle().length());
        assertTrue(semgrep.getTitle().endsWith("..."));
    }

    @Test
    void soExecutaAnalisadoresQueSuportamAStack() {
        run.getSystem().setStack(Stack.COBOL);    // Architecture e OWASP não suportam COBOL
        service.runAudit(7L).join();

        assertEquals(0, architectureCalls.get());
        assertEquals(2, persisted.size());        // Semgrep + Secrets
    }

    @Test
    void falhaDeUmAnalisadorNaoDerrubaAAuditoria() {
        secretsError = new IllegalStateException("regex explodiu");
        service.runAudit(7L).join();

        assertEquals(AuditStatus.COMPLETED, run.getStatus());
        assertEquals(3, persisted.size());
    }

    @Test
    void falhaNoCloneMarcaFailedComMotivoELimpaTudo() {
        cloneError = new IllegalStateException("git clone falhou (exit 128): repository not found");

        CompletionException error = assertThrows(CompletionException.class, () -> service.runAudit(7L).join());

        assertNotNull(error.getCause());
        assertEquals(AuditStatus.FAILED, run.getStatus());
        assertNotNull(run.getCompletedAt());
        assertTrue(run.getErrorMessage().contains("repository not found"), run.getErrorMessage());
        assertTrue(persisted.isEmpty());
    }

    @Test
    void aguardaORegistroFicarVisivelAposOCommit() {
        invisibleCalls = 3;   // as 3 primeiras leituras ainda não enxergam a auditoria
        service.runAudit(7L).join();

        assertEquals(AuditStatus.COMPLETED, run.getStatus());
        assertTrue(findByIdCalls.get() >= 4);
    }

    @Test
    void naoUsaEntidadeLazyForaDaTransacao() {
        // O contexto devolvido por tryMarkRunning contém só valores simples (String/Stack),
        // lidos enquanto a transação ainda está aberta — por isso o fluxo não depende de sessão do Hibernate.
        AuditOrchestrationService.AuditContext ctx = service.tryMarkRunning(7L);
        assertEquals("https://github.com/org/repo.git", ctx.repoUrl());
        assertEquals("main", ctx.branch());
        assertEquals(Stack.JAVA, ctx.stack());
        assertEquals(AuditStatus.RUNNING, run.getStatus());
    }

    private static String fingerprintOfFake(Category c, Severity sev, String title) {
        return Fingerprints.compute(finding(c, sev, title, "x"));
    }

    @Test
    void marcaNovosEmRelacaoAAuditoriaAnterior() {
        baseline = new FindingLifecycleService.Baseline(3L,
                Set.of(fingerprintOfFake(Category.SECURITY, Severity.CRITICAL, "secret")), Set.of());
        service.runAudit(7L).join();

        Finding secret = persisted.stream().filter(f -> f.getTitle().equals("secret")).findFirst().get();
        assertEquals(Boolean.FALSE, secret.getIsNew());                    // já existia
        assertTrue(persisted.stream().filter(f -> f != secret).allMatch(f -> Boolean.TRUE.equals(f.getIsNew())));
        assertTrue(persisted.stream().allMatch(f -> f.getFingerprint() != null && f.getFingerprint().length() == 64));
    }

    @Test
    void primeiraAuditoriaNaoMarcaNinguemComoNovo() {
        service.runAudit(7L).join();
        assertTrue(persisted.stream().allMatch(f -> f.getIsNew() == null));
    }

    @Test
    void findingSuprimidoFicaForaDoScore() {
        service.runAudit(7L).join();
        double antes = run.getRiskScore().getSecurity();
        persisted.clear();

        baseline = new FindingLifecycleService.Baseline(null, Set.of(),
                Set.of(fingerprintOfFake(Category.SECURITY, Severity.CRITICAL, "secret")));
        service.runAudit(7L).join();

        Finding secret = persisted.stream().filter(f -> f.getTitle().equals("secret")).findFirst().get();
        assertEquals(FindingStatus.SUPPRESSED, secret.getStatus());
        assertEquals(4, persisted.size());                                  // continua gravado (rastreabilidade)
        assertTrue(run.getRiskScore().getSecurity() > antes, "score sobe ao suprimir um CRITICAL");
    }

    @Test
    void falhaAoCarregarABaseNaoDerrubaAAuditoria() {
        baselineError = new IllegalStateException("banco indisponível");
        service.runAudit(7L).join();

        assertEquals(AuditStatus.COMPLETED, run.getStatus());
        assertEquals(4, persisted.size());
    }

    @Test
    void notificaAoConcluirEAoFalhar() {
        service.runAudit(7L).join();
        assertEquals(List.of(7L), notified);

        notified.clear();
        cloneError = new IllegalStateException("repository not found");
        assertThrows(CompletionException.class, () -> service.runAudit(7L).join());
        assertEquals(List.of(7L), notified);
        assertEquals(AuditStatus.FAILED, run.getStatus());
    }

    @Test
    void falhaNaNotificacaoNaoAlteraOResultadoDaAuditoria() {
        notifyError = new IllegalStateException("webhook fora do ar");
        service.runAudit(7L).join();

        assertEquals(AuditStatus.COMPLETED, run.getStatus());   // continua concluída
        assertNull(run.getErrorMessage());
    }

    @Test
    void cancelarDuranteOCloneParaAntesDaAnaliseESemGravarNada() {
        service.ingestionService = new IngestionService() {
            @Override
            public Path cloneRepository(String repoUrl, String branch, String token) {
                assertTrue(service.cancel(7L));   // o usuário clica em cancelar enquanto clona
                return tempDir;
            }

            @Override
            public void cleanupDirectory(Path dir) {
                cleaned.add(dir);
            }
        };

        service.runAudit(7L).join();               // cancelar não é falha: o future termina normalmente

        assertEquals(AuditStatus.CANCELLED, run.getStatus());
        assertEquals("Cancelada pelo usuário", run.getErrorMessage());
        assertNotNull(run.getCompletedAt());
        assertTrue(persisted.isEmpty());
        assertNull(run.getRiskScore());
        assertEquals(List.of(tempDir), cleaned, "o clone deve ser limpo");
        assertTrue(notified.isEmpty(), "cancelamento não dispara aviso de conclusão");
        assertFalse(service.progress.isCancelRequested(7L), "o pedido é limpo ao terminar");
    }

    @Test
    void cancelarDuranteAAnaliseDescartaOResultado() throws Exception {
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        service.semgrepAnalyzer = new SemgrepAnalyzer() {
            @Override
            public AnalyzerResult analyze(Path p, AuditRun r, com.sentinel.audit.pack.AuditPolicy policy) {
                started.countDown();
                try {
                    release.await(20, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return result("Semgrep");
            }
        };

        var future = service.runAudit(7L);
        assertTrue(started.await(10, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(service.cancel(7L));
        future.get(10, java.util.concurrent.TimeUnit.SECONDS);   // para em até ~0,5 s, sem esperar o analisador lento
        release.countDown();

        assertEquals(AuditStatus.CANCELLED, run.getStatus());
        assertTrue(persisted.isEmpty());
        assertEquals(List.of(tempDir), cleaned);
    }

    @Test
    void naoCancelaAuditoriaQueJaTerminou() {
        service.runAudit(7L).join();
        assertEquals(AuditStatus.COMPLETED, run.getStatus());

        assertFalse(service.cancel(7L));
        assertEquals(AuditStatus.COMPLETED, run.getStatus());
        assertFalse(service.progress.isCancelRequested(7L));
    }

    @Test
    void auditoriaCanceladaAntesDeComecarNaoVoltaARodar() {
        run.setStatus(AuditStatus.CANCELLED);

        service.runAudit(7L).join();

        assertEquals(AuditStatus.CANCELLED, run.getStatus());
        assertTrue(persisted.isEmpty());
        assertTrue(cleaned.isEmpty(), "nem chegou a clonar");
    }
}
