package com.sentinel.audit.cli;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resumo em Markdown para comentar em um PR (o CLI só grava o arquivo; quem posta é o pipeline).
 * Mostra apenas o que conta: achados em modo sombra e suprimidos ficam de fora (só aparece quantos são).
 */
final class MarkdownWriter {

    private static final int CELL_MAX_CHARS = 160;


    private static final int MAX_ROWS = 25;

    private MarkdownWriter() {}

    static String write(JsonNode findings, String title, String baseRef) {
        List<JsonNode> active = new ArrayList<>();
        int shadow = 0;
        for (JsonNode f : findings) {
            if ("SUPPRESSED".equals(f.path("status").asText(""))) {
                continue;
            }
            if (f.path("shadow").asBoolean(false)) {
                shadow++;
                continue;
            }
            active.add(f);
        }
        active.sort(Comparator.comparingInt((JsonNode f) -> -AuditCli.severityRank(f.path("severity").asText(""))));

        StringBuilder md = new StringBuilder();
        md.append("### ").append(title).append(baseRef == null ? "" : " — alterações em relação a `" + escCode(baseRef) + "`").append("\n\n");
        if (active.isEmpty()) {
            md.append("Nenhum problema encontrado");
            md.append(baseRef == null ? "" : " nos arquivos alterados").append(". ✅\n");
        } else {
            long crit = active.stream().filter(f -> AuditCli.severityRank(f.path("severity").asText("")) >= AuditCli.RANK_CRITICAL).count();
            long high = active.stream().filter(f -> AuditCli.severityRank(f.path("severity").asText("")) == AuditCli.RANK_HIGH).count();
            md.append("**").append(active.size()).append(" problema(s)**");
            md.append(" — ").append(crit).append(" crítico(s), ").append(high).append(" alto(s)\n\n");
            md.append("| Severidade | Regra | Local | Problema |\n|---|---|---|---|\n");
            int shown = 0;
            for (JsonNode f : active) {
                if (shown >= MAX_ROWS) {
                    break;
                }
                shown++;
                String file = f.path("filePath").asText("");
                int line = f.path("lineNumber").asInt(0);
                md.append("| ").append(cell(f.path("severity").asText("")))
                  .append(" | `").append(escCode(f.path("ruleId").asText(""))).append("` | ")
                  .append(file.isEmpty() ? "—" : "`" + escCode(file + (line > 0 ? ":" + line : "")) + "`")
                  .append(" | ").append(cell(f.path("title").asText(""))).append(" |\n");
            }
            if (active.size() > MAX_ROWS) {
                md.append("\n… e mais ").append(active.size() - MAX_ROWS).append(" no relatório completo.\n");
            }
        }
        if (shadow > 0) {
            md.append("\n_").append(shadow).append(" achado(s) em modo sombra (não contam para o resultado)._\n");
        }
        return md.toString();
    }

    /** Texto vindo do repositório auditado não pode virar Markdown/HTML ativo no comentário. */
    private static String cell(String s) {
        String t = s.replace("\r", " ").replace("\n", " ");
        if (t.length() > CELL_MAX_CHARS) {
            t = t.substring(0, CELL_MAX_CHARS - "...".length()) + "...";
        }
        return t.replace("\\", "\\\\").replace("|", "\\|").replace("<", "&lt;").replace(">", "&gt;")
                .replace("`", "'").replace("[", "\\[").replace("]", "\\]").replace("@", "@​");
    }

    private static String escCode(String s) {
        return s.replace("`", "'").replace("|", "/").replace("\n", " ").replace("\r", " ");
    }
}
