package com.sentinel.audit.service;

import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.FindingMapper;
import com.sentinel.audit.dto.FindingResponse;
import com.sentinel.audit.dto.SuppressRequest;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.FindingSuppression;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.repository.FindingSuppressionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ciclo de vida dos findings entre auditorias: o que é novo, o que foi corrigido, o que foi
 * suprimido (falso positivo / risco aceito) e como o score evolui no tempo.
 */
@ApplicationScoped
public class FindingLifecycleService {

    /**
     * Base de comparação de uma auditoria.
     *
     * @param baseAuditId           auditoria anterior (mesmo sistema e branch), ou {@code null} se for a primeira
     * @param previousFingerprints  fingerprints da auditoria anterior
     * @param suppressedFingerprints fingerprints com supressão ainda válida para o sistema
     */
    public record Baseline(Long baseAuditId, Set<String> previousFingerprints, Set<String> suppressedFingerprints) {
        public static Baseline empty() {
            return new Baseline(null, Set.of(), Set.of());
        }
    }

    @Inject
    AuditRunRepository auditRunRepository;

    @Inject
    FindingRepository findingRepository;

    @Inject
    FindingSuppressionRepository suppressionRepository;

    @Inject
    ScoringService scoringService;

    // ------------------------------------------------------------------ durante a auditoria

    /** Lê (dentro de uma transação) o que a auditoria precisa para marcar novos e suprimidos. */
    @Transactional
    public Baseline loadBaseline(Long auditRunId) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null) {
            return Baseline.empty();
        }
        Long systemId = run.getSystem().getId();
        Set<String> suppressed = new HashSet<>();
        for (FindingSuppression s : suppressionRepository.findActiveBySystemId(systemId, LocalDateTime.now())) {
            suppressed.add(s.getFingerprint());
        }
        AuditRun previous = previousFor(run, auditRunId);
        if (previous == null) {
            return new Baseline(null, Set.of(), suppressed);
        }
        return new Baseline(previous.getId(), fingerprintsOf(findingRepository.findByAuditRunId(previous.getId())), suppressed);
    }

    /**
     * Base de comparação: a auditoria completa anterior do mesmo branch; para auditoria de PR, a última completa do
     * branch base (ou, se o branch base nunca foi auditado com esse nome, a do branch padrão).
     */
    AuditRun previousFor(AuditRun run, Long auditId) {
        Long systemId = run.getSystem().getId();
        if (run.getBaseRef() == null || run.getBaseRef().isBlank()) {
            return auditRunRepository.findPreviousCompleted(systemId, run.getBranch(), auditId);
        }
        AuditRun base = auditRunRepository.findPreviousCompleted(systemId, run.getBaseRef(), auditId);
        return base != null ? base : auditRunRepository.findPreviousCompleted(systemId, null, auditId);
    }

    /** Calcula o fingerprint, marca os novos e suprime os que têm supressão válida. */
    public static void apply(List<Finding> findings, Baseline baseline) {
        for (Finding f : findings) {
            f.setFingerprint(Fingerprints.compute(f));
            f.setIsNew(baseline.baseAuditId() == null ? null : !baseline.previousFingerprints().contains(f.getFingerprint()));
            if (baseline.suppressedFingerprints().contains(f.getFingerprint())) {
                f.setStatus(FindingStatus.SUPPRESSED);
            }
        }
    }

    /** Findings que contam para o score (tudo menos os suprimidos). */
    public static List<Finding> active(List<Finding> findings) {
        return findings.stream()
                .filter(f -> f.getStatus() != FindingStatus.SUPPRESSED && !Boolean.TRUE.equals(f.getShadow()))
                .toList();
    }

    // ------------------------------------------------------------------ supressão

    @Transactional
    public FindingSuppression suppress(Long findingId, SuppressRequest request) {
        Finding finding = findingRepository.findById(findingId);
        if (finding == null) {
            throw new NotFoundException("Finding não encontrado: " + findingId);
        }
        AuditRun run = finding.getAuditRun();
        AuditedSystem system = run.getSystem();
        String fingerprint = fingerprintOf(finding);
        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now())) {
            throw new jakarta.ws.rs.BadRequestException("A data de validade da supressão deve estar no futuro");
        }

        FindingSuppression s = suppressionRepository.findBySystemAndFingerprint(system.getId(), fingerprint)
                .orElseGet(FindingSuppression::new);
        s.setSystem(system);
        s.setFingerprint(fingerprint);
        s.setReason(request.reason());
        s.setJustification(request.justification().strip());
        s.setCreatedBy(request.createdBy());
        s.setCreatedAt(LocalDateTime.now());
        s.setExpiresAt(request.expiresAt());
        s.setRuleId(finding.getRuleId());
        s.setFilePath(finding.getFilePath());
        s.setTitle(finding.getTitle());
        if (s.getId() == null) {
            suppressionRepository.persist(s);
        }

        // Efeito imediato na auditoria do finding e na mais recente do sistema.
        for (AuditRun target : runsToUpdate(run, system.getId())) {
            setStatusByFingerprint(target, fingerprint, FindingStatus.SUPPRESSED);
            rescore(target);
        }
        return s;
    }

    @Transactional
    public void removeSuppression(Long systemId, Long suppressionId) {
        FindingSuppression s = suppressionRepository.findById(suppressionId);
        if (s == null || !s.getSystem().getId().equals(systemId)) {
            throw new NotFoundException("Supressão não encontrada: " + suppressionId);
        }
        String fingerprint = s.getFingerprint();
        suppressionRepository.delete(s);
        List<AuditRun> latest = auditRunRepository.findLatestCompleted(systemId, 1);
        for (AuditRun target : latest) {
            setStatusByFingerprint(target, fingerprint, FindingStatus.OPEN);
            rescore(target);
        }
    }

    @Transactional
    public List<FindingSuppression> listSuppressions(Long systemId) {
        return suppressionRepository.findBySystemId(systemId);
    }

    private List<AuditRun> runsToUpdate(AuditRun own, Long systemId) {
        List<AuditRun> runs = new ArrayList<>();
        runs.add(own);
        for (AuditRun latest : auditRunRepository.findLatestCompleted(systemId, 1)) {
            if (!latest.getId().equals(own.getId())) {
                runs.add(latest);
            }
        }
        return runs;
    }

    private void setStatusByFingerprint(AuditRun run, String fingerprint, FindingStatus status) {
        for (Finding f : findingRepository.findByAuditRunId(run.getId())) {
            if (fingerprint.equals(fingerprintOf(f))) {
                if (status == FindingStatus.OPEN && f.getStatus() != FindingStatus.SUPPRESSED) {
                    continue; // não reabre o que foi marcado FIXED/ACKNOWLEDGED por outro caminho
                }
                f.setStatus(status);
            }
        }
    }

    /** Recalcula o score da auditoria sem os findings suprimidos. */
    void rescore(AuditRun run) {
        if (run.getRiskScore() == null) {
            return;
        }
        run.setRiskScore(scoringService.calculate(active(findingRepository.findByAuditRunId(run.getId()))));
    }

    // ------------------------------------------------------------------ comparação e tendência

    @Transactional
    public AuditDiffResponse diff(Long auditId) {
        AuditRun run = auditRunRepository.findById(auditId);
        if (run == null) {
            throw new NotFoundException("Auditoria não encontrada: " + auditId);
        }
        Double overall = run.getRiskScore() == null ? null : run.getRiskScore().getOverall();
        AuditRun base = previousFor(run, auditId);
        if (base == null) {
            return new AuditDiffResponse(auditId, null, false, overall, null, null, List.of(), List.of(), 0);
        }
        List<Finding> current = active(findingRepository.findByAuditRunId(auditId));
        List<Finding> previous = active(findingRepository.findByAuditRunId(base.getId()));
        Set<String> currentFps = fingerprintsOf(current);
        Set<String> previousFps = fingerprintsOf(previous);

        List<FindingResponse> added = new ArrayList<>();
        int persisting = 0;
        for (Finding f : current) {
            if (previousFps.contains(fingerprintOf(f))) {
                persisting++;
            } else {
                added.add(FindingMapper.toResponse(f));
            }
        }
        List<FindingResponse> resolved = new ArrayList<>();
        for (Finding f : previous) {
            if (!currentFps.contains(fingerprintOf(f))) {
                resolved.add(FindingMapper.toResponse(f));
            }
        }
        Double baseOverall = base.getRiskScore() == null ? null : base.getRiskScore().getOverall();
        Double delta = overall == null || baseOverall == null ? null : Math.round((overall - baseOverall) * 100.0) / 100.0;
        return new AuditDiffResponse(auditId, base.getId(), true, overall, baseOverall, delta, added, resolved, persisting);
    }

    @Transactional
    public List<TrendPoint> trend(Long systemId, int limit) {
        List<AuditRun> runs = new ArrayList<>(auditRunRepository.findLatestCompleted(systemId, Math.max(1, Math.min(limit, 100))));
        Collections.reverse(runs);
        List<TrendPoint> points = new ArrayList<>();
        for (AuditRun r : runs) {
            if (r.getRiskScore() == null) {
                continue;
            }
            points.add(new TrendPoint(r.getId(), r.getBranch(), r.getCompletedAt(),
                    r.getRiskScore().getOverall(), r.getRiskScore().getSecurity(), r.getRiskScore().getQuality(),
                    r.getRiskScore().getArchitecture(), r.getRiskScore().getDependencies(),
                    r.getRiskScore().getObservability(), findingRepository.countBySeverityForRun(r.getId())));
        }
        return points;
    }

    // ------------------------------------------------------------------ util

    /** Fingerprint gravado, ou calculado na hora para auditorias antigas (anteriores a este recurso). */
    private static String fingerprintOf(Finding f) {
        return f.getFingerprint() != null ? f.getFingerprint() : Fingerprints.compute(f);
    }

    private static Set<String> fingerprintsOf(List<Finding> findings) {
        Set<String> set = new HashSet<>();
        for (Finding f : findings) {
            set.add(fingerprintOf(f));
        }
        return set;
    }
}
