package com.sentinel.audit.mcp;

import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.FindingResponse;
import com.sentinel.audit.dto.FixPlanDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decide o que de cada resposta sai pelo MCP, de acordo com o {@link DataLevel} do sistema.
 * <p>
 * Regras que valem em todos os níveis: o trecho de código, a descrição do achado e a recomendação gerada por IA
 * nunca saem. Em {@link DataLevel#SENSITIVE} também não saem títulos nem locais: só contagens por regra.
 */
public final class McpShaper {

    /** Teto de itens em qualquer lista devolvida. */
    public static final int MAX_LIST = 50;

    private McpShaper() {}

    /** Um achado, sem nada além do que o nível permite. Não use em {@link DataLevel#SENSITIVE}. */
    public static Map<String, Object> finding(FindingResponse f, DataLevel level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.id());
        m.put("severidade", f.severity());
        m.put("categoria", f.category());
        m.put("regra", f.ruleId());
        m.put("titulo", f.title());
        m.put("origem", f.toolSource());
        m.put("situacao", f.status());
        m.put("novo", f.isNew());
        m.put("confianca", f.confidence());
        if (f.confidenceReason() != null) {
            m.put("motivoConfianca", f.confidenceReason());
        }
        if (level == DataLevel.FILE_LINE) {
            m.put("arquivo", f.filePath());
            m.put("linha", f.lineNumber());
        }
        return m;
    }

    /** Lista de achados do mais grave para o menos grave, cortada em {@code limit}. */
    public static List<Map<String, Object>> findings(List<FindingResponse> list, DataLevel level, int limit) {
        int cap = Math.max(1, Math.min(limit, MAX_LIST));
        return list.stream()
                .sorted(Comparator.comparingInt((FindingResponse f) -> f.severity().ordinal())
                        .thenComparing(f -> f.ruleId() == null ? "" : f.ruleId()))
                .limit(cap)
                .map(f -> finding(f, level))
                .toList();
    }

    /** Só números: quantas ocorrências de cada regra, da mais grave para a menos grave. */
    public static List<Map<String, Object>> countsByRule(List<FindingResponse> list) {
        Map<String, List<FindingResponse>> byRule = new LinkedHashMap<>();
        for (FindingResponse f : list) {
            byRule.computeIfAbsent(f.ruleId() == null ? "sem-regra" : f.ruleId(), k -> new ArrayList<>()).add(f);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<FindingResponse>> e : byRule.entrySet()) {
            FindingResponse worst = e.getValue().stream()
                    .min(Comparator.comparingInt((FindingResponse f) -> f.severity().ordinal())).orElseThrow();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("regra", e.getKey());
            m.put("severidade", worst.severity());
            m.put("categoria", worst.category());
            m.put("ocorrencias", e.getValue().size());
            out.add(m);
        }
        out.sort(Comparator.comparingInt((Map<String, Object> m) -> ((Enum<?>) m.get("severidade")).ordinal())
                .thenComparing((Map<String, Object> m) -> (Integer) m.get("ocorrencias"), Comparator.reverseOrder()));
        return out.stream().limit(MAX_LIST).toList();
    }

    /** Achados abertos no formato do nível; em {@link DataLevel#SENSITIVE}, contagens por regra. */
    public static Map<String, Object> findingsResult(List<FindingResponse> list, DataLevel level, int limit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", list.size());
        m.put("nivelDeDados", level);
        if (level == DataLevel.SENSITIVE) {
            m.put("porRegra", countsByRule(list));
        } else {
            List<Map<String, Object>> shown = findings(list, level, limit);
            m.put("mostrando", shown.size());
            m.put("achados", shown);
        }
        return m;
    }

    public static Map<String, Object> fixPlan(FixPlanDto plan, DataLevel level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("notaAtual", plan.scoreNow());
        m.put("notaSeOsCriticosForemCorrigidos", plan.scoreIfCriticalFixed());
        m.put("criticos", plan.criticalCount());
        m.put("nivelDeDados", level);
        List<Map<String, Object>> items = new ArrayList<>();
        for (FixPlanDto.Item i : plan.items()) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("regra", i.ruleId());
            it.put("severidade", i.severity());
            it.put("categoria", i.category());
            it.put("ocorrencias", i.occurrences());
            it.put("ganhoDeNota", i.scoreGain());
            if (level != DataLevel.SENSITIVE) {
                it.put("titulo", i.title());
                it.put("comoCorrigir", i.howToFix());
            }
            if (level == DataLevel.FILE_LINE) {
                it.put("arquivoExemplo", i.exampleFile());
                it.put("linhaExemplo", i.exampleLine());
            }
            items.add(it);
        }
        m.put("itens", items);
        return m;
    }

    public static Map<String, Object> diff(AuditDiffResponse d, DataLevel level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("auditoria", d.auditId());
        m.put("temBase", d.hasBaseline());
        m.put("nivelDeDados", level);
        if (!d.hasBaseline()) {
            m.put("nota", d.overall());
            m.put("mensagem", "Primeira auditoria deste sistema: ainda não há com o que comparar.");
            return m;
        }
        m.put("auditoriaBase", d.baseAuditId());
        m.put("nota", d.overall());
        m.put("notaBase", d.baseOverall());
        m.put("variacao", d.scoreDelta());
        m.put("novos", d.newFindings().size());
        m.put("resolvidos", d.resolvedFindings().size());
        m.put("persistentes", d.persistingCount());
        if (level == DataLevel.SENSITIVE) {
            m.put("novosPorRegra", countsByRule(d.newFindings()));
        } else {
            m.put("achadosNovos", findings(d.newFindings(), level, MAX_LIST));
            m.put("achadosResolvidos", findings(d.resolvedFindings(), level, MAX_LIST));
        }
        return m;
    }
}
