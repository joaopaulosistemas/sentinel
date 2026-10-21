package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.service.Fingerprints;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CleanCodeAnalyzerTest {

    private final CleanCodeAnalyzer analyzer = new CleanCodeAnalyzer();

    private List<Finding> java(String src) {
        return analyzer.analyzeSource("src/A.java", src, false, false, new AuditRun());
    }

    private static Finding byRule(List<Finding> fs, String rule) {
        return fs.stream().filter(f -> f.getRuleId().equals(rule)).findFirst().orElse(null);
    }

    /** Método Java com n linhas de código no corpo. */
    private static String longMethod(String name, int bodyLines) {
        StringBuilder sb = new StringBuilder("class A {\n  void " + name + "() {\n");
        for (int i = 0; i < bodyLines; i++) {
            sb.append("    run(").append(i).append(");\n");
        }
        return sb.append("  }\n}\n").toString();
    }

    private static String ifs(int n) {
        StringBuilder sb = new StringBuilder("class A {\n  void m(int x) {\n");
        for (int i = 0; i < n; i++) {
            sb.append("    if (x == ").append(i).append(") { run(); }\n");
        }
        return sb.append("  }\n}\n").toString();
    }

    private static String params(int n) {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < n; i++) {
            p.append(i > 0 ? ", " : "").append("int p").append(i);
        }
        return "class A {\n  void m(" + p + ") { run(); }\n}\n";
    }

    private static String nested(int levels) {
        StringBuilder open = new StringBuilder();
        StringBuilder close = new StringBuilder();
        for (int i = 0; i < levels; i++) {
            open.append("if (a").append(i).append(") { ");
            close.append("} ");
        }
        return "class A {\n  void m() {\n    " + open + "run(); " + close + "\n  }\n}\n";
    }

    // ------------------------------------------------------------------ limites e severidades

    @Test
    void funcaoLongaSoPassandoDoLimiteESeveridadeSobe() {
        // assinatura + n corpo + chave final = n + 2 linhas
        assertNull(byRule(java(longMethod("ok", 48)), "sentinel-cc-funcao-longa"));          // 50 linhas: no limite
        Finding f = byRule(java(longMethod("longa", 49)), "sentinel-cc-funcao-longa");        // 51 linhas
        assertNotNull(f);
        assertEquals(Severity.LOW, f.getSeverity());
        assertEquals(Category.QUALITY, f.getCategory());
        assertTrue(f.getTitle().contains("longa") && f.getTitle().contains("51"));
        assertEquals(Severity.MEDIUM, byRule(java(longMethod("enorme", 120)), "sentinel-cc-funcao-longa").getSeverity());
    }

    @Test
    void componentesJsxTemLimiteMaior() {
        StringBuilder sb = new StringBuilder("export function Tela() {\n");
        for (int i = 0; i < 80; i++) {
            sb.append("  run(").append(i).append(");\n");
        }
        sb.append("}\n");
        assertNotNull(byRule(analyzer.analyzeSource("a.ts", sb.toString(), true, false, new AuditRun()), "sentinel-cc-funcao-longa"));
        assertNull(byRule(analyzer.analyzeSource("a.tsx", sb.toString(), true, true, new AuditRun()), "sentinel-cc-funcao-longa"));
    }

    @Test
    void parametrosDemais() {
        assertNull(byRule(java(params(5)), "sentinel-cc-muitos-parametros"));
        assertEquals(Severity.LOW, byRule(java(params(6)), "sentinel-cc-muitos-parametros").getSeverity());
        assertEquals(Severity.LOW, byRule(java(params(8)), "sentinel-cc-muitos-parametros").getSeverity());
        assertEquals(Severity.MEDIUM, byRule(java(params(9)), "sentinel-cc-muitos-parametros").getSeverity());
    }

    @Test
    void complexidadeAlta() {
        assertNull(byRule(java(ifs(9)), "sentinel-cc-complexidade"));                       // 1 + 9 = 10: no limite
        Finding f = byRule(java(ifs(10)), "sentinel-cc-complexidade");                      // 11
        assertEquals(Severity.MEDIUM, f.getSeverity());
        assertTrue(f.getTitle().contains("11"));
        assertEquals(Severity.MEDIUM, byRule(java(ifs(19)), "sentinel-cc-complexidade").getSeverity());   // 20
        assertEquals(Severity.HIGH, byRule(java(ifs(20)), "sentinel-cc-complexidade").getSeverity());     // 21
    }

    @Test
    void aninhamentoProfundo() {
        assertNull(byRule(java(nested(4)), "sentinel-cc-aninhamento"));
        assertEquals(Severity.LOW, byRule(java(nested(5)), "sentinel-cc-aninhamento").getSeverity());
        assertEquals(Severity.LOW, byRule(java(nested(6)), "sentinel-cc-aninhamento").getSeverity());
        assertEquals(Severity.MEDIUM, byRule(java(nested(7)), "sentinel-cc-aninhamento").getSeverity());
    }

    @Test
    void codigoLimpoNaoGeraNada() {
        assertTrue(java("class A { int soma(int a, int b) { return a + b; } }").isEmpty());
    }

    @Test
    void limitesSaoConfiguraveis() {
        analyzer.maxLines = 10;
        analyzer.maxParams = 2;
        analyzer.maxComplexity = 3;
        analyzer.maxNesting = 1;
        assertNotNull(byRule(java(longMethod("m", 12)), "sentinel-cc-funcao-longa"));
        assertNotNull(byRule(java(params(3)), "sentinel-cc-muitos-parametros"));
        assertNotNull(byRule(java(ifs(3)), "sentinel-cc-complexidade"));
        assertNotNull(byRule(java(nested(2)), "sentinel-cc-aninhamento"));
    }

    // ------------------------------------------------------------------ identidade entre auditorias

    @Test
    void fingerprintNaoMudaQuandoCorpoCrescePassaDosNumerosOuLinhaMuda() {
        Finding antes = byRule(java(ifs(10)), "sentinel-cc-complexidade");
        Finding depois = byRule(java("// cabeçalho novo\n// outro\n" + ifs(15)), "sentinel-cc-complexidade");
        assertNotEquals(antes.getLineNumber(), depois.getLineNumber());
        assertEquals(Fingerprints.compute(antes), Fingerprints.compute(depois));          // mesmo problema, piorou

        Finding outroMetodo = byRule(java(ifs(10).replace("void m(", "void outra(")), "sentinel-cc-complexidade");
        assertNotEquals(Fingerprints.compute(antes), Fingerprints.compute(outroMetodo));
    }

    @Test
    void snippetEhAAssinaturaOriginal() {
        Finding f = byRule(java(params(6)), "sentinel-cc-muitos-parametros");
        assertTrue(f.getCodeSnippet().startsWith("void m(int p0"));
        assertEquals(2, f.getLineNumber());
    }

    // ------------------------------------------------------------------ varredura de arquivos

    @Test
    void varreProjetoIgnorandoTestesDependenciasEGerados() throws Exception {
        Path root = Files.createTempDirectory("cc");
        write(root, "src/main/java/Audit.java", ifs(12));                      // "Audit" não é teste
        write(root, "src/main/java/AuditTest.java", ifs(12));                  // é
        write(root, "src/main/java/AuditIT.java", ifs(12));                    // é
        write(root, "src/test/java/Outro.java", ifs(12));                      // pasta de teste
        write(root, "web/node_modules/lib/index.js", "function f(x){" + "if(x){}".repeat(15) + "}");
        write(root, "web/src/app.ts", "export function g(x: number) {" + " if (x) { run(); }".repeat(15) + " }");
        write(root, "web/src/app.spec.ts", "export function h(x: number) {" + " if (x) { run(); }".repeat(15) + " }");
        write(root, "web/src/tipos.d.ts", "declare function d(x: number): void;");
        write(root, "target/classes/Gerado.java", ifs(12));
        write(root, "README.md", "if (x) {");

        List<Finding> fs = analyzer.analyze(root, new AuditRun()).findings();

        assertEquals(List.of("src/main/java/Audit.java", "web/src/app.ts"),
                fs.stream().map(Finding::getFilePath).sorted().toList());
        assertTrue(fs.stream().allMatch(f -> f.getToolSource().equals("CleanCode")));
    }

    @Test
    void arquivoIlegivelOuEnormeNaoQuebra() throws Exception {
        Path root = Files.createTempDirectory("cc2");
        Files.write(root.resolve("Binario.java"), new byte[] {(byte) 0xff, (byte) 0xfe, 0x00, (byte) 0x80, (byte) 0xc3, 0x28});
        write(root, "Bom.java", ifs(12));
        assertEquals(1, analyzer.analyze(root, new AuditRun()).findings().size());
    }

    @Test
    void limitaAsOcorrenciasMantendoAsPiores() throws Exception {
        Path root = Files.createTempDirectory("cc3");
        write(root, "A.java", ifs(12));       // complexidade 13  (MEDIUM)
        write(root, "B.java", ifs(30));       // complexidade 31  (HIGH)
        write(root, "C.java", ifs(14));       // complexidade 15  (MEDIUM)
        analyzer.maxFindings = 2;

        List<Finding> fs = analyzer.analyze(root, new AuditRun()).findings();

        assertEquals(2, fs.size());
        assertEquals("B.java", fs.get(0).getFilePath());                              // a pior primeiro
        assertEquals("C.java", fs.get(1).getFilePath());                              // depois a segunda pior
    }

    @Test
    void desligadoOuStackNaoSuportada() throws Exception {
        Path root = Files.createTempDirectory("cc4");
        write(root, "A.java", ifs(12));
        analyzer.enabled = false;
        assertTrue(analyzer.analyze(root, new AuditRun()).findings().isEmpty());

        assertTrue(analyzer.supports(Stack.JAVA));
        assertTrue(analyzer.supports(Stack.JAVASCRIPT));
        assertTrue(analyzer.supports(Stack.MIXED));
        assertFalse(analyzer.supports(Stack.PYTHON));
        assertFalse(analyzer.supports(Stack.COBOL));
        assertFalse(analyzer.supports(Stack.DOTNET));
    }

    @Test
    void reconheceArquivosDeProducao() {
        assertTrue(CleanCodeAnalyzer.isSourceFile("Audit.java"));
        assertTrue(CleanCodeAnalyzer.isSourceFile("Commit.java"));
        assertTrue(CleanCodeAnalyzer.isSourceFile("Contest.java"));
        assertTrue(CleanCodeAnalyzer.isSourceFile("app.component.ts"));
        assertTrue(CleanCodeAnalyzer.isSourceFile("Pagina.tsx"));
        assertFalse(CleanCodeAnalyzer.isSourceFile("AuditTest.java"));
        assertFalse(CleanCodeAnalyzer.isSourceFile("AuditTests.java"));
        assertFalse(CleanCodeAnalyzer.isSourceFile("AuditIT.java"));
        assertFalse(CleanCodeAnalyzer.isSourceFile("app.spec.ts"));
        assertFalse(CleanCodeAnalyzer.isSourceFile("app.test.tsx"));
        assertFalse(CleanCodeAnalyzer.isSourceFile("tipos.d.ts"));
        assertFalse(CleanCodeAnalyzer.isSourceFile("bundle.min.js"));
        assertFalse(CleanCodeAnalyzer.isSourceFile("README.md"));
    }

    private static void write(Path root, String rel, String content) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
