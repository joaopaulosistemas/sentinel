package com.sentinel.audit.resource;

import com.sentinel.audit.actions.PendingActionService;
import com.sentinel.audit.mcp.McpGateService;
import com.sentinel.audit.review.ReviewService;
import com.sentinel.audit.tuning.SystemTuningService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Apaga o que outros módulos guardam sobre um sistema que está sendo removido. Roda na transação de quem chama. */
@ApplicationScoped
public class SystemCleanup {

    @Inject
    McpGateService mcpGate;

    @Inject
    SystemTuningService tuningService;

    @Inject
    ReviewService reviewService;

    @Inject
    PendingActionService pendingActions;

    void purgeRelated(Long systemId) {
        mcpGate.purge(systemId);
        tuningService.purge(systemId);
        reviewService.purge(systemId);
        pendingActions.purge(systemId);
    }
}
