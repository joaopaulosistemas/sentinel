package com.sentinel.audit.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.dto.FindingMapper;
import com.sentinel.audit.dto.FindingResponse;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.RiskScore;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.service.FindingLifecycleService;
import com.sentinel.audit.service.FixPlanner;
import com.sentinel.audit.service.ScoringService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executa as ferramentas de leitura em cima dos serviços que o dashboard já usa. Nada aqui escreve no banco. */
@ApplicationScoped
public class McpToolService {

    private static final int DEFAULT_LIMIT = 20;

    @Inject
    AuditRunRepository runRepository;

    @Inject
    FindingRepository findingRepository;

    @Inject
    FindingLifecycleService lifecycleService;

    @Inject
    ScoringService scoringService;

    @Inject
    ObjectMapper mapper;

    /**
     * @return o resultado em JSON
     * @throws IllegalArgumentException argumento inválido (a mensagem é segura para mostrar ao cliente)
     */
    public String run(McpTool tool, Long systemId, DataLevel level, JsonNode args) {
        AuditRun run = latest(systemId);
        Map<String, Object> result;
        if (run == null) {
            result = new LinkedHashMap<>();
            result.put("mensagem", "Este sistema ainda não tem auditoria concluída.");
        } else {
            result = switch (tool) {
                case STATUS_AUDITORIA -> status(run, level);
                case BUSCAR_ACHADOS -> achados(run, level, args);
                case DIFF_AUDITORIA -> McpShaper.diff(lifecycleService.diff(run.getId()), level);
                case O_QUE_CORRIGIR_PRIMEIRO -> McpShaper.fixPlan(
                        FixPlanner.plan(active(run), run.getLinesOfCode(), scoringService), level);
            };
        }
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível montar a resposta", e);
        }
    }

    private AuditRun latest(Long systemId) {
        List<AuditRun> runs = runRepository.findLatestCompleted(systemId, 1);
        return runs.isEmpty() ? null : runs.get(0);
    }

    private List<Finding> active(AuditRun run) {
        return FindingLifecycleService.active(findingRepository.findByAuditRunId(run.getId()));
    }

    private Map<String, Object> status(AuditRun run, DataLevel level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("auditoria", run.getId());
        m.put("branch", run.getBranch());
        m.put("commit", run.getCommitSha());
        m.put("concluidaEm", run.getCompletedAt() == null ? null : run.getCompletedAt().toString());
        m.put("linhasDeCodigo", run.getLinesOfCode());
        m.put("nivelDeDados", level);
        RiskScore s = run.getRiskScore();
        if (s != null) {
            Map<String, Object> nota = new LinkedHashMap<>();
            nota.put("geral", s.getOverall());
            nota.put("seguranca", s.getSecurity());
            nota.put("qualidade", s.getQuality());
            nota.put("arquitetura", s.getArchitecture());
            nota.put("dependencias", s.getDependencies());
            nota.put("observabilidade", s.getObservability());
            nota.put("desempenho", s.getPerformance());
            m.put("nota", nota);
        }
        List<Finding> active = active(run);
        Map<Severity, Long> bySeverity = new EnumMap<>(Severity.class);
        for (Severity sev : Severity.values()) {
            bySeverity.put(sev, 0L);
        }
        active.forEach(f -> bySeverity.merge(f.getSeverity(), 1L, Long::sum));
        m.put("achadosPorSeveridade", bySeverity);
        m.put("totalAchados", active.size());
        return m;
    }

    private Map<String, Object> achados(AuditRun run, DataLevel level, JsonNode args) {
        Severity severity = parseEnum(Severity.class, args.path("severidade").asText(""), "severidade");
        Category category = parseEnum(Category.class, args.path("categoria").asText(""), "categoria");
        boolean onlyNew = args.path("somenteNovos").asBoolean(false);
        int limit = args.path("limite").asInt(DEFAULT_LIMIT);
        List<FindingResponse> list = active(run).stream()
                .map(FindingMapper::toResponse)
                .filter(f -> severity == null || f.getSeverity() == severity)
                .filter(f -> category == null || f.getCategory() == category)
                .filter(f -> !onlyNew || Boolean.TRUE.equals(f.getIsNew()))
                .toList();
        return McpShaper.findingsResult(list, level, limit);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valor inválido para " + field + ": " + raw);
        }
    }
}
