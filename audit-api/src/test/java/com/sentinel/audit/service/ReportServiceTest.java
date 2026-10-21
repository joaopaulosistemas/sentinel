package com.sentinel.audit.service;

import com.sentinel.audit.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {

    private static AuditRun run() {
        AuditedSystem s = new AuditedSystem();
        s.setName("Portal <script>");
        AuditRun r = new AuditRun();
        r.setId(7L);
        r.setSystem(s);
        r.setBranch("main");
        r.setTriggeredBy("joao");
        r.setStartedAt(LocalDateTime.of(2026, 10, 17, 10, 0));
        return r;
    }

    private static Finding f(String title, Severity sev, FindingStatus st, Boolean isNew, String rule) {
        Finding f = new Finding();
        f.setTitle(title);
        f.setSeverity(sev);
        f.setCategory(Category.SECURITY);
        f.setStatus(st);
        f.setIsNew(isNew);
        f.setRuleId(rule);
        f.setFilePath("src/A.java");
        f.setLineNumber(10);
        return f;
    }

    @Test
    void separaAtivosDeSuprimidosEMarcaNovos() {
        String html = new ReportService().generateHtmlReport(run(), List.of(
                f("SQL <b>injection</b>", Severity.CRITICAL, FindingStatus.OPEN, true, "sql-injection"),
                f("Ruído", Severity.LOW, FindingStatus.SUPPRESSED, false, "x")));

        assertTrue(html.contains("Findings (1)"));
        assertTrue(html.contains("Findings suprimidos (1)"));
        assertTrue(html.contains("NOVO"));
        assertTrue(html.contains("@media print"));
        assertTrue(html.contains("1 novos"));
    }

    @Test
    void escapaConteudoDeTerceiros() {
        String html = new ReportService().generateHtmlReport(run(), List.of(
                f("<img src=x onerror=alert(1)>", Severity.HIGH, FindingStatus.OPEN, null, "r")));

        assertFalse(html.contains("<img src=x"));
        assertFalse(html.contains("Portal <script>"));
        assertTrue(html.contains("Portal &lt;script&gt;"));
    }

    @Test
    void relatorioSemFindingsNaoQuebra() {
        String html = new ReportService().generateHtmlReport(run(), List.of());
        assertTrue(html.contains("Nenhum finding ativo"));
    }

    @Test
    void relatorioTrazAuditoriaCompletaComPrioridadeELimites() {
        String html = new ReportService().generateHtmlReport(run(), List.of(
                f("SQL <b>injection</b>", Severity.CRITICAL, FindingStatus.OPEN, true, "sentinel-sql-injection"),
                f("Ruído", Severity.LOW, FindingStatus.SUPPRESSED, false, "x")));

        assertTrue(html.contains("Auditoria completa"));
        assertTrue(html.contains("REPROVADO"));
        assertTrue(html.contains("P0 Bloqueador"));
        assertTrue(html.contains("Plano de ação"));
        assertTrue(html.contains("Fase 1"));
        assertTrue(html.contains("Não foi possível verificar"));
        assertTrue(html.contains("Core Web Vitals"));
        assertFalse(html.contains("SQL <b>"), "título de terceiros escapado também na tabela de dívida");
        assertFalse(html.contains("Ruído</td>"), "suprimido não entra na dívida técnica");
    }
}
