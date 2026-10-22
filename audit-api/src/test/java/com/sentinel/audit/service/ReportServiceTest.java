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

    private static com.sentinel.audit.dto.FixPlanDto plan() {
        return new com.sentinel.audit.dto.FixPlanDto(60, 71, 2, List.of(
                new com.sentinel.audit.dto.FixPlanDto.Item("sql", "SQL <b>injection</b>", Severity.CRITICAL, Category.SECURITY, 2, 8.5,
                        "A.java", 10, "usar parâmetros")));
    }

    @Test
    void relatorioExecutivoMostraNotaMetaEPrioridades() {
        AuditRun r = run();
        RiskScore score = new RiskScore();
        score.setOverall(60.0);
        r.setRiskScore(score);
        String html = new ReportService().generateExecutiveReport(r, List.of(
                f("SQL", Severity.CRITICAL, FindingStatus.OPEN, false, "sql")), plan(), 2.0, 75);

        assertTrue(html.contains("Relatório executivo"));
        assertTrue(html.contains("faltam <strong>15 pontos</strong>"));
        assertTrue(html.contains("+2 ponto(s)"));
        assertTrue(html.contains("para cerca de <strong>71</strong>"));
        assertTrue(html.contains("SQL &lt;b&gt;injection&lt;/b&gt;"), "conteúdo escapado");
        assertFalse(html.contains("Findings ("), "não lista achado por achado");
    }

    @Test
    void relatorioComparativoListaEvolucaoOuExplicaQuandoFaltaHistorico() {
        var sev = java.util.Map.of(Severity.CRITICAL, 3L);
        var a = new com.sentinel.audit.dto.TrendPoint(1L, "main", LocalDateTime.of(2026, 10, 1, 9, 0), 50.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, sev);
        var b = new com.sentinel.audit.dto.TrendPoint(2L, "main", LocalDateTime.of(2026, 10, 8, 9, 0), 58.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, java.util.Map.of());
        ReportService svc = new ReportService();

        String html = svc.generateComparativeReport(run(), List.of(a, b), 30);
        assertTrue(html.contains("últimos 30 dias"));
        assertTrue(html.contains("+8.5 ponto(s)"));
        assertTrue(html.contains("<td>#2</td>"));

        assertTrue(svc.generateComparativeReport(run(), List.of(a), null).contains("ao menos duas auditorias"));
    }

    @Test
    void csvProtegeContraInjecaoDeFormulaEAspas() {
        Finding danger = f("=HYPERLINK(\"x\")", Severity.HIGH, FindingStatus.OPEN, true, "r,1");
        String csv = new ReportService().generateCsv(List.of(danger, f("oculto", Severity.LOW, FindingStatus.SUPPRESSED, false, "x")));
        assertTrue(csv.startsWith("severidade,categoria,regra,titulo,arquivo,linha,ferramenta,novo\r\n"));
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));
        assertTrue(csv.contains("\"r,1\""));
        assertFalse(csv.contains("oculto"), "suprimidos ficam de fora");
        assertTrue(csv.contains(",sim\r\n"));
    }

    @Test
    void tipoDeRelatorioAceitaPortuguesIngles() {
        assertEquals(ReportService.Type.TECNICO, ReportService.Type.parse(null));
        assertEquals(ReportService.Type.EXECUTIVO, ReportService.Type.parse(" Executivo "));
        assertEquals(ReportService.Type.COMPARATIVO, ReportService.Type.parse("comparative"));
        assertThrows(IllegalArgumentException.class, () -> ReportService.Type.parse("xyz"));
    }
}
