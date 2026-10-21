package com.sentinel.audit.analyzer.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê planos de execução salvos ({@code EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)}) e aponta os pontos que mais custam:
 * varredura sequencial que descarta quase tudo, ordenação em disco, estimativa errada e tempo total alto.
 */
final class PlanChecks {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final double SLOW_MS = 1000;
    static final double REMOVED_ROWS = 100_000;
    static final double ESTIMATE_FACTOR = 100;

    private PlanChecks() {
    }

    static List<Finding> run(String json, String file, AuditRun run) {
        List<Finding> out = new ArrayList<>();
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            return out;
        }
        JsonNode first = root.isArray() && !root.isEmpty() ? root.get(0) : root;
        JsonNode plan = first.path("Plan");
        if (plan.isMissingNode()) {
            return out;
        }
        double time = first.path("Execution Time").asDouble(0);
        if (time >= SLOW_MS) {
            out.add(Ev.finding(run, Category.PERFORMANCE, time >= 5000 ? Severity.HIGH : Severity.MEDIUM, "evidencia-plano-lento",
                    "Consulta do plano levou " + Ev.fmt(time) + " ms (" + file + ")",
                    "O plano salvo em " + file + " executou em " + Ev.fmt(time) + " ms. Os pontos que mais custam estão "
                            + "listados nos outros achados deste arquivo; comece pelo nó de maior tempo.",
                    file, 1, null));
        }
        walk(plan, file, run, out);
        return out;
    }

    private static void walk(JsonNode node, String file, AuditRun run, List<Finding> out) {
        String type = node.path("Node Type").asText("");
        String relation = node.path("Relation Name").asText("");
        double loops = Math.max(1, node.path("Actual Loops").asDouble(1));
        double removed = node.path("Rows Removed by Filter").asDouble(0) * loops;
        String filter = node.path("Filter").asText("");

        if (type.equals("Seq Scan") && removed >= REMOVED_ROWS) {
            out.add(Ev.finding(run, Category.PERFORMANCE, removed >= 10_000_000 ? Severity.HIGH : Severity.MEDIUM,
                    "evidencia-plano-varredura-sequencial",
                    "Varredura sequencial em " + relation + " descartou " + Ev.fmt(removed) + " linhas",
                    "O plano lê a tabela " + relation + " inteira e joga fora " + Ev.fmt(removed) + " linhas pelo filtro ("
                            + filter + "). Um índice nas colunas do filtro evita ler tudo.",
                    file, 1, filter));
        }
        String sortMethod = node.path("Sort Method").asText("");
        if (type.equals("Sort") && sortMethod.toLowerCase().contains("external")) {
            out.add(Ev.finding(run, Category.PERFORMANCE, Severity.MEDIUM, "evidencia-plano-ordenacao-em-disco",
                    "Ordenação em disco (" + node.path("Sort Space Used").asLong(0) + " kB)",
                    "A ordenação não coube em work_mem e usou disco (" + sortMethod + "). Um índice na ordem pedida, um "
                            + "LIMIT, ou mais work_mem para esta consulta evitam isso.",
                    file, 1, node.path("Sort Key").toString()));
        }
        if (node.path("Hash Batches").asInt(1) > 1) {
            out.add(Ev.finding(run, Category.PERFORMANCE, Severity.LOW, "evidencia-plano-hash-em-disco",
                    "Junção por hash dividida em " + node.path("Hash Batches").asInt() + " lotes",
                    "A tabela hash não coube em work_mem e foi dividida em lotes, gravando em disco. Reduza as linhas "
                            + "que entram na junção ou aumente work_mem.",
                    file, 1, null));
        }
        double planned = node.path("Plan Rows").asDouble(0);
        double actual = node.path("Actual Rows").asDouble(-1);
        if (actual >= 0 && type.length() > 0 && !type.equals("Limit")) {
            double a = actual * loops;
            double hi = Math.max(planned, a);
            double lo = Math.max(1, Math.min(planned, a));
            if (hi >= 10_000 && hi / lo >= ESTIMATE_FACTOR) {
                out.add(Ev.finding(run, Category.PERFORMANCE, Severity.LOW, "evidencia-plano-estimativa-errada",
                        "Estimativa de linhas errada em " + type + (relation.isEmpty() ? "" : " (" + relation + ")"),
                        "O planejador previu " + Ev.fmt(planned) + " linhas e vieram " + Ev.fmt(a) + ". Estatísticas "
                                + "desatualizadas levam a planos ruins: rode ANALYZE, aumente o alvo de estatísticas da "
                                + "coluna ou crie estatísticas estendidas.",
                        file, 1, null));
            }
        }
        for (JsonNode child : node.path("Plans")) {
            walk(child, file, run, out);
        }
    }
}
