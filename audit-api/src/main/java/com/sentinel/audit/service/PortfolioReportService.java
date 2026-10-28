package com.sentinel.audit.service;

import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Relatórios consolidados de todos os sistemas (a opção "Todos os sistemas" da tela de relatórios):
 * executivo (nota e riscos por sistema), técnico (achados críticos e altos) e comparativo (evolução no período).
 * Saem em HTML, PDF ou CSV.
 */
@ApplicationScoped
public class PortfolioReportService {

    /** Sistema sem nota fica depois de todos na ordenação. */
    private static final int UNSCORED_SORT_VALUE = 101;
    private static final int REPORT_BUFFER_CHARS = 4096;


    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Inject
    PdfReportService pdf;

    /** Situação de um sistema: última auditoria concluída, achados dela e os pontos do período. */
    public record SystemSnapshot(String name, String stack, Long auditId, LocalDateTime completedAt, Double overall,
                                 long critical, long high, List<Finding> findings, List<TrendPoint> points) {}

    public enum Format {
        HTML, PDF, CSV;

        public static Format parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return HTML;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Formato inválido: " + raw + ". Use html, pdf ou csv.", e);
            }
        }
    }

    private record Doc(String title, List<String> intro, List<PdfReportService.TableSection> sections, String csv) {}

    public byte[] render(ReportService.Type type, Format format, List<SystemSnapshot> systems, Integer days, double target) {
        Doc doc = switch (type) {
            case EXECUTIVO -> executive(systems, target);
            case TECNICO -> technical(systems);
            case COMPARATIVO -> comparative(systems, days);
        };
        return switch (format) {
            case CSV -> doc.csv().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            case PDF -> pdf.tables(doc.title(), doc.intro(), doc.sections());
            case HTML -> html(doc).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        };
    }

    // ------------------------------------------------------------------ conteúdo

    private Doc executive(List<SystemSnapshot> systems, double target) {
        List<SystemSnapshot> sorted = new ArrayList<>(systems);
        sorted.sort(Comparator.comparing((SystemSnapshot s) -> s.overall() == null ? UNSCORED_SORT_VALUE : s.overall()));
        long below = sorted.stream().filter(s -> s.overall() != null && s.overall() < target).count();
        long critical = sorted.stream().mapToLong(SystemSnapshot::critical).sum();
        List<List<String>> rows = new ArrayList<>();
        StringBuilder csv = new StringBuilder("sistema,stack,auditoria,nota,variacao,criticos,altos,ultima_auditoria\r\n");
        for (SystemSnapshot s : sorted) {
            Double change = change(s.points());
            String changeText = change == null ? "-" : (change > 0 ? "+" : "") + fmt(change);
            rows.add(List.of(s.name(), nz(s.stack()), "#" + s.auditId(), fmt(s.overall()), changeText,
                    String.valueOf(s.critical()), String.valueOf(s.high()), date(s.completedAt())));
            csv.append(ReportService.csv(s.name())).append(',').append(ReportService.csv(s.stack())).append(',')
                    .append(s.auditId()).append(',').append(fmt(s.overall())).append(',')
                    .append(change == null ? "" : fmt(change)).append(',').append(s.critical()).append(',')
                    .append(s.high()).append(',').append(date(s.completedAt())).append("\r\n");
        }
        List<String> intro = List.of(
                systems.size() + " sistema(s) com auditoria concluída. Meta da organização: " + fmt(target) + ".",
                below + " abaixo da meta; " + critical + " achado(s) crítico(s) no total. "
                        + "Variação = mudança da nota dentro do período escolhido.");
        return new Doc("Relatório executivo - todos os sistemas", intro,
                List.of(new PdfReportService.TableSection("Sistemas, do menor para o maior risco",
                        List.of("Sistema", "Stack", "Auditoria", "Nota", "Variação", "Críticos", "Altos", "Última auditoria"), rows)),
                csv.toString());
    }

    private Doc technical(List<SystemSnapshot> systems) {
        List<PdfReportService.TableSection> sections = new ArrayList<>();
        StringBuilder csv = new StringBuilder("sistema,auditoria,severidade,categoria,regra,titulo,arquivo,linha,novo\r\n");
        long total = 0;
        for (SystemSnapshot s : systems) {
            List<Finding> items = s.findings().stream()
                    .filter(f -> f.getStatus() != FindingStatus.SUPPRESSED)
                    .sorted(Comparator.comparing(Finding::getSeverity)).toList();
            List<List<String>> rows = new ArrayList<>();
            for (Finding f : items) {
                String line = f.getLineNumber() == null ? "" : String.valueOf(f.getLineNumber());
                if (f.getSeverity() == Severity.CRITICAL || f.getSeverity() == Severity.HIGH) {
                    rows.add(List.of(String.valueOf(f.getSeverity()), nz(f.getRuleId()), nz(f.getTitle()),
                            nz(f.getFilePath()) + (line.isEmpty() ? "" : ":" + line)));
                    total++;
                }
                csv.append(ReportService.csv(s.name())).append(',').append(s.auditId()).append(',')
                        .append(f.getSeverity()).append(',').append(f.getCategory()).append(',')
                        .append(ReportService.csv(f.getRuleId())).append(',').append(ReportService.csv(f.getTitle())).append(',')
                        .append(ReportService.csv(f.getFilePath())).append(',').append(line).append(',')
                        .append(Boolean.TRUE.equals(f.getIsNew()) ? "sim" : "nao").append("\r\n");
            }
            sections.add(new PdfReportService.TableSection(s.name() + " - auditoria #" + s.auditId(),
                    List.of("Severidade", "Regra", "Achado", "Local"), rows));
        }
        return new Doc("Relatório técnico - todos os sistemas",
                List.of(systems.size() + " sistema(s); " + total + " achado(s) crítico(s) e alto(s) abertos. "
                        + "A planilha traz todas as severidades."),
                sections, csv.toString());
    }

    private Doc comparative(List<SystemSnapshot> systems, Integer days) {
        String period = days == null ? "todo o histórico" : "últimos " + days + " dias";
        List<List<String>> rows = new ArrayList<>();
        StringBuilder csv = new StringBuilder("sistema,auditorias,nota_inicial,nota_atual,variacao,criticos_atuais\r\n");
        for (SystemSnapshot s : systems) {
            List<TrendPoint> p = s.points();
            if (p.isEmpty()) {
                continue;
            }
            Double first = p.get(0).overall();
            Double last = p.get(p.size() - 1).overall();
            Double delta = first == null || last == null || p.size() < 2 ? null : last - first;
            long crit = p.get(p.size() - 1).findings().getOrDefault(Severity.CRITICAL, 0L);
            String d = delta == null ? "-" : (delta > 0 ? "+" : "") + fmt(delta);
            rows.add(List.of(s.name(), String.valueOf(p.size()), fmt(first), fmt(last), d, String.valueOf(crit)));
            csv.append(ReportService.csv(s.name())).append(',').append(p.size()).append(',').append(fmt(first)).append(',')
                    .append(fmt(last)).append(',').append(delta == null ? "" : fmt(delta)).append(',').append(crit).append("\r\n");
        }
        return new Doc("Relatório comparativo - todos os sistemas",
                List.of("Período: " + period + ". Variação = nota da última auditoria menos a da primeira no período; "
                        + "sistemas com uma só auditoria não têm variação."),
                List.of(new PdfReportService.TableSection("Evolução por sistema",
                        List.of("Sistema", "Auditorias", "Nota inicial", "Nota atual", "Variação", "Críticos atuais"), rows)),
                csv.toString());
    }

    // ------------------------------------------------------------------ HTML

    private String html(Doc doc) {
        StringBuilder h = new StringBuilder(REPORT_BUFFER_CHARS)
                .append("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(ReportService.esc(doc.title())).append("</title>")
                .append("<style>").append(ReportService.CSS).append("</style></head><body>")
                .append("<p class=\"noprint hint\">Para gerar o PDF: Imprimir (Ctrl+P) &rarr; Salvar como PDF.</p>")
                .append("<h1>").append(ReportService.esc(doc.title())).append("</h1>");
        for (String line : doc.intro()) {
            h.append("<p>").append(ReportService.esc(line)).append("</p>");
        }
        for (PdfReportService.TableSection s : doc.sections()) {
            h.append("<h2>").append(ReportService.esc(s.heading())).append("</h2>");
            if (s.rows().isEmpty()) {
                h.append("<p class=\"hint\">Nada a mostrar.</p>");
                continue;
            }
            h.append("<table><tr>");
            s.heads().forEach(c -> h.append("<th>").append(ReportService.esc(c)).append("</th>"));
            h.append("</tr>");
            for (List<String> row : s.rows()) {
                h.append("<tr>");
                row.forEach(c -> h.append("<td>").append(ReportService.esc(c)).append("</td>"));
                h.append("</tr>");
            }
            h.append("</table>");
        }
        return h.append("<p class=\"foot\">Gerado pelo Sentinel Audit Tool. Ferramentas automáticas geram falsos positivos e não ")
                .append("substituem a revisão humana.</p></body></html>").toString();
    }

    /** Nota da última auditoria do período menos a da primeira; vazio com menos de duas auditorias. */
    private static Double change(List<TrendPoint> points) {
        if (points.size() < 2) {
            return null;
        }
        Double first = points.get(0).overall();
        Double last = points.get(points.size() - 1).overall();
        return first == null || last == null ? null : last - first;
    }

    private static String date(LocalDateTime t) {
        return t == null ? "-" : t.format(DATE_FMT);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String fmt(Double v) {
        return v == null ? "-" : fmt(v.doubleValue());
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
