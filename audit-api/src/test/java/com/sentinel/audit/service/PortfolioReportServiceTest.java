package com.sentinel.audit.service;

import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.PortfolioReportService.Format;
import com.sentinel.audit.service.PortfolioReportService.SystemSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioReportServiceTest {

    private final PortfolioReportService service = new PortfolioReportService();

    PortfolioReportServiceTest() {
        service.pdf = new PdfReportService();
    }

    private static Finding f(String title, Severity sev, FindingStatus st) {
        Finding f = new Finding();
        f.setTitle(title);
        f.setSeverity(sev);
        f.setCategory(Category.SECURITY);
        f.setStatus(st);
        f.setRuleId("r1");
        f.setFilePath("src/A.java");
        f.setLineNumber(10);
        return f;
    }

    private static TrendPoint p(long id, double overall, long critical) {
        return new TrendPoint(id, "main", LocalDateTime.of(2026, 10, 1, 9, 0), overall, null, null, null, null, null, null,
                Map.of(Severity.CRITICAL, critical));
    }

    private static List<SystemSnapshot> systems() {
        return List.of(
                new SystemSnapshot("Conta <Corrente>", "JAVA", 214L, LocalDateTime.of(2026, 10, 16, 9, 10), 62.0, 3, 5,
                        List.of(f("Injeção de SQL", Severity.CRITICAL, FindingStatus.OPEN),
                                f("=cmd()", Severity.LOW, FindingStatus.OPEN),
                                f("Ruído", Severity.HIGH, FindingStatus.SUPPRESSED)),
                        List.of(p(200, 55, 5), p(214, 62, 3))),
                new SystemSnapshot("Pagamentos", "NODE", 90L, LocalDateTime.of(2026, 10, 15, 9, 10), 88.0, 0, 1,
                        List.of(), List.of(p(90, 88, 0))));
    }

    private String text(ReportService.Type t, Format fmt) {
        return new String(service.render(t, fmt, systems(), 30, 75), StandardCharsets.UTF_8);
    }

    @Test
    void executivoOrdenaDoPiorParaOMelhorEEscapaHtml() {
        String html = text(ReportService.Type.EXECUTIVO, Format.HTML);
        assertTrue(html.indexOf("Conta &lt;Corrente&gt;") < html.indexOf("Pagamentos"));
        assertTrue(html.contains("1 abaixo da meta"));
        assertTrue(html.contains("3 achado(s) crítico(s)"));
        assertTrue(html.contains("<td>+7</td>"));
    }

    @Test
    void executivoCsvTemUmaLinhaPorSistema() {
        String csv = text(ReportService.Type.EXECUTIVO, Format.CSV);
        assertEquals(3, csv.trim().split("\r\n").length);
        assertTrue(csv.startsWith("sistema,stack,auditoria,nota,variacao,criticos,altos,ultima_auditoria"));
    }

    @Test
    void tecnicoIgnoraSuprimidosNoCsvENeutralizaFormulas() {
        String csv = text(ReportService.Type.TECNICO, Format.CSV);
        assertTrue(csv.contains("Injeção de SQL"));
        assertTrue(csv.contains("\"'=cmd()\""));
        assertFalse(csv.contains("Ruído"));
        String html = text(ReportService.Type.TECNICO, Format.HTML);
        assertTrue(html.contains("1 achado(s) crítico(s) e alto(s)"));
    }

    @Test
    void comparativoMostraVariacaoESistemaComUmaAuditoriaSemVariacao() {
        String html = text(ReportService.Type.COMPARATIVO, Format.HTML);
        assertTrue(html.contains("+7"));
        assertTrue(html.contains("últimos 30 dias"));
        String csv = text(ReportService.Type.COMPARATIVO, Format.CSV);
        assertTrue(csv.contains("\"Pagamentos\",1,88,88,,0"));
    }

    @Test
    void pdfDosTresTiposSaiValido() {
        for (ReportService.Type t : ReportService.Type.values()) {
            byte[] pdf = service.render(t, Format.PDF, systems(), null, 75);
            assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
        }
    }

    @Test
    void formatoInvalidoEhRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> Format.parse("docx"));
        assertEquals(Format.HTML, Format.parse(null));
    }
}
