package com.sentinel.audit.service;

import com.sentinel.audit.dto.AuditDiffResponse;
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
        AuditRunResponse dto = new AuditRunResponse();
        dto.setId(run.getId());
        dto.setSystemId(run.getSystem().getId());
        dto.setSystemName(run.getSystem().getName());
        dto.setBranch(run.getBranch());
        dto.setBaseRef(run.getBaseRef());
        dto.setStatus(run.getStatus());
        dto.setStartedAt(run.getStartedAt());
        dto.setCompletedAt(run.getCompletedAt());
        dto.setErrorMessage(run.getErrorMessage());
        dto.setTriggeredBy(run.getTriggeredBy());
        dto.setProfile(run.getSystem().effectiveProfile());
        dto.setCommitSha(run.getCommitSha());
        dto.setLinesOfCode(run.getLinesOfCode());

        if (run.getRiskScore() != null) {
            var s = run.getRiskScore();
            dto.setRiskScore(new RiskScoreDto(s.getOverall(), s.getSecurity(), s.getQuality(), s.getArchitecture(),
                    s.getDependencies(), s.getObservability(), s.getPerformance()));
        }

        Map<Severity, Long> summary = findingRepository.countBySeverityForRun(run.getId());
        dto.setFindingsSummary(summary);
        dto.setTotalFindings(summary.values().stream().mapToLong(Long::longValue).sum());

        if (run.getStatus() == AuditStatus.RUNNING || run.getStatus() == AuditStatus.PENDING) {
            progressTracker.snapshot(run.getId()).ifPresent(dto::setProgress);
        }
        if (withDiff && run.getStatus() == AuditStatus.COMPLETED) {
            try {
                AuditDiffResponse diff = lifecycleService.diff(run.getId());
                if (diff.hasBaseline()) {
                    dto.setNewFindings(diff.newFindings().size());
                    dto.setResolvedFindings(diff.resolvedFindings().size());
                    dto.setPreviousAuditId(diff.baseAuditId());
                    dto.setScoreDelta(diff.scoreDelta());
                }
            } catch (RuntimeException ignored) {
                // a comparação é um complemento: sem ela a resposta segue valendo
            }
        }
        return dto;
    }
}
