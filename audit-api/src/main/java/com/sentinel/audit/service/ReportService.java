package com.sentinel.audit.service;

import com.sentinel.audit.insights.AreaReport;
import com.sentinel.audit.insights.AuditInsights;
import com.sentinel.audit.insights.InsightsBuilder;
import com.sentinel.audit.insights.InsightsService;
import com.sentinel.audit.insights.RoadmapPhase;

import com.sentinel.audit.dto.FixPlanDto;
import com.sentinel.audit.dto.RemediationDto;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gera o relatório de auditoria em HTML. O CSS de impressão deixa a página pronta para
 * "Imprimir &gt; Salvar como PDF" no navegador (evidência para auditoria/compliance).
 * <p>
 * O score e as contagens consideram só os findings ativos; o que foi suprimido (falso positivo /
 * risco aceito) aparece em seção própria, para que a decisão fique registrada no documento.
 */
@ApplicationScoped
public class ReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public String generateHtmlReport(AuditRun run, List<Finding> findings) {
        List<Finding> active = findings.stream().filter(f -> f.getStatus() != FindingStatus.SUPPRESSED).toList();
        List<Finding> suppressed = findings.stream().filter(f -> f.getStatus() == FindingStatus.SUPPRESSED).toList();

        StringBuilder h = new StringBuilder(8192);
        h.append("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Relatório de Auditoria - ").append(esc(run.getSystem().getName())).append("</title>")
                .append("<style>").append(CSS).append("</style></head><body>");

        h.append("<p class=\"noprint hint\">Para gerar o PDF: Imprimir (Ctrl+P) &rarr; Salvar como PDF.</p>");
        h.append("<h1>Relatório de Auditoria de Código</h1><dl class=\"meta\">")
                .append(meta("Sistema", esc(run.getSystem().getName())))
                .append(meta("Auditoria", "#" + run.getId()))
                .append(meta("Branch", esc(run.getBranch())))
                .append(meta("Executado por", esc(run.getTriggeredBy())))
                .append(meta("Início", run.getStartedAt() != null ? run.getStartedAt().format(DATE_FMT) : "—"))
                .append(meta("Conclusão", run.getCompletedAt() != null ? run.getCompletedAt().format(DATE_FMT) : "—"))
                .append("</dl>");

        h.append("<h2>Resumo executivo</h2><div class=\"summary\">");
        if (run.getRiskScore() != null) {
            var s = run.getRiskScore();
            h.append("<div class=\"score\">").append(s.getOverall()).append("</div>")
                    .append("<p>Score geral (0–100, maior = melhor)</p><table><tr>")
                    .append("<th>Segurança</th><th>Qualidade</th><th>Arquitetura</th><th>Dependências</th><th>Observabilidade</th><th>Desempenho</th></tr><tr>")
                    .append("<td>").append(s.getSecurity()).append("</td><td>").append(s.getQuality()).append("</td><td>")
                    .append(s.getArchitecture()).append("</td><td>").append(s.getDependencies()).append("</td><td>")
                    .append(s.getObservability()).append("</td><td>").append(s.getPerformance() == null ? "—" : s.getPerformance())
                    .append("</td></tr></table>");
        }
        Map<Severity, Long> bySeverity = active.stream()
                .collect(Collectors.groupingBy(Finding::getSeverity, Collectors.counting()));
        h.append("<p class=\"badges\">");
        for (Severity sev : Severity.values()) {
            h.append("<span class=\"badge ").append(sev).append("\">").append(sev).append(": ")
                    .append(bySeverity.getOrDefault(sev, 0L)).append("</span> ");
        }
        long novos = active.stream().filter(f -> Boolean.TRUE.equals(f.getIsNew())).count();
        h.append("</p><p>").append(active.size()).append(" findings ativos");
        if (novos > 0) {
            h.append(", dos quais <strong>").append(novos).append(" novos</strong> desde a auditoria anterior");
        }
        if (!suppressed.isEmpty()) {
            h.append("; ").append(suppressed.size()).append(" suprimidos (fora do score)");
        }
        h.append(".</p></div>");

        appendInsights(h, run, FindingLifecycleService.active(findings));

        h.append("<h2>Findings (").append(active.size()).append(")</h2>");
        if (active.isEmpty()) {
            h.append("<p>Nenhum finding ativo.</p>");
        }
        int i = 1;
        for (Finding f : active.stream().sorted(Comparator.comparing(Finding::getSeverity)).toList()) {
            appendFinding(h, i++, f, true);
        }

        if (!suppressed.isEmpty()) {
            h.append("<h2>Findings suprimidos (").append(suppressed.size()).append(")</h2>")
                    .append("<p>Decisões registradas de falso positivo / risco aceito. Não entram no score.</p>");
            int j = 1;
            for (Finding f : suppressed) {
                appendFinding(h, j++, f, false);
            }
        }

        h.append("<p class=\"foot\">Gerado pelo Sentinel Audit Tool. Ferramentas automáticas geram falsos positivos e não ")
                .append("substituem a revisão humana.</p></body></html>");
        return h.toString();
    }


    // ------------------------------------------------------------------ tipos de relatório

    /** Tipos de relatório HTML oferecidos pela API. */
    public enum Type {
        EXECUTIVO, TECNICO, COMPARATIVO;

        /** Aceita o nome em português ou inglês, sem diferenciar maiúsculas; vazio = técnico. */
        public static Type parse(String raw) {
            if (raw == null || raw.isBlank()) return TECNICO;
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "executivo", "executive" -> EXECUTIVO;
                case "tecnico", "técnico", "technical" -> TECNICO;
                case "comparativo", "comparative" -> COMPARATIVO;
                default -> throw new IllegalArgumentException("Tipo de relatório desconhecido: " + raw
                        + ". Use executivo, tecnico ou comparativo");
            };
        }
    }

    /**
     * Relatório de uma página para quem decide: nota, quanto falta para a meta, riscos principais e o que
     * corrigir primeiro. Não lista achado por achado.
     */
    public String generateExecutiveReport(AuditRun run, List<Finding> findings, FixPlanDto plan, Double scoreDelta, double target) {
        List<Finding> active = FindingLifecycleService.active(findings);
        StringBuilder h = pageStart("Relatório executivo", run);
        h.append("<h1>Relatório executivo</h1><dl class=\"meta\">")
                .append(meta("Sistema", esc(run.getSystem().getName())))
                .append(meta("Auditoria", "#" + run.getId() + " · " + esc(run.getBranch())))
                .append(meta("Concluída em", run.getCompletedAt() != null ? run.getCompletedAt().format(DATE_FMT) : "—"))
                .append("</dl>");
        h.append("<div class=\"summary\">");
        double score = run.getRiskScore() != null && run.getRiskScore().getOverall() != null ? run.getRiskScore().getOverall() : plan.scoreNow();
        h.append("<div class=\"score\">").append(fmt(score)).append("</div><p>Nota geral (0–100, maior = melhor). Meta: ")
                .append(fmt(target)).append(score >= target ? " — <strong>meta atingida</strong>." : " — faltam <strong>"
                        + fmt(target - score) + " pontos</strong>.").append("</p>");
        if (scoreDelta != null) {
            h.append("<p>Em relação à auditoria anterior: <strong>").append(scoreDelta > 0 ? "+" : "").append(fmt(scoreDelta))
                    .append(" ponto(s)</strong>.</p>");
        }
        h.append("</div>");

        Map<Severity, Long> bySeverity = active.stream().collect(Collectors.groupingBy(Finding::getSeverity, Collectors.counting()));
        h.append("<h2>Riscos em aberto</h2><p class=\"badges\">");
        for (Severity sev : Severity.values()) {
            h.append("<span class=\"badge ").append(sev).append("\">").append(sev).append(": ")
                    .append(bySeverity.getOrDefault(sev, 0L)).append("</span> ");
        }
        h.append("</p>");
        if (plan.criticalCount() > 0) {
            h.append("<p>Corrigir os ").append(plan.criticalCount()).append(" crítico(s) leva a nota de ")
                    .append(fmt(plan.scoreNow())).append(" para cerca de <strong>").append(fmt(plan.scoreIfCriticalFixed())).append("</strong>.</p>");
        }

        h.append("<h2>O que corrigir primeiro</h2>");
        if (plan.items().isEmpty()) {
            h.append("<p>Nenhum problema ativo.</p>");
        } else {
            h.append("<table><tr><th>#</th><th>Problema</th><th>Severidade</th><th>Ocorrências</th><th>Ganho na nota</th></tr>");
            int n = 1;
            for (FixPlanDto.Item it : plan.items().stream().limit(5).toList()) {
                h.append("<tr><td>").append(n++).append("</td><td>").append(esc(it.title())).append("</td><td><span class=\"badge ")
                        .append(it.severity()).append("\">").append(it.severity()).append("</span></td><td>").append(it.occurrences())
                        .append("</td><td>+").append(fmt(it.scoreGain())).append("</td></tr>");
            }
            h.append("</table>");
        }
        return pageEnd(h);
    }

    /** Evolução da nota e dos achados entre as auditorias concluídas do sistema no período (mais antiga primeiro). */
    public String generateComparativeReport(AuditRun run, List<TrendPoint> points, Integer days) {
        StringBuilder h = pageStart("Relatório comparativo", run);
        h.append("<h1>Relatório comparativo</h1><dl class=\"meta\">")
                .append(meta("Sistema", esc(run.getSystem().getName())))
                .append(meta("Período", days == null ? "todas as auditorias concluídas" : "últimos " + days + " dias"))
                .append(meta("Auditorias", String.valueOf(points.size())))
                .append("</dl>");
        if (points.size() < 2) {
            h.append("<p class=\"hint\">É preciso ter ao menos duas auditorias concluídas no período para comparar. ")
                    .append("Execute outra auditoria e gere este relatório de novo.</p>");
            return pageEnd(h);
        }
        TrendPoint first = points.get(0);
        TrendPoint last = points.get(points.size() - 1);
        double delta = (last.overall() == null ? 0 : last.overall()) - (first.overall() == null ? 0 : first.overall());
        h.append("<div class=\"summary\"><div class=\"score\">").append(fmt(last.overall())).append("</div><p>Nota atual. Variação no período: <strong>")
                .append(delta > 0 ? "+" : "").append(fmt(delta)).append(" ponto(s)</strong> (de ").append(fmt(first.overall()))
                .append(" para ").append(fmt(last.overall())).append(").</p></div>");
        h.append("<h2>Auditoria a auditoria</h2><table><tr><th>Auditoria</th><th>Branch</th><th>Concluída em</th><th>Nota</th><th>Variação</th>")
                .append("<th>Críticos</th><th>Altos</th><th>Médios</th></tr>");
        Double previous = null;
        for (TrendPoint p : points) {
            Double change = previous == null || p.overall() == null ? null : p.overall() - previous;
            h.append("<tr><td>#").append(p.auditId()).append("</td><td>").append(esc(p.branch())).append("</td><td>")
                    .append(p.completedAt() == null ? "—" : p.completedAt().format(DATE_FMT)).append("</td><td>").append(fmt(p.overall()))
                    .append("</td><td>").append(change == null ? "—" : (change > 0 ? "+" : "") + fmt(change)).append("</td><td>")
                    .append(p.findings().getOrDefault(Severity.CRITICAL, 0L)).append("</td><td>")
                    .append(p.findings().getOrDefault(Severity.HIGH, 0L)).append("</td><td>")
                    .append(p.findings().getOrDefault(Severity.MEDIUM, 0L)).append("</td></tr>");
            previous = p.overall();
        }
        h.append("</table>");
        return pageEnd(h);
    }

    /** Todos os achados ativos em CSV (separador vírgula, UTF-8, campos protegidos contra injeção em planilha). */
    public String generateCsv(List<Finding> findings) {
        StringBuilder sb = new StringBuilder("severidade,categoria,regra,titulo,arquivo,linha,ferramenta,novo\r\n");
        findings.stream().filter(f -> f.getStatus() != FindingStatus.SUPPRESSED)
                .sorted(Comparator.comparing(Finding::getSeverity)).forEach(f -> sb
                        .append(f.getSeverity()).append(',').append(f.getCategory()).append(',')
                        .append(csv(f.getRuleId())).append(',').append(csv(f.getTitle())).append(',')
                        .append(csv(f.getFilePath())).append(',').append(f.getLineNumber() == null ? "" : f.getLineNumber()).append(',')
                        .append(csv(f.getToolSource())).append(',').append(Boolean.TRUE.equals(f.getIsNew()) ? "sim" : "nao").append("\r\n"));
        return sb.toString();
    }

    /** Escapa um campo CSV e neutraliza fórmulas (=, +, -, @) que a planilha executaria ao abrir. */
    static String csv(String value) {
        if (value == null) return "";
        String v = value.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@\t".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    private StringBuilder pageStart(String title, AuditRun run) {
        return new StringBuilder(4096)
                .append("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(title).append(" - ").append(esc(run.getSystem().getName())).append("</title>")
                .append("<style>").append(CSS).append("</style></head><body>")
                .append("<p class=\"noprint hint\">Para gerar o PDF: Imprimir (Ctrl+P) &rarr; Salvar como PDF.</p>");
    }

    private static String pageEnd(StringBuilder h) {
        return h.append("<p class=\"foot\">Gerado pelo Sentinel Audit Tool. Ferramentas automáticas geram falsos positivos e não ")
                .append("substituem a revisão humana.</p></body></html>").toString();
    }

    private static String fmt(Double v) {
        return v == null ? "—" : fmt(v.doubleValue());
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private void appendFinding(StringBuilder h, int n, Finding f, boolean withRemediation) {
        h.append("<section class=\"finding\"><h3><span class=\"badge ").append(f.getSeverity()).append("\">")
                .append(f.getSeverity()).append("</span> ").append(n).append(". ").append(esc(f.getTitle()));
        if (Boolean.TRUE.equals(f.getIsNew())) {
            h.append(" <span class=\"new\">NOVO</span>");
        }
        h.append("</h3><p class=\"where\">").append(f.getCategory()).append(" · ")
                .append(f.getFilePath() != null ? "<code>" + esc(f.getFilePath())
                        + (f.getLineNumber() != null ? ":" + f.getLineNumber() : "") + "</code>" : "—");
        if (f.getCveId() != null && !f.getCveId().isBlank()) {
            h.append(" · ").append(esc(f.getCveId()));
        }
        h.append("</p>");
        if (f.getDescription() != null && !f.getDescription().isBlank()) {
            h.append("<p>").append(esc(f.getDescription())).append("</p>");
        }
        if (f.getCodeSnippet() != null && !f.getCodeSnippet().isBlank()) {
            h.append("<pre>").append(esc(f.getCodeSnippet())).append("</pre>");
        }
        if (withRemediation) {
            RemediationDto r = RemediationCatalog.forFinding(f);
            if (r != null) {
                h.append("<div class=\"fix\"><strong>Como corrigir:</strong> ").append(esc(r.summary()));
                if (r.before() != null && !r.before().isBlank()) {
                    h.append("<pre class=\"bad\">").append(esc(r.before())).append("</pre>");
                }
                if (r.after() != null && !r.after().isBlank()) {
                    h.append("<pre class=\"good\">").append(esc(r.after())).append("</pre>");
                }
                if (r.references() != null) {
                    for (String ref : r.references()) {
                        h.append("<div class=\"ref\">").append(esc(ref)).append("</div>");
                    }
                }
                h.append("</div>");
            }
        }
        h.append("</section>");
    }

    /** Auditoria completa: parecer, áreas, dívida técnica priorizada, quick wins, plano em fases e limites. */
    private void appendInsights(StringBuilder h, AuditRun run, List<Finding> active) {
        AuditInsights in = InsightsBuilder.build(run.getSystem().getName(), active, InsightsService.parseContext(run.getContextJson()));
        h.append("<h2>Auditoria completa</h2><div class=\"summary\"><p><strong>").append(esc(in.verdict())).append("</strong></p><p>")
                .append(esc(in.executiveSummary())).append("</p></div>");

        h.append("<h3>Por área</h3><table><tr><th>Área</th><th>Estado</th><th>Achados</th><th>Situação</th></tr>");
        for (AreaReport a : in.areas()) {
            h.append("<tr><td>").append(esc(a.label())).append("</td><td>").append(stateLabel(a.state())).append("</td><td>")
                    .append(a.total()).append("</td><td>").append(esc(a.currentState())).append("</td></tr>");
        }
        h.append("</table>");

        if (!in.debtTable().isEmpty()) {
            h.append("<h3>Dívida técnica (").append(in.debtTable().size()).append(" problemas)</h3>")
                    .append("<table><tr><th>Prioridade</th><th>Problema</th><th>Impacto</th><th>Esforço</th><th>Risco de regressão</th><th>Ocorrências</th></tr>");
            in.debtTable().stream().limit(25).forEach(d -> h.append("<tr><td>").append(d.priority()).append(" ")
                    .append(esc(d.priorityLabel())).append("</td><td>").append(esc(d.problem())).append("<br><small>")
                    .append(esc(d.areaLabel())).append(d.example() == null ? "" : " · " + esc(d.example())).append("</small></td><td>")
                    .append(esc(d.impact())).append("</td><td>").append(d.effort()).append(d.effortEstimated() ? " (estimado)" : "")
                    .append("</td><td>").append(d.regressionRisk() == null ? "—" : d.regressionRisk()).append("</td><td>")
                    .append(d.occurrences()).append("</td></tr>"));
            h.append("</table>");
            if (in.debtTable().size() > 25) {
                h.append("<p class=\"hint\">Mostrando 25 de ").append(in.debtTable().size()).append(" problemas; a lista completa está na API (/insights).</p>");
            }
        }
        if (!in.quickWins().isEmpty()) {
            h.append("<h3>Quick wins (esforço pequeno, prioridade até P2)</h3><ul>");
            in.quickWins().forEach(d -> h.append("<li><strong>").append(esc(d.problem())).append("</strong> — ")
                    .append(esc(d.recommendation())).append("</li>"));
            h.append("</ul>");
        }
        h.append("<h3>Plano de ação</h3><ol>");
        for (RoadmapPhase p : in.roadmap()) {
            h.append("<li><strong>Fase ").append(p.number()).append(" — ").append(esc(p.name())).append("</strong> (")
                    .append(p.total()).append(" item(ns)): ").append(esc(p.goal())).append("</li>");
        }
        h.append("</ol>");
        h.append("<h3>Não foi possível verificar</h3><p class=\"hint\">Análise estática não alcança estes pontos; ")
                .append("não os trate como aprovados.</p><ul>");
        in.unverified().forEach(u -> h.append("<li>").append(esc(u)).append("</li>"));
        h.append("</ul>");
    }

    private static String stateLabel(String state) {
        return switch (state) {
            case "CRITICO" -> "Crítico";
            case "ATENCAO" -> "Atenção";
            case "SEM_ACHADOS" -> "Sem achados";
            default -> "Não verificado";
        };
    }

    private static String meta(String k, String v) {
        return "<dt>" + k + "</dt><dd>" + v + "</dd>";
    }

    private static String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final String CSS =
            "body{font-family:Arial,sans-serif;margin:32px;color:#222;line-height:1.4}"
            + "h1{color:#003641}h2{color:#00525f;border-bottom:2px solid #00525f;padding-bottom:4px;margin-top:32px}"
            + "h3{font-size:15px;margin:0 0 4px}.hint{background:#fff8e1;padding:8px;border-radius:4px;font-size:13px}"
            + ".meta{display:grid;grid-template-columns:140px 1fr;gap:2px 12px;font-size:14px}.meta dt{font-weight:bold}.meta dd{margin:0}"
            + ".summary{background:#f4f7fb;border:1px solid #cdd9e8;border-radius:6px;padding:16px}"
            + ".score{font-size:44px;font-weight:bold;color:#00525f}"
            + ".badge{display:inline-block;padding:1px 7px;border-radius:4px;font-size:12px;font-weight:bold;color:#fff}"
            + ".CRITICAL{background:#c0392b}.HIGH{background:#d35400}.MEDIUM{background:#b7950b}.LOW{background:#1e8449}.INFO{background:#2471a3}"
            + ".new{background:#6a1b9a;color:#fff;border-radius:4px;font-size:11px;padding:1px 6px}"
            + "table{width:100%;border-collapse:collapse}th{background:#00525f;color:#fff;padding:8px;text-align:left}td{padding:6px 8px;border-bottom:1px solid #ddd}"
            + ".finding{border:1px solid #ddd;border-radius:6px;padding:10px 14px;margin:10px 0;break-inside:avoid}"
            + ".where{color:#555;font-size:13px;margin:2px 0 8px}pre{background:#f5f5f5;padding:8px;border-radius:4px;overflow:auto;white-space:pre-wrap;font-size:12px}"
            + ".fix{background:#f1f8f4;border-left:4px solid #1e8449;padding:8px 12px;margin-top:8px;font-size:13px}"
            + ".bad{border-left:3px solid #c0392b}.good{border-left:3px solid #1e8449}.ref{font-size:12px;color:#1565c0;word-break:break-all}"
            + ".foot{margin-top:32px;font-size:12px;color:#666}"
            + "@media print{body{margin:12mm}.noprint{display:none}h2{break-after:avoid}a{color:inherit}}";
}
