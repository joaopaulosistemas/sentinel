package com.sentinel.audit.insights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.pack.RemediationEntry;
import com.sentinel.audit.service.RemediationCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InsightsBuilderTest {

    @BeforeEach
    void catalogo() {
        RemediationCatalog.install(List.of(
                new RemediationEntry("fe-img-sem-alt", "Descreva a imagem", null, null, List.of(), "S", "LOW"),
                new RemediationEntry("proj-no-tests", "Escreva testes", null, null, List.of(), "L", "LOW")));
    }

    @AfterEach
    void limpa() {
        RemediationCatalog.install(List.of());
    }

    private static Finding f(String rule, Severity sev, Category cat, String file, int line) {
        Finding f = new Finding();
        f.setRuleId(rule);
        f.setSeverity(sev);
        f.setCategory(cat);
        f.setTitle("Título " + rule);
        f.setFilePath(file);
        f.setLineNumber(line);
        return f;
    }

    private static List<AnalyzerRunInfo> allOk() {
        List<AnalyzerRunInfo> l = new ArrayList<>();
        for (String n : List.of("Semgrep", "Secrets", "Gitleaks", "Trivy", "OWASP-Dependency", "Architecture",
                "Arquitetura (pacotes)", "CleanCode", "PMD", "ESLint", "Higiene do projeto")) {
            l.add(new AnalyzerRunInfo(n, "OK", 0, 1, null));
        }
        return l;
    }

    @Test
    void agrupaPorRegraEPriorizaPelaGravidade() {
        List<Finding> fs = List.of(
                f("sentinel-fe-img-sem-alt", Severity.MEDIUM, Category.QUALITY, "a.tsx", 3),
                f("sentinel-fe-img-sem-alt", Severity.MEDIUM, Category.QUALITY, "b.tsx", 9),
                f("sentinel-sql-injection", Severity.CRITICAL, Category.SECURITY, "Dao.java", 10),
                f("sentinel-ts-var", Severity.LOW, Category.QUALITY, "c.ts", 1));
        AuditInsights in = InsightsBuilder.build("Pagamentos", fs, new AuditContextData(null, allOk()));

        assertEquals(3, in.debtTable().size(), "duas ocorrências da mesma regra viram uma linha");
        DebtItem first = in.debtTable().get(0);
        assertEquals("sentinel-sql-injection", first.id());
        assertEquals("P0", first.priority());
        assertEquals("Bloqueador", first.priorityLabel());
        DebtItem img = in.debtTable().stream().filter(d -> d.id().contains("img")).findFirst().orElseThrow();
        assertEquals(2, img.occurrences());
        assertEquals("P2", img.priority());
        assertEquals("S", img.effort());
        assertFalse(img.effortEstimated());
        assertEquals("LOW", img.regressionRisk());
        assertEquals("a.tsx:3", img.example());
        assertEquals("Descreva a imagem", img.recommendation());
        DebtItem sql = first;
        assertTrue(sql.effortEstimated(), "sem orientação cadastrada o esforço é estimado e marcado como tal");
        assertEquals("M", sql.effort());
        assertTrue(in.verdict().startsWith("REPROVADO"));
    }

    @Test
    void quickWinsSaoEsforcoPequenoEPrioridadeAteP2() {
        List<Finding> fs = List.of(
                f("sentinel-fe-img-sem-alt", Severity.MEDIUM, Category.QUALITY, "a.tsx", 3),
                f("sentinel-proj-no-tests", Severity.HIGH, Category.QUALITY, ".", 1),
                f("sentinel-ts-var", Severity.LOW, Category.QUALITY, "c.ts", 1));
        AuditInsights in = InsightsBuilder.build("X", fs, new AuditContextData(null, allOk()));
        List<String> qw = in.quickWins().stream().map(DebtItem::id).collect(Collectors.toList());
        assertEquals(List.of("sentinel-fe-img-sem-alt"), qw);
    }

    @Test
    void roadmapTemOitoFasesECadaItemFicaEmUmaSo() {
        List<Finding> fs = List.of(
                f("sentinel-sql-injection", Severity.CRITICAL, Category.SECURITY, "D.java", 1),
                f("sentinel-api-ssrf", Severity.HIGH, Category.SECURITY, "E.java", 1),
                f("sentinel-perf-consulta-em-laco", Severity.MEDIUM, Category.QUALITY, "S.java", 1),
                f("sentinel-proj-no-tests", Severity.HIGH, Category.QUALITY, ".", 1),
                f("sentinel-fe-img-sem-alt", Severity.MEDIUM, Category.QUALITY, "a.tsx", 3),
                f("sentinel-proj-no-ci", Severity.MEDIUM, Category.QUALITY, ".", 1),
                f("sentinel-cc-complexidade", Severity.MEDIUM, Category.QUALITY, "X.java", 1),
                f("sentinel-proj-no-readme", Severity.LOW, Category.QUALITY, ".", 1));
        AuditInsights in = InsightsBuilder.build("X", fs, new AuditContextData(null, allOk()));
        assertEquals(8, in.roadmap().size());
        int total = in.roadmap().stream().mapToInt(RoadmapPhase::total).sum();
        assertEquals(in.debtTable().size(), total);
        assertEquals(1, in.roadmap().get(0).total(), "fase 1: só P0");
        assertEquals("sentinel-sql-injection", in.roadmap().get(0).items().get(0).id());
        assertEquals(1, in.roadmap().get(1).total(), "estrutural: complexidade");
        assertEquals(1, in.roadmap().get(2).total(), "performance");
        assertEquals(1, in.roadmap().get(3).total(), "segurança: ssrf");
        assertEquals(1, in.roadmap().get(4).total(), "testes");
        assertEquals(1, in.roadmap().get(5).total(), "frontend");
        assertEquals(1, in.roadmap().get(6).total(), "devops: sem CI");
        assertEquals(1, in.roadmap().get(7).total(), "P3 e documentação");
    }

    @Test
    void areaSemAnalisadorNaoViraBomEListaNaoVerificado() {
        List<AnalyzerRunInfo> runs = new ArrayList<>(allOk());
        runs.removeIf(r -> r.name().equals("Trivy") || r.name().equals("OWASP-Dependency"));
        runs.add(new AnalyzerRunInfo("Trivy", "INDISPONIVEL", 0, 0, "binário ausente"));
        AuditInsights in = InsightsBuilder.build("X", List.of(), new AuditContextData(null, runs));

        AreaReport dep = in.areas().stream().filter(a -> a.area().equals("DEPENDENCIES")).findFirst().orElseThrow();
        assertEquals("NAO_VERIFICADO", dep.state());
        AreaReport sec = in.areas().stream().filter(a -> a.area().equals("SECURITY")).findFirst().orElseThrow();
        assertEquals("SEM_ACHADOS", sec.state(), "outros analisadores de segurança rodaram");
        assertTrue(in.unverified().stream().anyMatch(s -> s.contains("Trivy") && s.contains("indisponivel")), in.unverified().toString());
        assertTrue(in.unverified().stream().anyMatch(s -> s.contains("Core Web Vitals")));
        assertTrue(in.unverified().stream().anyMatch(s -> s.contains("Cobertura real")));
        assertTrue(in.verdict().startsWith("APROVADO"));
    }

    @Test
    void auditoriaAntigaSemContextoNaoDeclaraAreaNaoVerificada() {
        AuditInsights in = InsightsBuilder.build("X", List.of(f("sentinel-ts-var", Severity.LOW, Category.QUALITY, "c.ts", 1)), null);
        assertTrue(in.areas().stream().noneMatch(a -> a.state().equals("NAO_VERIFICADO")));
        assertEquals(1, in.activeFindings());
        assertTrue(in.executiveSummary().contains("1 achado(s)"));
    }

    @Test
    void estadoDaAreaSegueAsSeveridades() {
        List<Finding> fs = List.of(
                f("sentinel-api-ssrf", Severity.HIGH, Category.SECURITY, "A.java", 1),
                f("sentinel-api-cors-wildcard", Severity.MEDIUM, Category.SECURITY, "B.java", 1),
                f("sentinel-ts-var", Severity.LOW, Category.QUALITY, "c.ts", 1));
        AuditInsights in = InsightsBuilder.build("X", fs, new AuditContextData(null, allOk()));
        AreaReport sec = in.areas().stream().filter(a -> a.area().equals("SECURITY")).findFirst().orElseThrow();
        assertEquals("ATENCAO", sec.state());
        assertEquals(2, sec.total());
        assertEquals(1, sec.risks().size(), "só P0/P1 entram em riscos");
        AreaReport q = in.areas().stream().filter(a -> a.area().equals("CODE_QUALITY")).findFirst().orElseThrow();
        assertEquals("ATENCAO", q.state());
        assertTrue(q.currentState().contains("baixa gravidade"));
    }

    @Test
    void top10Limitado() {
        List<Finding> fs = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            fs.add(f("sentinel-regra-" + i, Severity.MEDIUM, Category.QUALITY, "f" + i + ".java", i + 1));
        }
        AuditInsights in = InsightsBuilder.build("X", fs, new AuditContextData(null, allOk()));
        assertEquals(15, in.debtTable().size());
        assertEquals(10, in.topProblems().size());
        assertEquals(10, in.topImprovements().size());
    }
}
