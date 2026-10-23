package com.sentinel.audit.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.FindingResponse;
import com.sentinel.audit.dto.FixPlanDto;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpShaperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private FindingResponse finding(long id, Severity sev, String rule, String title) {
        FindingResponse f = new FindingResponse();
        f.setId(id);
        f.setSeverity(sev);
        f.setCategory(Category.SECURITY);
        f.setRuleId(rule);
        f.setTitle(title);
        f.setDescription("DESCRICAO-SECRETA");
        f.setCodeSnippet("String senha = \"SNIPPET-SECRETO\";");
        f.setAiRecommendation("RECOMENDACAO-SECRETA");
        f.setFilePath("src/main/java/Conta.java");
        f.setLineNumber(42);
        f.setToolSource("semgrep");
        f.setStatus(FindingStatus.OPEN);
        f.setIsNew(true);
        return f;
    }

    private String json(Object o) throws Exception {
        return mapper.writeValueAsString(o);
    }

    @Test
    void titulosNaoTrazemArquivoNemLinha() throws Exception {
        Map<String, Object> m = McpShaper.finding(finding(1, Severity.CRITICAL, "sql-injection", "SQL por concatenação"), DataLevel.TITLES);
        assertEquals("SQL por concatenação", m.get("titulo"));
        assertFalse(m.containsKey("arquivo"));
        assertFalse(m.containsKey("linha"));
    }

    @Test
    void arquivoELinhaSoNoNivelQuePermite() {
        Map<String, Object> m = McpShaper.finding(finding(1, Severity.CRITICAL, "sql-injection", "x"), DataLevel.FILE_LINE);
        assertEquals("src/main/java/Conta.java", m.get("arquivo"));
        assertEquals(42, m.get("linha"));
    }

    @Test
    void nenhumNivelDeixaSairTrechoDescricaoOuRecomendacao() throws Exception {
        List<FindingResponse> list = List.of(finding(1, Severity.CRITICAL, "sql-injection", "SQL por concatenação"));
        for (DataLevel level : DataLevel.values()) {
            String out = json(McpShaper.findingsResult(list, level, 20));
            assertFalse(out.contains("SNIPPET-SECRETO"), level.name());
            assertFalse(out.contains("DESCRICAO-SECRETA"), level.name());
            assertFalse(out.contains("RECOMENDACAO-SECRETA"), level.name());
        }
    }

    @Test
    void sensivelSoTemContagensPorRegra() throws Exception {
        List<FindingResponse> list = List.of(
                finding(1, Severity.HIGH, "sql-injection", "SQL por concatenação"),
                finding(2, Severity.CRITICAL, "sql-injection", "SQL por concatenação"),
                finding(3, Severity.LOW, "log-sem-nivel", "Log sem nível"));
        Map<String, Object> r = McpShaper.findingsResult(list, DataLevel.SENSITIVE, 20);
        String out = json(r);
        assertFalse(out.contains("SQL por concatenação"));
        assertFalse(out.contains("Conta.java"));
        assertFalse(r.containsKey("achados"));
        List<?> porRegra = (List<?>) r.get("porRegra");
        assertEquals(2, porRegra.size());
        Map<?, ?> primeira = (Map<?, ?>) porRegra.get(0);
        assertEquals("sql-injection", primeira.get("regra"));
        assertEquals(Severity.CRITICAL, primeira.get("severidade"));
        assertEquals(2, primeira.get("ocorrencias"));
    }

    @Test
    void achadosVemDoMaisGraveParaOMenosGraveECortadosNoLimite() {
        List<FindingResponse> list = List.of(
                finding(1, Severity.LOW, "b", "baixo"),
                finding(2, Severity.CRITICAL, "a", "crítico"),
                finding(3, Severity.HIGH, "c", "alto"));
        List<Map<String, Object>> shown = McpShaper.findings(list, DataLevel.TITLES, 2);
        assertEquals(2, shown.size());
        assertEquals("crítico", shown.get(0).get("titulo"));
        assertEquals("alto", shown.get(1).get("titulo"));
        assertEquals(McpShaper.MAX_LIST, McpShaper.findings(java.util.Collections.nCopies(80, list.get(0)), DataLevel.TITLES, 500).size());
    }

    @Test
    void planDeCorrecaoSensivelNaoTemTituloNemLocal() throws Exception {
        FixPlanDto plan = new FixPlanDto(62.0, 71.0, 3, List.of(
                new FixPlanDto.Item("sql-injection", "SQL por concatenação", Severity.CRITICAL, Category.SECURITY, 4, 4.0,
                        "Conta.java", 42, "Use parâmetros")));
        String sensivel = json(McpShaper.fixPlan(plan, DataLevel.SENSITIVE));
        assertFalse(sensivel.contains("SQL por concatenação"));
        assertFalse(sensivel.contains("Conta.java"));
        assertFalse(sensivel.contains("Use parâmetros"));
        assertTrue(sensivel.contains("ganhoDeNota"));

        String titulos = json(McpShaper.fixPlan(plan, DataLevel.TITLES));
        assertTrue(titulos.contains("SQL por concatenação"));
        assertFalse(titulos.contains("Conta.java"));

        assertTrue(json(McpShaper.fixPlan(plan, DataLevel.FILE_LINE)).contains("Conta.java"));
    }

    @Test
    void primeiraAuditoriaNaoTemComparacao() {
        AuditDiffResponse d = new AuditDiffResponse(10L, null, false, 62.0, null, null, List.of(), List.of(), 0);
        Map<String, Object> m = McpShaper.diff(d, DataLevel.TITLES);
        assertEquals(false, m.get("temBase"));
        assertNotNull(m.get("mensagem"));
    }

    @Test
    void comparacaoMostraNovosEResolvidosConformeONivel() throws Exception {
        AuditDiffResponse d = new AuditDiffResponse(11L, 10L, true, 71.0, 62.0, 9.0,
                List.of(finding(1, Severity.HIGH, "r1", "Novo problema")),
                List.of(finding(2, Severity.CRITICAL, "r2", "Problema resolvido")), 5);
        Map<String, Object> titulos = McpShaper.diff(d, DataLevel.TITLES);
        assertEquals(9.0, titulos.get("variacao"));
        assertEquals(1, titulos.get("novos"));
        assertTrue(json(titulos).contains("Problema resolvido"));
        String sensivel = json(McpShaper.diff(d, DataLevel.SENSITIVE));
        assertFalse(sensivel.contains("Novo problema"));
        assertFalse(sensivel.contains("Problema resolvido"));
        assertTrue(sensivel.contains("novosPorRegra"));
    }

    @Test
    void nivelDeDadosInvalidoCaiNoPadrao() {
        assertEquals(DataLevel.TITLES, DataLevel.parse(null, DataLevel.TITLES));
        assertEquals(DataLevel.TITLES, DataLevel.parse("qualquer coisa", DataLevel.TITLES));
        assertEquals(DataLevel.FILE_LINE, DataLevel.parse("file_line", DataLevel.TITLES));
    }
}
