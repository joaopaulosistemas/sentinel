package com.sentinel.audit.resource;

import com.sentinel.audit.insights.AuditInsights;
import com.sentinel.audit.insights.InsightsService;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.service.IngestionService;
import com.sentinel.audit.service.RunCatalog;
import com.sentinel.audit.service.SecretBox;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Leituras sobre o estado de uma auditoria: se o código mudou desde então e a visão completa por área. */
@ApplicationScoped
public class AuditStatusViews {

    @Inject
    RunCatalog runCatalog;

    @Inject
    InsightsService insightsService;

    @Inject
    IngestionService ingestionService;

    @Inject
    SecretBox secretBox;

    /** Se o código mudou desde a auditoria: {@code stale} é nulo quando não dá para saber. */
    public record Freshness(String auditedCommit, String currentCommit, Boolean stale) {
    }

    Freshness freshness(Long id) {
        AuditRun run = runCatalog.require(id);
        String audited = run.getCommitSha();
        AuditedSystem system = run.getSystem();
        if (audited == null || audited.isBlank() || system == null || run.getBranch() == null) {
            return new Freshness(audited, null, null);
        }
        String token = system.hasGitToken() ? secretBox.decrypt(system.getGitTokenEnc()) : null;
        String current = system.isLocalSource()
                ? ingestionService.localHeadSha(system.getLocalPath(), run.getBranch())
                : ingestionService.remoteHeadSha(system.getRepoUrl(), run.getBranch(), token, system.getGitTokenHosts());
        return new Freshness(audited, current, current == null ? null : !current.equals(audited));
    }

    AuditInsights insights(Long id) {
        AuditRun run = runCatalog.require(id);
        return insightsService.forRun(run);
    }
}
