package com.sentinel.audit.service;

import com.sentinel.audit.dto.FindingsOverviewDto;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FindingsOverviewTest {

    private static Finding f(Severity sev, String file, FindingStatus st, Boolean isNew) {
        Finding x = new Finding();
        x.setSeverity(sev);
        x.setCategory(Category.SECURITY);
        x.setRuleId("r");
        x.setTitle("t");
        x.setFilePath(file);
        x.setLineNumber(1);
        x.setStatus(st);
        x.setIsNew(isNew);
        return x;
    }

    private static final FindingsOverview.Filter NONE =
            new FindingsOverview.Filter(null, java.util.Set.of(), null, null, false, false);

    private final List<FindingsOverview.Source> sources = List.of(
            new FindingsOverview.Source(1L, "api", 10L, List.of(
                    f(Severity.HIGH, "A.java", FindingStatus.OPEN, true),
                    f(Severity.CRITICAL, "B.java", FindingStatus.OPEN, false),
                    f(Severity.LOW, "C.java", FindingStatus.SUPPRESSED, false))),
            new FindingsOverview.Source(2L, "portal", 20L, List.of(
                    f(Severity.CRITICAL, "a.ts", FindingStatus.OPEN, true))));

    @Test
    void juntaOsSistemasComOsMaisGravesPrimeiro() {
        FindingsOverviewDto r = FindingsOverview.build(sources, NONE, 20, 0);
        assertEquals(3, r.total()); // o suprimido não conta
        assertEquals(List.of("api", "portal", "api"), r.items().stream().map(FindingsOverviewDto.Item::systemName).toList());
        assertEquals(Severity.CRITICAL, r.items().get(0).finding().getSeverity());
        assertEquals(Severity.HIGH, r.items().get(2).finding().getSeverity());
    }

    @Test
    void variasSeveridadesEResumoComCriticosEAltos() {
        FindingsOverviewDto r = FindingsOverview.build(sources,
                new FindingsOverview.Filter(null, java.util.Set.of(Severity.CRITICAL, Severity.HIGH), null, null, false, false), 20, 0);
        assertEquals(3, r.total());
        assertEquals(1, r.bySystem().get(0).critical());
        assertEquals(1, r.bySystem().get(0).high());
        assertEquals(1, r.bySystem().get(1).critical());
        assertEquals(0, r.bySystem().get(1).high());

        FindingsOverviewDto soAltos = FindingsOverview.build(sources,
                new FindingsOverview.Filter(null, java.util.Set.of(Severity.HIGH), null, null, false, false), 20, 0);
        assertEquals(1, soAltos.total());
    }

    @Test
    void resumoTrazStackEDataDaAuditoria() {
        java.time.LocalDateTime at = java.time.LocalDateTime.of(2026, 10, 22, 9, 0);
        List<FindingsOverview.Source> one = List.of(new FindingsOverview.Source(1L, "api", 10L,
                List.of(f(Severity.CRITICAL, "B.java", FindingStatus.OPEN, false)), "JAVA", at));
        FindingsOverviewDto r = FindingsOverview.build(one, NONE, 20, 0);
        assertEquals("JAVA", r.bySystem().get(0).stack());
        assertEquals(at, r.bySystem().get(0).auditedAt());
    }

    @Test
    void filtroDeSistemaMantemOResumoDosOutros() {
        FindingsOverviewDto r = FindingsOverview.build(sources,
                new FindingsOverview.Filter(2L, java.util.Set.of(), null, null, false, false), 20, 0);
        assertEquals(1, r.total());
        assertEquals("portal", r.items().get(0).systemName());
        assertEquals(2, r.bySystem().get(0).count()); // api continua com seus 2 ativos no resumo
        assertEquals(1, r.bySystem().get(1).count());
    }

    @Test
    void somenteNovosEPaginacao() {
        FindingsOverviewDto novos = FindingsOverview.build(sources,
                new FindingsOverview.Filter(null, java.util.Set.of(), null, null, true, false), 20, 0);
        assertEquals(2, novos.total());

        FindingsOverviewDto pagina = FindingsOverview.build(sources, NONE, 2, 2);
        assertEquals(3, pagina.total());
        assertEquals(1, pagina.items().size());
        // limite absurdo é reduzido ao máximo; com poucos achados devolve todos
        assertEquals(3, FindingsOverview.build(sources, NONE, 9999, 0).items().size());
    }

    @Test
    void suprimidosAparecemSoSePedidos() {
        FindingsOverviewDto r = FindingsOverview.build(sources,
                new FindingsOverview.Filter(null, java.util.Set.of(), null, null, false, true), 20, 0);
        assertEquals(4, r.total());
    }
}
