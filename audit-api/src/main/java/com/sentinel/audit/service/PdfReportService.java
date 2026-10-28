package com.sentinel.audit.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sentinel.audit.dto.FixPlanDto;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import jakarta.enterprise.context.ApplicationScoped;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Versão em PDF dos relatórios, gerada no servidor (sem navegador nem serviço externo).
 * Mesmo conteúdo essencial do HTML, em layout simples de A4.
 */
@ApplicationScoped
public class PdfReportService {

    private static final float[] FINDINGS_COLUMNS = {1.2f, 4f, 3.5f, 1.3f};
    private static final float[] FIX_PLAN_COLUMNS = {0.6f, 5f, 1.5f, 1.4f, 1.5f};
    private static final int FIX_PLAN_ITEMS = 5;
    private static final float PAGE_MARGIN = 40;
    private static final float H2_SPACING_BEFORE = 14;
    private static final float H2_SPACING_AFTER = 6;
    private static final float TABLE_SPACING_BEFORE = 4;
    private static final float CELL_PADDING = 4;
    /** Primeiro caractere imprimível do ASCII e o DEL; acima de 0xFF a fonte padrão do PDF não tem glifo. */
    private static final char FIRST_PRINTABLE = 0x20;
    private static final char DEL = 0x7f;
    private static final char LAST_LATIN1 = 0xFF;


    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
    private static final Font TH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TD = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Color HEAD_BG = new Color(0x16, 0x16, 0x16);

    // ------------------------------------------------------------------ técnico

    public byte[] technical(AuditRun run, List<Finding> findings) {
        List<Finding> active = FindingLifecycleService.active(findings);
        List<Finding> suppressed = findings.stream().filter(f -> f.getStatus() == FindingStatus.SUPPRESSED).toList();
        return build("Relatório de Auditoria de Código", run, doc -> {
            meta(doc, run);
            doc.add(h2("Resumo"));
            if (run.getRiskScore() != null && run.getRiskScore().getOverall() != null) {
                var s = run.getRiskScore();
                doc.add(new Paragraph("Score geral: " + fmt(s.getOverall()) + " (0-100, maior = melhor)", BOLD));
                table(doc, List.of("Segurança", "Qualidade", "Arquitetura", "Dependências", "Observabilidade", "Desempenho"),
                        List.of(List.of(fmt(s.getSecurity()), fmt(s.getQuality()), fmt(s.getArchitecture()),
                                fmt(s.getDependencies()), fmt(s.getObservability()), fmt(s.getPerformance()))));
            }
            doc.add(new Paragraph(severityLine(active) + " | " + active.size() + " findings ativos"
                    + (suppressed.isEmpty() ? "" : "; " + suppressed.size() + " suprimidos (fora do score)"), BODY));

            List<Finding> reliable = active.stream().filter(f -> !ReportService.isLowConfidence(f)).toList();
            List<Finding> lowConfidence = active.stream().filter(ReportService::isLowConfidence).toList();
            doc.add(h2("Findings (" + reliable.size() + ")"));
            if (active.isEmpty()) {
                doc.add(new Paragraph("Nenhum finding ativo.", BODY));
            }
            findingsTable(doc, reliable);
            if (!lowConfidence.isEmpty()) {
                doc.add(h2("Baixa confiança (" + lowConfidence.size() + ")"));
                doc.add(new Paragraph("Provavelmente não são problemas: testes, exemplos, documentação, valores de exemplo "
                        + "ou regras com muitos falsos positivos. Revise por amostragem.", BODY));
                findingsTable(doc, lowConfidence);
            }
            if (!suppressed.isEmpty()) {
                doc.add(h2("Findings suprimidos (" + suppressed.size() + ")"));
                doc.add(new Paragraph("Decisões registradas de falso positivo / risco aceito. Não entram no score.", BODY));
                findingsTable(doc, suppressed);
            }
            doc.add(new Paragraph("Gerado pelo Sentinel Audit Tool. Ferramentas automáticas geram falsos positivos e não "
                    + "substituem a revisão humana.", SMALL));
        });
    }

    private void findingsTable(Document doc, List<Finding> list) throws DocumentException {
        List<List<String>> rows = list.stream()
                .sorted(Comparator.comparing(Finding::getSeverity))
                .map(f -> List.of(String.valueOf(f.getSeverity()), nz(f.getTitle()),
                        nz(f.getFilePath()) + (f.getLineNumber() == null ? "" : ":" + f.getLineNumber()),
                        nz(f.getToolSource())))
                .toList();
        if (!rows.isEmpty()) {
            table(doc, List.of("Severidade", "Problema", "Local", "Ferramenta"), rows, FINDINGS_COLUMNS);
        }
    }

    // ------------------------------------------------------------------ executivo

    public byte[] executive(AuditRun run, List<Finding> findings, FixPlanDto plan, Double scoreDelta, double target) {
        List<Finding> active = FindingLifecycleService.active(findings);
        return build("Relatório executivo", run, doc -> {
            meta(doc, run);
            double score = run.getRiskScore() != null && run.getRiskScore().getOverall() != null
                    ? run.getRiskScore().getOverall() : plan.scoreNow();
            doc.add(new Paragraph("Nota geral: " + fmt(score) + " (0-100, maior = melhor). Meta: " + fmt(target)
                    + (score >= target ? " - meta atingida." : " - faltam " + fmt(target - score) + " pontos."), BOLD));
            if (scoreDelta != null) {
                doc.add(new Paragraph("Em relação à auditoria anterior: " + (scoreDelta > 0 ? "+" : "") + fmt(scoreDelta)
                        + " ponto(s).", BODY));
            }
            doc.add(h2("Riscos em aberto"));
            doc.add(new Paragraph(severityLine(active), BODY));
            if (plan.criticalCount() > 0) {
                doc.add(new Paragraph("Corrigir os " + plan.criticalCount() + " crítico(s) leva a nota de "
                        + fmt(plan.scoreNow()) + " para cerca de " + fmt(plan.scoreIfCriticalFixed()) + ".", BODY));
            }
            doc.add(h2("O que corrigir primeiro"));
            if (plan.items().isEmpty()) {
                doc.add(new Paragraph("Nenhum problema ativo.", BODY));
                return;
            }
            List<List<String>> rows = new java.util.ArrayList<>();
            int n = 1;
            for (FixPlanDto.Item it : plan.items().stream().limit(FIX_PLAN_ITEMS).toList()) {
                rows.add(List.of(String.valueOf(n++), nz(it.title()), String.valueOf(it.severity()),
                        String.valueOf(it.occurrences()), "+" + fmt(it.scoreGain())));
            }
            table(doc, List.of("#", "Problema", "Severidade", "Ocorrências", "Ganho na nota"), rows,
                    FIX_PLAN_COLUMNS);
        });
    }

    // ------------------------------------------------------------------ comparativo

    public byte[] comparative(AuditRun run, List<TrendPoint> points, Integer days) {
        return build("Relatório comparativo", run, doc -> {
            doc.add(new Paragraph("Sistema: " + nz(run.getSystem().getName()), BODY));
            doc.add(new Paragraph("Período: " + (days == null ? "todas as auditorias concluídas" : "últimos " + days + " dias")
                    + " | Auditorias: " + points.size(), BODY));
            if (points.size() < 2) {
                doc.add(new Paragraph("É preciso ter ao menos duas auditorias concluídas no período para comparar.", BODY));
                return;
            }
            TrendPoint first = points.get(0);
            TrendPoint last = points.get(points.size() - 1);
            double delta = (last.overall() == null ? 0 : last.overall()) - (first.overall() == null ? 0 : first.overall());
            doc.add(new Paragraph("Nota atual: " + fmt(last.overall()) + ". Variação no período: " + (delta > 0 ? "+" : "")
                    + fmt(delta) + " ponto(s) (de " + fmt(first.overall()) + " para " + fmt(last.overall()) + ").", BOLD));
            doc.add(h2("Auditoria a auditoria"));
            List<List<String>> rows = new java.util.ArrayList<>();
            Double previous = null;
            for (TrendPoint p : points) {
                Double change = previous == null || p.overall() == null ? null : p.overall() - previous;
                rows.add(List.of("#" + p.auditId(), nz(p.branch()),
                        p.completedAt() == null ? "-" : p.completedAt().format(DATE_FMT), fmt(p.overall()),
                        change == null ? "-" : (change > 0 ? "+" : "") + fmt(change),
                        String.valueOf(p.findings().getOrDefault(Severity.CRITICAL, 0L)),
                        String.valueOf(p.findings().getOrDefault(Severity.HIGH, 0L)),
                        String.valueOf(p.findings().getOrDefault(Severity.MEDIUM, 0L))));
                previous = p.overall();
            }
            table(doc, List.of("Auditoria", "Branch", "Concluída em", "Nota", "Variação", "Críticos", "Altos", "Médios"), rows);
        });
    }

    /** Seção genérica de tabela, usada pelos relatórios consolidados (todos os sistemas). */
    public record TableSection(String heading, List<String> heads, List<List<String>> rows) {}

    public byte[] tables(String title, List<String> intro, List<TableSection> sections) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle(clean(title));
            doc.addCreator("Sentinel Audit Tool");
            doc.open();
            doc.add(new Paragraph(clean(title), H1));
            for (String line : intro) {
                doc.add(new Paragraph(clean(line), BODY));
            }
            for (TableSection s : sections) {
                doc.add(h2(s.heading()));
                if (s.rows().isEmpty()) {
                    doc.add(new Paragraph("Nada a mostrar.", BODY));
                } else {
                    table(doc, s.heads(), s.rows());
                }
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar o PDF: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ infraestrutura

    @FunctionalInterface
    private interface Body {
        void write(Document doc) throws DocumentException;
    }

    private byte[] build(String title, AuditRun run, Body body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle(clean(title + " - " + run.getSystem().getName()));
            doc.addCreator("Sentinel Audit Tool");
            doc.open();
            doc.add(new Paragraph(clean(title), H1));
            body.write(doc);
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar o PDF: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    private void meta(Document doc, AuditRun run) throws DocumentException {
        doc.add(new Paragraph("Sistema: " + clean(nz(run.getSystem().getName())), BODY));
        doc.add(new Paragraph("Auditoria #" + run.getId() + " | Branch: " + clean(nz(run.getBranch()))
                + " | Commit: " + clean(ReportService.shortSha(run.getCommitSha()))
                + " | Executado por: " + clean(nz(run.getTriggeredBy())), BODY));
        doc.add(new Paragraph("Início: " + (run.getStartedAt() == null ? "-" : run.getStartedAt().format(DATE_FMT))
                + " | Conclusão: " + (run.getCompletedAt() == null ? "-" : run.getCompletedAt().format(DATE_FMT)), BODY));
    }

    private static Paragraph h2(String text) {
        Paragraph p = new Paragraph(clean(text), H2);
        p.setSpacingBefore(H2_SPACING_BEFORE);
        p.setSpacingAfter(H2_SPACING_AFTER);
        return p;
    }

    private static void table(Document doc, List<String> heads, List<List<String>> rows) throws DocumentException {
        float[] w = new float[heads.size()];
        java.util.Arrays.fill(w, 1f);
        table(doc, heads, rows, w);
    }

    private static void table(Document doc, List<String> heads, List<List<String>> rows, float[] widths) throws DocumentException {
        PdfPTable t = new PdfPTable(heads.size());
        t.setWidthPercentage(100);
        t.setWidths(widths);
        t.setSpacingBefore(TABLE_SPACING_BEFORE);
        t.setHeaderRows(1);
        for (String h : heads) {
            PdfPCell c = new PdfPCell(new Phrase(clean(h), TH));
            c.setBackgroundColor(HEAD_BG);
            c.setPadding(CELL_PADDING);
            t.addCell(c);
        }
        for (List<String> row : rows) {
            for (String v : row) {
                PdfPCell c = new PdfPCell(new Phrase(clean(v), TD));
                c.setPadding(CELL_PADDING);
                c.setHorizontalAlignment(Element.ALIGN_LEFT);
                t.addCell(c);
            }
        }
        doc.add(t);
    }

    private static String severityLine(List<Finding> active) {
        StringBuilder sb = new StringBuilder();
        for (Severity sev : Severity.values()) {
            long n = active.stream().filter(f -> f.getSeverity() == sev).count();
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(sev).append(": ").append(n);
        }
        return sb.toString();
    }

    private static String fmt(Double v) {
        return v == null ? "-" : fmt(v.doubleValue());
    }

    private static String fmt(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", v);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** As fontes padrão do PDF cobrem Latin-1; o que estiver fora vira '?' e controles são removidos. */
    static String clean(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\t') {
                sb.append(c);
            } else if (c < FIRST_PRINTABLE || c == DEL) {
                continue;
            } else if (c > LAST_LATIN1) {
                sb.append(c == '–' || c == '—' ? '-' : '?');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
