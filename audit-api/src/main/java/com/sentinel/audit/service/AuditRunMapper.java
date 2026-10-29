package com.sentinel.audit.service;

import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.AuditProgressDto;
import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.RiskScoreDto;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.repository.FindingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

/** Monta a resposta de uma auditoria; as listas e o detalhe usam o mesmo resultado. */
@ApplicationScoped
public class AuditRunMapper {

    @Inject
    FindingRepository findingRepository;

    @Inject
    AuditProgressTracker progressTracker;

    @Inject
    FindingLifecycleService lifecycleService;

    /** Resposta sem a comparação com a auditoria anterior (mais barata, para listas longas). */
    public AuditRunResponse toResponse(AuditRun run) {
        return toResponse(run, false);
    }

    /**
     * @param withDiff se verdadeiro e a auditoria estiver concluída, inclui novos/resolvidos e a variação da nota
     *                 em relação à anterior
     */
    public AuditRunResponse toResponse(AuditRun run, boolean withDiff) {
        Map<Severity, Long> summary = findingRepository.countBySeverityForRun(run.getId());
        boolean running = run.getStatus() == AuditStatus.RUNNING || run.getStatus() == AuditStatus.PENDING;
        AuditProgressDto progress = running ? progressTracker.snapshot(run.getId()).orElse(null) : null;
        AuditDiffResponse diff = withDiff && run.getStatus() == AuditStatus.COMPLETED ? comparison(run) : null;
        boolean compared = diff != null && diff.hasBaseline();
        return new AuditRunResponse(
                run.getId(), run.getSystem().getId(), run.getSystem().getName(), run.getBranch(), run.getBaseRef(),
                run.getStatus(), scoreOf(run), summary, run.getStartedAt(), run.getCompletedAt(), run.getErrorMessage(),
                progress, run.getTriggeredBy(), run.getSystem().effectiveProfile(), run.getCommitSha(),
                run.getLinesOfCode(), summary.values().stream().mapToLong(Long::longValue).sum(),
                compared ? diff.newFindings().size() : null,
                compared ? diff.resolvedFindings().size() : null,
                compared ? diff.baseAuditId() : null,
                compared ? diff.scoreDelta() : null);
    }

    private static RiskScoreDto scoreOf(AuditRun run) {
        var s = run.getRiskScore();
        return s == null ? null : new RiskScoreDto(s.getOverall(), s.getSecurity(), s.getQuality(),
                s.getArchitecture(), s.getDependencies(), s.getObservability(), s.getPerformance());
    }

    /** A comparação é um complemento: sem ela a resposta segue valendo. */
    private AuditDiffResponse comparison(AuditRun run) {
        try {
            return lifecycleService.diff(run.getId());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
