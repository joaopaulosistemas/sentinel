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

    @Inject
    com.sentinel.audit.review.ReviewService review;

    @Inject
    com.sentinel.audit.actions.PendingActionService pending;

    /**
     * @return o resultado em JSON
     * @throws IllegalArgumentException argumento inválido (a mensagem é segura para mostrar ao cliente)
     */
    public String run(McpTool tool, Long systemId, DataLevel level, JsonNode args) {
        if (tool.writes()) {
            return writeTool(tool, systemId, args);
        }
        if (tool == McpTool.STATUS_PEDIDOS) {
            return json(pendingStatus(systemId));
        }
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
                case STATUS_PEDIDOS -> pendingStatus(systemId);
                case REGISTRAR_SUGESTAO, PROPOR_REGRA, INICIAR_AUDITORIA, SUPRIMIR_ACHADO -> throw new IllegalStateException("ferramenta de escrita fora do caminho de escrita");
            };
        }
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível montar a resposta", e);
        }
    }

    /** Ferramentas que gravam, só na área Revisão do Claude. Nunca tocam em achado, regra do motor ou nota. */
    private String writeTool(McpTool tool, Long systemId, JsonNode args) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (tool == McpTool.INICIAR_AUDITORIA || tool == McpTool.SUPRIMIR_ACHADO) {
            var a = tool == McpTool.INICIAR_AUDITORIA
                    ? pending.requestStartAudit(systemId, text(args, "branch"))
                    : pending.requestSuppress(systemId, args.path("achadoId").canConvertToLong() ? args.path("achadoId").asLong() : null,
                            text(args, "motivo"), text(args, "justificativa"));
            result.put("pedidoCriado", true);
            result.put("id", a.getId());
            result.put("situacao", a.getStatus().name());
            result.put("mensagem", "Pedido criado, mas NADA foi feito ainda: uma pessoa precisa aprovar no dashboard "
                    + "(Informações de Auditoria → Reauditoria) em até 1 hora. Consulte status_pedidos depois.");
            return json(result);
        }
        if (tool == McpTool.REGISTRAR_SUGESTAO) {
            var s = review.addSuggestion(systemId, text(args, "titulo"), text(args, "detalhe"), text(args, "regra"),
                    text(args, "arquivo"), args.path("linha").isInt() ? args.path("linha").asInt() : null);
            result.put("registrada", true);
            result.put("id", s.getId());
            result.put("mensagem", "Sugestão registrada em Revisão do Claude. Ela não entra na nota; uma pessoa vai avaliá-la.");
        } else {
            var p = review.propose(systemId, text(args, "id"), text(args, "titulo"), text(args, "motivo"), text(args, "regraSemgrep"));
            result.put("registrada", true);
            result.put("id", p.getId());
            result.put("etapa", p.getStage().name());
            result.put("mensagem", "Regra proposta. Ela só passa a rodar depois de revisada por uma pessoa e aprovada no teste de amostra, "
                    + "e fica fora da nota durante a observação.");
        }
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível montar a resposta", e);
        }
    }

    private Map<String, Object> pendingStatus(Long systemId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pedidos", pending.recent(systemId, 10).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("tipo", a.getKind().name());
            m.put("situacao", a.getStatus().name());
            m.put("resumo", a.getSummary());
            m.put("resultado", a.getResultNote());
            m.put("auditoriaId", a.getResultAuditRunId());
            return m;
        }).toList());
        return out;
    }

    private String json(Map<String, Object> result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível montar a resposta", e);
        }
    }

    private static String text(JsonNode args, String field) {
        JsonNode n = args.path(field);
        return n.isTextual() ? n.asText() : null;
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
