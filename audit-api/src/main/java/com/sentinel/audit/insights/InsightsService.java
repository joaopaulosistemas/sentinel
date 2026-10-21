package com.sentinel.audit.insights;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.service.FindingLifecycleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

/** Monta a visão de auditoria completa de uma execução a partir dos findings e do contexto gravado. */
@ApplicationScoped
public class InsightsService {

    private static final Logger LOG = Logger.getLogger(InsightsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Inject
    FindingRepository findingRepository;

    public AuditInsights forRun(AuditRun run) {
        List<Finding> active = FindingLifecycleService.active(findingRepository.findByAuditRunId(run.getId()));
        return InsightsBuilder.build(run.getSystem() == null ? null : run.getSystem().getName(), active,
                parseContext(run.getContextJson()));
    }

    /** Contexto ilegível ou ausente (auditoria antiga) vira {@code null}: os insights saem só com os findings. */
    public static AuditContextData parseContext(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, AuditContextData.class);
        } catch (Exception e) {
            LOG.warnf("Contexto da auditoria ilegível: %s", e.getMessage());
            return null;
        }
    }
}
