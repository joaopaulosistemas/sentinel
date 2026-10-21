package com.sentinel.audit.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes da interpretação da saída JSON do Semgrep (sem precisar do Semgrep instalado).
 */
class SemgrepAnalyzerTest {

    private final SemgrepAnalyzer analyzer = new SemgrepAnalyzer();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String JSON = """
            {"results":[
              {"check_id":"java.lang.security.audit.sqli","path":"/work/proj/src/Dao.java",
               "start":{"line":42},
               "extra":{"message":"SQL injection","severity":"ERROR","lines":"requires login",
                        "metadata":{"cwe":"CWE-89: Improper Neutralization of Special Elements used in an SQL Command ('SQL Injection')",
                                    "category":"security"}}},
              {"check_id":"sentinel-god-object","path":"src/Big.java","start":{"line":1},
               "extra":{"message":"God object","severity":"WARNING","lines":"class Big {",
                        "metadata":{"cwe":["CWE-1060: Excessive Number of Inefficient Server-Side Data Accesses"],"category":"quality"}}}
            ]}
            """;

    @Test
    void deveInterpretarResultadosEExtrairCweCurto() {
        List<Finding> findings = analyzer.parseSemgrepOutput(JSON, Path.of("/work/proj"), new AuditRun());
        assertEquals(2, findings.size());

        Finding sqli = findings.get(0);
        assertEquals("CWE-89", sqli.getCveId());               // cabe na coluna de 50 caracteres
        assertEquals(Severity.HIGH, sqli.getSeverity());
        assertEquals(Category.SECURITY, sqli.getCategory());
        assertEquals("src/Dao.java", sqli.getFilePath());      // caminho relativo ao projeto
        assertNull(sqli.getCodeSnippet());                     // "requires login" não é código

        Finding god = findings.get(1);
        assertEquals("CWE-1060", god.getCveId());              // cwe em formato de lista
        assertEquals(Severity.MEDIUM, god.getSeverity());
        assertEquals(Category.QUALITY, god.getCategory());
        assertEquals("class Big {", god.getCodeSnippet());
    }

    @Test
    void saidaInvalidaNaoLancaExcecao() {
        assertTrue(analyzer.parseSemgrepOutput("not json at all", Path.of("/x"), new AuditRun()).isEmpty());
        assertTrue(analyzer.parseSemgrepOutput("", Path.of("/x"), new AuditRun()).isEmpty());
    }

    @Test
    void extractCweTrataFormatosVariados() throws Exception {
        assertEquals("CWE-79", SemgrepAnalyzer.extractCwe(mapper.readTree("\"cwe-79: XSS\"")));
        assertEquals("CWE-22", SemgrepAnalyzer.extractCwe(mapper.readTree("[\"CWE-22: Path\",\"CWE-23\"]")));
        assertNull(SemgrepAnalyzer.extractCwe(mapper.readTree("[]")));
        assertNull(SemgrepAnalyzer.extractCwe(mapper.readTree("{}")));
    }

    @Test
    void categoriaDeArquiteturaEObservabilidadeVemDosMetadadosDaRegra() {
        String json = """
                {"results":[
                  {"check_id":"sentinel-java-service-locator","path":"A.java","start":{"line":3},
                   "extra":{"message":"m","severity":"INFO","lines":"x","metadata":{"category":"architecture"}}},
                  {"check_id":"regra-x","path":"B.java","start":{"line":4},
                   "extra":{"message":"m","severity":"INFO","lines":"x","metadata":{"category":"observability"}}},
                  {"check_id":"sentinel-java-print-stack-trace","path":"C.java","start":{"line":5},
                   "extra":{"message":"m","severity":"INFO","lines":"x","metadata":{"category":"quality"}}}
                ]}
                """;
        List<Finding> f = analyzer.parseSemgrepOutput(json, Path.of("/p"), new AuditRun());
        assertEquals(Category.ARCHITECTURE, f.get(0).getCategory());
        assertEquals(Category.OBSERVABILITY, f.get(1).getCategory());
        assertEquals(Category.QUALITY, f.get(2).getCategory());
        assertEquals(Severity.LOW, f.get(0).getSeverity());     // INFO do Semgrep = LOW
    }

    @Test
    void ignoraBibliotecasDeTerceirosEArquivosGerados() {
        assertTrue(SemgrepAnalyzer.VENDORED_EXCLUDES.containsAll(
                List.of("node_modules", "vendor", "libs", "*.min.js")));
    }
}
