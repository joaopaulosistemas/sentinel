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
        SystemSummaryResponse dto = new SystemSummaryResponse();
        dto.setId(system.getId());
        dto.setName(system.getName());
        dto.setStack(system.getStack());
        dto.setTeam(system.getTeam());
        dto.setAuditIntervalHours(system.getAuditIntervalHours());
        dto.setScheduledBranch(system.effectiveScheduledBranch());
        dto.setAuditOnPush(Boolean.TRUE.equals(system.getAuditOnPush()));
        dto.setProfile(system.effectiveProfile());
        dto.setRepoUrl(system.getRepoUrl());
        dto.setSourceType(system.effectiveSourceType());
        dto.setLocalPath(system.getLocalPath());
        dto.setLocalSnapshot(system.isLocalSource() ? system.effectiveLocalSnapshot() : null);
        dto.setDescription(system.getDescription());
        dto.setGitTokenSet(system.hasGitToken());
        dto.setWebhookSecretSet(system.hasWebhookSecret());
        dto.setGitHistoryDepth(system.getGitHistoryDepth());
        dto.setNotifyUrlSet(system.hasNotifyUrl());
        dto.setNotifyFormat(system.getNotifyFormat());
        dto.setNotifyMinScoreDrop(system.getNotifyMinScoreDrop());
        dto.setNotifyMinSeverity(system.getNotifyMinSeverity());
        dto.setNotifyPublicUrl(system.getNotifyPublicUrl());
        dto.setGitTokenHosts(system.getGitTokenHosts());

        List<AuditRun> runs = runCatalog.bySystem(system.getId());
        dto.setScoreDelta(ScoreTrend.delta(runs));
        dto.setAuditCount(runs.size());
        runs.stream()
            .filter(r -> r.getStatus() == AuditStatus.COMPLETED)
            .findFirst()
            .ifPresent(lastRun -> {
                if (lastRun.getRiskScore() != null) {
                    dto.setLastAuditScore(lastRun.getRiskScore().getOverall());
                }
                dto.setLastAuditDate(lastRun.getCompletedAt());

                Map<Severity, Long> counts = findingCatalog.countBySeverity(lastRun.getId());
                dto.setOpenCritical(counts.getOrDefault(Severity.CRITICAL, 0L));
                dto.setOpenHigh(counts.getOrDefault(Severity.HIGH, 0L));
            });

        return dto;
    }
}
