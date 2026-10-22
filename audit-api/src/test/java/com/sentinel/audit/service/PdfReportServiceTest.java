package com.sentinel.audit.service;

import com.sentinel.audit.dto.FixPlanDto;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.model.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfReportServiceTest {

    private static AuditRun run() {
        AuditedSystem s = new AuditedSystem();
        s.setName("Conta Corrente — API");
        AuditRun r = new AuditRun();
        r.setId(7L);
        r.setSystem(s);
        r.setBranch("main");
        r.setTriggeredBy("joão");
        r.setStartedAt(LocalDateTime.of(2026, 10, 17, 10, 0));
        return r;
    }

    private static Finding f(String title, Severity sev, FindingStatus st) {
        Finding f = new Finding();
        f.setTitle(title);
        f.setSeverity(sev);
        f.setCategory(Category.SECURITY);
        f.setStatus(st);
        f.setRuleId("r");
        f.setFilePath("src/A.java");
        f.setLineNumber(10);
        return f;
    }

    private static void assertPdf(byte[] pdf) {
        assertTrue(pdf.length > 500, "PDF não pode ser vazio");
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
    }

    @Test
    void tecnicoGeraPdfValidoComAcentosEEmoji() {
        assertPdf(new PdfReportService().technical(run(), List.of(
                f("Injeção de SQL 😀 → grave", Severity.CRITICAL, FindingStatus.OPEN),
                f("Ruído", Severity.LOW, FindingStatus.SUPPRESSED))));
    }

    @Test
    void tecnicoSemFindingsTambemGera() {
        assertPdf(new PdfReportService().technical(run(), List.of()));
    }

    @Test
    void executivoEComparativoGeramPdf() {
        FixPlanDto plan = new FixPlanDto(60, 71, 1, List.of(
                new FixPlanDto.Item("r", "SQL por concatenação", Severity.CRITICAL, Category.SECURITY, 3, 4.5, "A.java", 3, "use prepared")));
        assertPdf(new PdfReportService().executive(run(), List.of(f("x", Severity.CRITICAL, FindingStatus.OPEN)), plan, 2.0, 80));

        List<TrendPoint> pts = List.of(
                new TrendPoint(1L, "main", LocalDateTime.of(2026, 9, 1, 10, 0), 50.0, 1.0, 1.0, 1.0, 1.0, 1.0, null, Map.of(Severity.CRITICAL, 2L)),
                new TrendPoint(2L, "main", LocalDateTime.of(2026, 10, 1, 10, 0), 70.0, 1.0, 1.0, 1.0, 1.0, 1.0, null, Map.of()));
        assertPdf(new PdfReportService().comparative(run(), pts, 30));
        assertPdf(new PdfReportService().comparative(run(), pts.subList(0, 1), null));
    }

    @Test
    void limpaCaracteresForaDoLatin1() {
        assertEquals("a-b?c", PdfReportService.clean("a—b→c"));
        assertEquals("ção", PdfReportService.clean("ção"));
    }
}
