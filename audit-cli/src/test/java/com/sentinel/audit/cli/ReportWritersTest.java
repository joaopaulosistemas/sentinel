package com.sentinel.audit.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReportWritersTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static final String FINDINGS = """
            [
              {"ruleId":"sentinel-sql-injection","severity":"HIGH","category":"SECURITY","title":"SQL <Injection>",
               "description":"Use PreparedStatement & params","filePath":".\\\\src\\\\Dao.java","lineNumber":42,
               "fingerprint":"abc123","toolSource":"Semgrep","isNew":true,
               "remediation":{"references":["https://cwe.mitre.org/data/definitions/89.html"]}},
              {"ruleId":"sentinel-sql-injection","severity":"HIGH","category":"SECURITY","title":"SQL <Injection>",
               "filePath":"src/Other.java","lineNumber":0},
              {"ruleId":null,"severity":"LOW","category":"QUALITY","title":"Sem regra","filePath":""}
            ]""";

    @Test
    void sarifTemEstruturaValidaParaCodeScanning() throws Exception {
        JsonNode sarif = M.readTree(SarifWriter.write(M.readTree(FINDINGS), "1.0.0"));

        assertEquals("2.1.0", sarif.path("version").asText());
        JsonNode run = sarif.path("runs").get(0);
        assertEquals("Sentinel Audit Tool", run.path("tool").path("driver").path("name").asText());

        // 3 resultados, mas só 2 regras distintas (a repetida não duplica)
        assertEquals(3, run.path("results").size());
        assertEquals(2, run.path("tool").path("driver").path("rules").size());

        JsonNode first = run.path("results").get(0);
        assertEquals("error", first.path("level").asText());
        assertEquals("src/Dao.java", first.path("locations").get(0).path("physicalLocation").path("artifactLocation").path("uri").asText());
        assertEquals(42, first.path("locations").get(0).path("physicalLocation").path("region").path("startLine").asInt());
        assertEquals("abc123", first.path("partialFingerprints").path("sentinelFingerprint/v1").asText());
        assertTrue(first.path("properties").path("new").asBoolean());

        JsonNode rule = run.path("tool").path("driver").path("rules").get(0);
        assertEquals("8.0", rule.path("properties").path("security-severity").asText());
        assertEquals("https://cwe.mitre.org/data/definitions/89.html", rule.path("helpUri").asText());

        // sem linha => sem region; sem arquivo => sem locations; sem ruleId => id sintético
        assertTrue(run.path("results").get(1).path("locations").get(0).path("physicalLocation").path("region").isMissingNode());
        assertTrue(run.path("results").get(2).path("locations").isMissingNode());
        assertEquals("sentinel-audit-quality", run.path("results").get(2).path("ruleId").asText());
        assertEquals("note", run.path("results").get(2).path("level").asText());
    }

    @Test
    void sarifNormalizaCaminhos() {
        assertEquals("a/b.java", SarifWriter.normalizePath("./a\\b.java"));
        assertEquals("a/b.java", SarifWriter.normalizePath("/a/b.java"));
        assertEquals("warning", SarifWriter.level("MEDIUM"));
        assertEquals("note", SarifWriter.level("INFO"));
    }

    @Test
    void junitGeraXmlBemFormadoComEscape() throws Exception {
        String xml = JUnitWriter.write(M.readTree(FINDINGS), "Auditoria \"1\"");

        // precisa ser XML parseável mesmo com <, & e aspas nos textos
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(3, doc.getElementsByTagName("testcase").getLength());
        assertEquals(3, doc.getElementsByTagName("failure").getLength());
        assertEquals("3", doc.getDocumentElement().getAttribute("failures"));
        assertTrue(xml.contains("SQL &lt;Injection&gt;"));
        assertTrue(xml.contains("&amp; params"));
    }

    @Test
    void junitRemoveCaracteresDeControleInvalidos() throws Exception {
        String xml = JUnitWriter.write(M.readTree("[{\"title\":\"a\\u0000b\\u0007c\",\"severity\":\"LOW\"}]"), "s");
        assertFalse(xml.contains("\u0000"));
        assertFalse(xml.contains("\u0007"));
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));   // não lança
    }

    // ---------------------------------------------------------------- Markdown do PR

    private static com.fasterxml.jackson.databind.JsonNode json(String s) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
    }

    @Test
    void markdownDoPrMostraSoOQueContaEEscapaTextoDoRepositorio() throws Exception {
        String md = MarkdownWriter.write(json("""
                [{"severity":"HIGH","ruleId":"r1","filePath":"src/A.java","lineNumber":7,"title":"Injeção | <img src=x> @time [x](http://e)","status":"OPEN"},
                 {"severity":"CRITICAL","ruleId":"r2","filePath":"B.java","lineNumber":0,"title":"Segredo","status":"OPEN"},
                 {"severity":"LOW","ruleId":"r3","filePath":"C.java","title":"Estilo","status":"OPEN","shadow":true},
                 {"severity":"HIGH","ruleId":"r4","filePath":"D.java","title":"Falso positivo","status":"SUPPRESSED"}]
                """), "Sentinel Audit #9", "main");

        assertTrue(md.contains("alterações em relação a `main`"), md);
        assertTrue(md.contains("**2 problema(s)** — 1 crítico(s), 1 alto(s)"), md);
        assertTrue(md.indexOf("Segredo") < md.indexOf("Injeção"), "mais grave primeiro");
        assertTrue(md.contains("`src/A.java:7`"));
        assertFalse(md.contains("Estilo") || md.contains("Falso positivo"), "sombra e suprimidos fora da tabela");
        assertTrue(md.contains("1 achado(s) em modo sombra"));
        assertFalse(md.contains("<img") || md.contains("[x]("), "sem HTML nem link ativo vindo do repositório: " + md);
        assertFalse(md.contains("@time"), "sem menção a usuário");
        assertEquals(1, md.lines().filter(l -> l.contains("Injeção")).count());
    }

    @Test
    void markdownSemProblemasEmUmaLinha() throws Exception {
        String md = MarkdownWriter.write(json("[]"), "Sentinel Audit #1", "main");
        assertTrue(md.contains("Nenhum problema encontrado nos arquivos alterados"), md);
    }

    @Test
    void shadowESuprimidosNaoDerrubamOPipeline() throws Exception {
        var f = json("""
                [{"severity":"CRITICAL","shadow":true,"status":"OPEN"},
                 {"severity":"CRITICAL","status":"SUPPRESSED"},
                 {"severity":"HIGH","status":"OPEN"}]
                """);
        assertEquals(1, AuditCli.countAtLeast(f, "HIGH"));
        assertEquals(0, AuditCli.countAtLeast(f, "CRITICAL"));
    }

    @Test
    void baseRefEPrCommentSaoLidosNaLinhaDeComando() {
        AuditCli.CliArgs a = AuditCli.parse(new String[]{"--system-id", "1", "--base-ref", "main", "--pr-comment", "pr.md"});
        assertEquals("main", a.baseRef);
        assertEquals("pr.md", a.prCommentFile);
    }
}
