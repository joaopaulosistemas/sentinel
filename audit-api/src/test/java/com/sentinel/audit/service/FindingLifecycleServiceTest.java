package com.sentinel.audit.service;

import com.sentinel.audit.pack.RemediationCatalog;
import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.pack.RemediationDto;
import com.sentinel.audit.dto.SuppressRequest;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.*;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.repository.FindingSuppressionRepository;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Ciclo de vida dos findings com repositórios em memória (sem banco). */
class FindingLifecycleServiceTest {

    private final Map<Long, AuditRun> runs = new LinkedHashMap<>();
    private final Map<Long, List<Finding>> findingsByRun = new HashMap<>();
    private final Map<Long, Finding> findingsById = new HashMap<>();
    private final List<FindingSuppression> suppressions = new ArrayList<>();
    private FindingLifecycleService service;
    private AuditedSystem system;
    private long nextId = 100;

    @BeforeEach
    void setUp() {
        system = new AuditedSystem();
        system.setId(1L);
        service = new FindingLifecycleService();
        service.scoringService = new ScoringService();
        service.auditRunRepository = new AuditRunRepository() {
            @Override public AuditRun findById(Long id) { return runs.get(id); }
            @Override public AuditRun findPreviousCompleted(Long sid, String branch, Long before) {
                return runs.values().stream()
                        .filter(r -> r.getId() < before && r.getStatus() == AuditStatus.COMPLETED && Objects.equals(r.getBranch(), branch))
                        .max(Comparator.comparing(AuditRun::getId)).orElse(null);
            }
            @Override public List<AuditRun> findLatestCompleted(Long sid, int limit) {
                return runs.values().stream().filter(r -> r.getStatus() == AuditStatus.COMPLETED)
                        .sorted(Comparator.comparing(AuditRun::getId).reversed()).limit(limit).toList();
            }
        };
        service.findingRepository = new FindingRepository() {
            @Override public Finding findById(Long id) { return findingsById.get(id); }
            @Override public List<Finding> findByAuditRunId(Long id) { return findingsByRun.getOrDefault(id, List.of()); }
            @Override public Map<Severity, Long> countBySeverityForRun(Long id) {
                Map<Severity, Long> m = new EnumMap<>(Severity.class);
                for (Severity s : Severity.values()) m.put(s, 0L);
                findingsByRun.getOrDefault(id, List.of()).stream().filter(f -> f.getStatus() != FindingStatus.SUPPRESSED)
                        .forEach(f -> m.merge(f.getSeverity(), 1L, Long::sum));
                return m;
            }
            @Override public Map<Long, Map<Severity, Long>> countBySeverityForRuns(java.util.Collection<Long> ids) {
                Map<Long, Map<Severity, Long>> out = new java.util.LinkedHashMap<>();
                for (Long id : ids) out.put(id, countBySeverityForRun(id));
                return out;
            }
        };
        service.suppressionRepository = new FindingSuppressionRepository() {
            @Override public Optional<FindingSuppression> findBySystemAndFingerprint(Long sid, String fp) {
                return suppressions.stream().filter(s -> s.getFingerprint().equals(fp)).findFirst();
            }
            @Override public void persist(FindingSuppression s) { s.setId(nextId++); suppressions.add(s); }
            @Override public FindingSuppression findById(Long id) {
                return suppressions.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
            }
            @Override public void delete(FindingSuppression s) { suppressions.remove(s); }
            @Override public List<FindingSuppression> findActiveBySystemId(Long sid, LocalDateTime now) {
                return suppressions.stream().filter(s -> s.isActive(now)).toList();
            }
            @Override public List<FindingSuppression> findBySystemId(Long sid) { return List.copyOf(suppressions); }
        };
    }

    // ------------------------------------------------------------------ helpers

    private AuditRun run(long id, String branch, AuditStatus status, Finding... findings) {
        AuditRun r = new AuditRun();
        r.setId(id);
        r.setBranch(branch);
        r.setSystem(system);
        r.setStatus(status);
        r.setCompletedAt(LocalDateTime.now().minusDays(10 - id));
        runs.put(id, r);
        List<Finding> list = new ArrayList<>(List.of(findings));
        for (Finding f : list) {
            f.setAuditRun(r);
            f.setFingerprint(Fingerprints.compute(f));
            f.setStatus(FindingStatus.OPEN);
        }
        findingsByRun.put(id, list);
        r.setRiskScore(new ScoringService().calculate(list));
        return r;
    }

    private Finding finding(long id, String rule, String file, String snippet, Severity sev) {
        Finding f = new Finding();
        f.setId(id);
        f.setRuleId(rule);
        f.setFilePath(file);
        f.setCodeSnippet(snippet);
        f.setTitle(rule + " em " + file);
        f.setCategory(Category.SECURITY);
        f.setSeverity(sev);
        findingsById.put(id, f);
        return f;
    }

    private static SuppressRequest req(LocalDateTime expires) {
        return new SuppressRequest(SuppressionReason.FALSE_POSITIVE, "Valor de teste, não é credencial real", "ana@sentinel", expires);
    }

    // ------------------------------------------------------------------ fingerprint

    @Test
    void fingerprintIgnoraNumeroDaLinhaEEspacos() {
        Finding a = finding(1, "r", "A.java", "  String   x = \"a\";", Severity.HIGH);
        a.setLineNumber(10);
        Finding b = finding(2, "r", "A.java", "String x = \"a\";", Severity.HIGH);
        b.setLineNumber(250);
        assertEquals(Fingerprints.compute(a), Fingerprints.compute(b));

        Finding outroArquivo = finding(3, "r", "B.java", "String x = \"a\";", Severity.HIGH);
        assertNotEquals(Fingerprints.compute(a), Fingerprints.compute(outroArquivo));
    }

    @Test
    void fingerprintDeDependenciaNaoMudaComAVersao() {
        Finding v1 = finding(1, "osv-g:lib", "pom.xml", null, Severity.HIGH);
        v1.setCategory(Category.DEPENDENCY);
        v1.setTitle("Dependência vulnerável: g:lib 1.0");
        Finding v2 = finding(2, "osv-g:lib", "pom.xml", null, Severity.HIGH);
        v2.setCategory(Category.DEPENDENCY);
        v2.setTitle("Dependência vulnerável: g:lib 1.1 (3 vulnerabilidades)");
        assertEquals(Fingerprints.compute(v1), Fingerprints.compute(v2));
    }

    // ------------------------------------------------------------------ baseline / apply

    @Test
    void baselineVemDaAuditoriaConcluidaAnteriorDaMesmaBranch() {
        run(1, "main", AuditStatus.COMPLETED, finding(10, "r1", "A.java", "x", Severity.HIGH));
        run(2, "dev", AuditStatus.COMPLETED, finding(11, "r2", "A.java", "y", Severity.HIGH));
        run(3, "main", AuditStatus.FAILED);
        run(4, "main", AuditStatus.RUNNING);

        FindingLifecycleService.Baseline b = service.loadBaseline(4L);
        assertEquals(1L, b.baseAuditId());
        assertEquals(Set.of(Fingerprints.compute(findingsById.get(10L))), b.previousFingerprints());
    }

    @Test
    void primeiraAuditoriaNaoTemBase() {
        run(1, "main", AuditStatus.RUNNING);
        assertNull(service.loadBaseline(1L).baseAuditId());
    }

    @Test
    void supressaoVencidaNaoValeMais() {
        run(1, "main", AuditStatus.RUNNING);
        FindingSuppression ativa = new FindingSuppression();
        ativa.setFingerprint("ativa");
        ativa.setExpiresAt(LocalDateTime.now().plusDays(1));
        FindingSuppression vencida = new FindingSuppression();
        vencida.setFingerprint("vencida");
        vencida.setExpiresAt(LocalDateTime.now().minusDays(1));
        FindingSuppression semPrazo = new FindingSuppression();
        semPrazo.setFingerprint("sempre");
        suppressions.addAll(List.of(ativa, vencida, semPrazo));

        assertEquals(Set.of("ativa", "sempre"), service.loadBaseline(1L).suppressedFingerprints());
    }

    // ------------------------------------------------------------------ diff

    @Test
    void diffMostraNovosCorrigidosEPersistentes() {
        run(1, "main", AuditStatus.COMPLETED,
                finding(10, "sql", "A.java", "q1", Severity.HIGH),
                finding(11, "xss", "B.java", "q2", Severity.MEDIUM),
                finding(12, "pwd", "C.java", "q3", Severity.CRITICAL));
        run(2, "main", AuditStatus.COMPLETED,
                finding(20, "sql", "A.java", "q1", Severity.HIGH),      // persiste (outra linha, mesmo trecho)
                finding(21, "ldap", "D.java", "q4", Severity.HIGH));    // novo

        AuditDiffResponse diff = service.diff(2L);
        assertTrue(diff.hasBaseline());
        assertEquals(1L, diff.baseAuditId());
        assertEquals(1, diff.persistingCount());
        assertEquals(List.of("ldap"), diff.newFindings().stream().map(f -> f.ruleId()).toList());
        assertEquals(Set.of("xss", "pwd"), new HashSet<>(diff.resolvedFindings().stream().map(f -> f.ruleId()).toList()));
        assertTrue(diff.scoreDelta() > 0, "menos problemas graves => score sobe");
        assertEquals(Math.round((diff.overall() - diff.baseOverall()) * 100.0) / 100.0, diff.scoreDelta());
    }

    @Test
    void diffDaPrimeiraAuditoriaNaoTemBase() {
        run(1, "main", AuditStatus.COMPLETED, finding(10, "sql", "A.java", "q", Severity.HIGH));
        AuditDiffResponse diff = service.diff(1L);
        assertFalse(diff.hasBaseline());
        assertTrue(diff.newFindings().isEmpty());
        assertThrows(NotFoundException.class, () -> service.diff(99L));
    }

    @Test
    void diffIgnoraSuprimidos() {
        run(1, "main", AuditStatus.COMPLETED, finding(10, "sql", "A.java", "q", Severity.HIGH));
        Finding suprimido = finding(20, "pwd", "C.java", "s", Severity.CRITICAL);
        run(2, "main", AuditStatus.COMPLETED, suprimido);
        suprimido.setStatus(FindingStatus.SUPPRESSED);

        AuditDiffResponse diff = service.diff(2L);
        assertTrue(diff.newFindings().isEmpty());                  // suprimido não é "novo"
        assertEquals(1, diff.resolvedFindings().size());           // o do baseline sumiu
    }

    // ------------------------------------------------------------------ suprimir / reabrir

    @Test
    void suprimirRetiraDoScoreDaAuditoriaEDaMaisRecente() {
        AuditRun r1 = run(1, "main", AuditStatus.COMPLETED, finding(10, "pwd", "C.java", "s", Severity.CRITICAL));
        AuditRun r2 = run(2, "main", AuditStatus.COMPLETED, finding(20, "pwd", "C.java", "s", Severity.CRITICAL));
        double antes = r2.getRiskScore().getSecurity();

        FindingSuppression s = service.suppress(10L, req(null));

        assertEquals(FindingStatus.SUPPRESSED, findingsById.get(10L).getStatus());
        assertEquals(FindingStatus.SUPPRESSED, findingsById.get(20L).getStatus());   // mesma identidade, auditoria mais recente
        assertTrue(r2.getRiskScore().getSecurity() > antes);
        assertTrue(r1.getRiskScore().getSecurity() > antes);
        assertEquals("ana@sentinel", s.getCreatedBy());
        assertEquals(SuppressionReason.FALSE_POSITIVE, s.getReason());
        assertEquals("pwd", s.getRuleId());
        assertEquals(1, suppressions.size());
    }

    @Test
    void suprimirDeNovoAtualizaEmVezDeDuplicar() {
        run(1, "main", AuditStatus.COMPLETED, finding(10, "pwd", "C.java", "s", Severity.CRITICAL));
        service.suppress(10L, req(null));
        service.suppress(10L, new SuppressRequest(SuppressionReason.ACCEPTED_RISK, "Risco aceito até a migração", "bia", null));
        assertEquals(1, suppressions.size());
        assertEquals(SuppressionReason.ACCEPTED_RISK, suppressions.get(0).getReason());
    }

    @Test
    void naoAceitaPrazoNoPassadoNemFindingInexistente() {
        run(1, "main", AuditStatus.COMPLETED, finding(10, "pwd", "C.java", "s", Severity.CRITICAL));
        assertThrows(BadRequestException.class, () -> service.suppress(10L, req(LocalDateTime.now().minusDays(1))));
        assertThrows(NotFoundException.class, () -> service.suppress(999L, req(null)));
        assertTrue(suppressions.isEmpty());
    }

    @Test
    void removerSupressaoReabreOFindingERecalculaOScore() {
        AuditRun r1 = run(1, "main", AuditStatus.COMPLETED, finding(10, "pwd", "C.java", "s", Severity.CRITICAL));
        double original = r1.getRiskScore().getSecurity();
        FindingSuppression s = service.suppress(10L, req(null));

        service.removeSuppression(1L, s.getId());

        assertEquals(FindingStatus.OPEN, findingsById.get(10L).getStatus());
        assertEquals(original, r1.getRiskScore().getSecurity());
        assertTrue(suppressions.isEmpty());
        assertThrows(NotFoundException.class, () -> service.removeSuppression(1L, 12345L));
    }

    // ------------------------------------------------------------------ tendência

    @Test
    void tendenciaVemEmOrdemCronologicaESemSuprimidosNaContagem() {
        run(1, "main", AuditStatus.COMPLETED, finding(10, "a", "A.java", "1", Severity.HIGH), finding(11, "b", "B.java", "2", Severity.HIGH));
        run(2, "main", AuditStatus.COMPLETED, finding(20, "a", "A.java", "1", Severity.HIGH));
        run(3, "main", AuditStatus.FAILED);

        List<TrendPoint> trend = service.trend(1L, 30);
        assertEquals(List.of(1L, 2L), trend.stream().map(TrendPoint::auditId).toList());
        assertEquals(2L, trend.get(0).findings().get(Severity.HIGH));
        assertEquals(1L, trend.get(1).findings().get(Severity.HIGH));
        assertTrue(trend.get(1).overall() > trend.get(0).overall());
    }

    // ------------------------------------------------------------------ orientação de correção

    @Test
    void catalogoOrientaPorRegraELinkaIdentificadores() {
        com.sentinel.audit.pack.TestPacks.registry();
        Finding sql = finding(1, "audit-rules.rules.security.sentinel-sql-injection", "A.java", "q", Severity.HIGH);
        sql.setCveId("CWE-89");
        RemediationDto r = RemediationCatalog.forFinding(sql);
        assertNotNull(r);
        assertTrue(r.after().contains("prepareStatement"));
        assertEquals("https://cwe.mitre.org/data/definitions/89.html", r.references().get(0));

        Finding hist = finding(2, "secrets-history-password-assignment", "A.java", "q", Severity.HIGH);
        assertTrue(RemediationCatalog.forFinding(hist).summary().contains("REVOGUE"));   // histórico ganha da regra genérica

        Finding dep = finding(3, "osv-org.yaml:snakeyaml", "pom.xml", null, Severity.HIGH);
        dep.setCveId("GHSA-abcd-1234-efgh");
        assertEquals("https://github.com/advisories/GHSA-ABCD-1234-EFGH", RemediationCatalog.forFinding(dep).references().get(0));

        Finding desconhecido = finding(4, "regra-nova", "A.java", "q", Severity.LOW);
        assertNull(RemediationCatalog.forFinding(desconhecido));
    }
}
