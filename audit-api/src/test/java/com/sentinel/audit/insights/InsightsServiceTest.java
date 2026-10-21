package com.sentinel.audit.insights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InsightsServiceTest {

    @Test
    void contextoSobreviveAoIdaEVolta() throws Exception {
        ProjectMap map = new ProjectMap(Map.of("Java", 3), List.of("Maven"), List.of("Quarkus"), List.of("Main.java"),
                List.of(".github/workflows/ci.yml"), List.of(), List.of(), List.of("README.md"), List.of(), 3, 1, true, 12,
                true, false, List.of(), List.of(".env"), List.of(), List.of(), List.of(), List.of(), false);
        AuditContextData ctx = new AuditContextData(map, List.of(new AnalyzerRunInfo("Semgrep", "OK", 4, 120, null)));
        String json = new ObjectMapper().writeValueAsString(ctx);

        AuditContextData back = InsightsService.parseContext(json);
        assertNotNull(back);
        assertEquals(List.of("Quarkus"), back.projectMap().frameworks());
        assertEquals(List.of(".env"), back.projectMap().committedEnvFiles());
        assertEquals(12, back.projectMap().readmeLines());
        assertTrue(back.analyzerRuns().get(0).ok());
    }

    @Test
    void contextoAusenteOuInvalidoViraNulo() {
        assertNull(InsightsService.parseContext(null));
        assertNull(InsightsService.parseContext("  "));
        assertNull(InsightsService.parseContext("{não é json"));
    }

    @Test
    void camposDesconhecidosSaoIgnorados() {
        AuditContextData c = InsightsService.parseContext("{\"projectMap\":null,\"analyzerRuns\":[],\"futuro\":1}");
        assertNotNull(c);
        assertTrue(c.analyzerRuns().isEmpty());
    }
}
