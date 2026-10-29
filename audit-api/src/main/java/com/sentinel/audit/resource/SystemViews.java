package com.sentinel.audit.resource;

import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.SuppressionResponse;
import com.sentinel.audit.dto.SystemSummaryResponse;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.AuditRunMapper;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.FindingLifecycleService;
import com.sentinel.audit.service.RunCatalog;
import com.sentinel.audit.service.ScoreTrend;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** O que a API de sistemas mostra a partir do histórico de auditorias: resumo, auditorias, tendência e supressões. */
@ApplicationScoped
public class SystemViews {

    @Inject
    AuditRunMapper runMapper;

    @Inject
    RunCatalog runCatalog;

    @Inject
    FindingCatalog findingCatalog;

    @Inject
    FindingLifecycleService lifecycleService;

    List<AuditRunResponse> audits(Long systemId) {
        return runCatalog.bySystem(systemId).stream()
                .map(r -> runMapper.toResponse(r, true))
                .collect(Collectors.toList());
    }

    List<TrendPoint> trend(Long systemId, int limit) {
        return lifecycleService.trend(systemId, limit);
    }

    List<SuppressionResponse> suppressions(Long systemId) {
        LocalDateTime now = LocalDateTime.now();
        return lifecycleService.listSuppressions(systemId).stream()
                .map(s -> SuppressionResponse.from(s, now))
                .collect(Collectors.toList());
    }

    void removeSuppression(Long systemId, Long suppressionId) {
        lifecycleService.removeSuppression(systemId, suppressionId);
    }

    SystemSummaryResponse summary(AuditedSystem system) {
        List<AuditRun> runs = runCatalog.bySystem(system.getId());
        AuditRun lastRun = runs.stream().filter(r -> r.getStatus() == AuditStatus.COMPLETED).findFirst().orElse(null);
        Map<Severity, Long> counts = lastRun == null ? Map.of() : findingCatalog.countBySeverity(lastRun.getId());
        boolean scored = lastRun != null && lastRun.getRiskScore() != null;
        return new SystemSummaryResponse(
                system.getId(), system.getName(), system.getStack(), system.getTeam(),
                scored ? lastRun.getRiskScore().getOverall() : null,
                lastRun == null ? null : lastRun.getCompletedAt(),
                lastRun == null ? null : counts.getOrDefault(Severity.CRITICAL, 0L),
                lastRun == null ? null : counts.getOrDefault(Severity.HIGH, 0L),
                system.getAuditIntervalHours(), system.effectiveScheduledBranch(),
                Boolean.TRUE.equals(system.getAuditOnPush()), system.effectiveProfile(),
                ScoreTrend.delta(runs), runs.size(),
                system.getRepoUrl(), system.getDescription(), system.effectiveSourceType(), system.getLocalPath(),
                system.isLocalSource() ? system.effectiveLocalSnapshot() : null,
                system.hasGitToken(), system.getGitTokenHosts(), system.hasWebhookSecret(), system.getGitHistoryDepth(),
                system.hasNotifyUrl(), system.getNotifyFormat(), system.getNotifyMinScoreDrop(),
                system.getNotifyMinSeverity(), system.getNotifyPublicUrl());
    }
}
