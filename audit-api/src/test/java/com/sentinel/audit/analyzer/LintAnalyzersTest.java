package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PMD e ESLint. As fixtures {@code lint/*-real.json} são a saída <b>real</b> das ferramentas (PMD 7.7.0 e ESLint 9)
 * rodando sobre um arquivo com problemas conhecidos; os testes de execução usam executáveis falsos.
 */
class LintAnalyzersTest {

    private static final Path PROJ = Path.of("/proj");

    private static AuditRun run() {
        AuditRun r = new AuditRun();
        r.setId(1L);
        return r;
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = LintAnalyzersTest.class.getResourceAsStream("/lint/" + name)) {
            assertNotNull(in, "fixture ausente: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path fakeBin(Path dir, String name, String body) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rwxr-xr-x"));
        return f;
    }

    private static Map<String, Finding> byRule(List<Finding> fs) {
        return fs.stream().collect(Collectors.toMap(Finding::getRuleId, f -> f, (a, b) -> a));
    }

    // ------------------------------------------------------------------ PMD: leitura

    @Test
    void pmdLeASaidaRealComCaminhoRelativoSeveridadeECategoria() throws IOException {
        List<Finding> fs = new PmdAnalyzer().parse(fixture("pmd-real.json"), PROJ, run());

        assertEquals(8, fs.size());
        assertTrue(fs.stream().allMatch(f -> "src/main/java/x/Bad.java".equals(f.getFilePath())), "caminho relativo ao projeto");
        assertTrue(fs.stream().allMatch(f -> "PMD".equals(f.getToolSource())));
        Map<String, Finding> m = byRule(fs);
        Finding empty = m.get("pmd-EmptyCatchBlock");
        assertEquals(7, empty.getLineNumber());
        assertEquals(Severity.MEDIUM, empty.getSeverity(), "Error Prone com prioridade 3 sobe para MEDIUM");
        assertEquals(Category.QUALITY, empty.getCategory());
        assertEquals(Severity.LOW, m.get("pmd-UnusedPrivateField").getSeverity(), "Best Practices prioridade 3 fica LOW");
        assertTrue(empty.getDescription().contains("https://docs.pmd-code.org/"), "link da regra na descrição");
        assertTrue(empty.getTitle().startsWith("PMD EmptyCatchBlock"));
    }

    @Test
    void pmdMapeiaPrioridadeERulesetEIgnoraPastasDeBuildETeste() {
        assertEquals(Severity.HIGH, PmdAnalyzer.severity(1, "Best Practices"));
        assertEquals(Severity.MEDIUM, PmdAnalyzer.severity(2, "Design"));
        assertEquals(Severity.LOW, PmdAnalyzer.severity(3, "Design"));
        assertEquals(Severity.INFO, PmdAnalyzer.severity(5, "Design"));
        assertEquals(Severity.HIGH, PmdAnalyzer.severity(1, "Error Prone"), "já é HIGH: não passa de HIGH");
        assertEquals(Severity.MEDIUM, PmdAnalyzer.severity(3, "Security"));
        assertEquals(Category.SECURITY, PmdAnalyzer.category("Security"));
        assertEquals(Category.ARCHITECTURE, PmdAnalyzer.category("Design"));
        assertEquals(Category.QUALITY, PmdAnalyzer.category("Error Prone"));

        String json = """
                {"files":[
                 {"filename":"/proj/src/test/java/ATest.java","violations":[{"rule":"R1","ruleset":"Design","priority":3,"beginline":1,"description":"d"}]},
                 {"filename":"/proj/target/gen/G.java","violations":[{"rule":"R2","ruleset":"Design","priority":3,"beginline":1,"description":"d"}]},
                 {"filename":"/proj/src/main/java/A.java","violations":[{"rule":"R3","ruleset":"Design","priority":3,"beginline":4,"description":"d"}]}]}""";
        List<Finding> fs = new PmdAnalyzer().parse(json, PROJ, run());
        assertEquals(1, fs.size());
        assertEquals("pmd-R3", fs.get(0).getRuleId());
    }

    @Test
    void pmdNaoUsaARegraDeRetornoNuloQueTambemApontaArrays() {
        assertTrue(PmdAnalyzer.DEFAULT_RULESET.contains("<exclude name=\"ReturnEmptyCollectionRatherThanNull\"/>"),
                "null em byte[] é sinal de entrada inválida; coleções ficam com a regra Semgrep");
    }

    @Test
    void pmdComLimiteMantemOsMaisGravesERelatorioInvalidoNaoQuebra() {
        PmdAnalyzer p = new PmdAnalyzer();
        p.maxFindings = 1;
        String json = """
                {"files":[{"filename":"/proj/A.java","violations":[
                 {"rule":"Leve","ruleset":"Design","priority":4,"beginline":1,"description":"d"},
                 {"rule":"Grave","ruleset":"Design","priority":1,"beginline":2,"description":"d"}]}]}""";
        List<Finding> fs = p.parse(json, PROJ, run());
        assertEquals(1, fs.size());
        assertEquals("pmd-Grave", fs.get(0).getRuleId());

        assertTrue(p.parse("isto não é json", PROJ, run()).isEmpty());
        assertTrue(p.parse("", PROJ, run()).isEmpty());
        assertTrue(p.parse("{\"files\":[]}", PROJ, run()).isEmpty());
    }

    // ------------------------------------------------------------------ ESLint: leitura

    @Test
    void eslintLeASaidaRealEUsaSeveridadeDoProprioEslint() throws IOException {
        List<Finding> fs = new EslintAnalyzer().parse(fixture("eslint-real.json"), PROJ, run());

        assertEquals(7, fs.size());
        assertTrue(fs.stream().allMatch(f -> "web/bad.ts".equals(f.getFilePath())), "caminho relativo ao projeto");
        Map<String, Finding> m = byRule(fs);
        assertEquals(Severity.MEDIUM, m.get("eslint-no-var").getSeverity(), "severity 2 = error");
        assertEquals(Severity.LOW, m.get("eslint-no-console").getSeverity(), "severity 1 = warn");
        assertEquals(2, m.get("eslint-no-var").getLineNumber());
        assertNotNull(m.get("eslint-@typescript-eslint/no-explicit-any"));
        assertEquals("ESLint", m.get("eslint-eqeqeq").getToolSource());
    }

    @Test
    void eslintIgnoraErroDeSintaxeSemRegraEPastasIgnoradas() {
        String json = """
                [{"filePath":"/proj/src/broken.ts","messages":[
                   {"ruleId":null,"fatal":true,"severity":2,"message":"Parsing error: Unexpected token","line":3}]},
                 {"filePath":"/proj/node_modules/lib/x.js","messages":[{"ruleId":"no-var","severity":2,"message":"m","line":1}]},
                 {"filePath":"/proj/src/ok.ts","messages":[
                   {"ruleId":"no-eval","severity":2,"message":"eval is evil","line":9}]}]""";
        List<Finding> fs = new EslintAnalyzer().parse(json, PROJ, run());

        assertEquals(1, fs.size());
        assertEquals("eslint-no-eval", fs.get(0).getRuleId());
        assertEquals(Category.SECURITY, fs.get(0).getCategory());
        assertTrue(new EslintAnalyzer().parse("{}", PROJ, run()).isEmpty());
        assertTrue(new EslintAnalyzer().parse("lixo", PROJ, run()).isEmpty());
    }

    // ------------------------------------------------------------------ execução com executáveis falsos

    @Test
    void pmdExecutadoDePontaAPontaUsaRulesetEmbutidoEApagaOsTemporarios() throws Exception {
        Path bin = Files.createTempDirectory("fakebin");
        Path project = Files.createTempDirectory("proj");
        Path args = bin.resolve("args.txt");
        Path rulesetCopy = bin.resolve("ruleset-usado.xml");
        // grava argumentos, copia o ruleset recebido e escreve o relatório em --report-file
        fakeBin(bin, "pmd", "echo \"$@\" > " + args + "\n"
                + "while [ $# -gt 0 ]; do case \"$1\" in --report-file) R=\"$2\";; --rulesets) S=\"$2\";; esac; shift; done\n"
                + "cp \"$S\" " + rulesetCopy + "\n"
                + "cat > \"$R\" <<'EOF'\n" + fixture("pmd-real.json") + "\nEOF");

        PmdAnalyzer p = new PmdAnalyzer();
        p.enabled = true;
        p.timeoutSeconds = 30;
        p.rulesetPath = java.util.Optional.empty();
        p.maxFindings = 500;
        p.pathEnv = bin.toString();
        assertTrue(p.isAvailable());

        List<Finding> fs = p.analyze(Path.of("/proj"), run()).findings();   // caminhos da fixture são /proj/...
        assertEquals(8, fs.size());

        String used = Files.readString(args);
        assertTrue(used.contains("--no-cache") && used.contains("--format json"), used);
        assertTrue(used.contains("--no-fail-on-violation"), "violação não pode virar erro de execução: " + used);
        String xml = Files.readString(rulesetCopy);
        assertTrue(xml.contains("category/java/errorprone.xml") && xml.contains("<exclude name=\"CyclomaticComplexity\"/>"),
                "ruleset embutido deixa a complexidade para o CleanCode");
        assertNotNull(project);
    }

    @Test
    void pmdComFalhaOuAusenteNaoQuebra() throws Exception {
        Path bin = Files.createTempDirectory("fakebin");
        fakeBin(bin, "pmd", "exit 1");
        PmdAnalyzer p = new PmdAnalyzer();
        p.enabled = true;
        p.timeoutSeconds = 30;
        p.rulesetPath = java.util.Optional.empty();
        p.pathEnv = bin.toString();
        assertTrue(p.analyze(Files.createTempDirectory("proj"), run()).findings().isEmpty());

        p.pathEnv = Files.createTempDirectory("vazio").toString();
        assertFalse(p.isAvailable());
        assertTrue(p.analyze(Files.createTempDirectory("proj"), run()).findings().isEmpty());

        p.enabled = false;
        p.pathEnv = bin.toString();
        assertFalse(p.isAvailable(), "desligado por configuração");
    }

    @Test
    void eslintNuncaCarregaAConfiguracaoDoRepositorioERodaNaRaizDoProjeto() throws Exception {
        Path bin = Files.createTempDirectory("fakebin");
        Path project = Files.createTempDirectory("proj").toRealPath();
        Path config = Files.createTempFile("eslint.config", ".mjs");
        Path args = bin.resolve("args.txt");
        Path cwd = bin.resolve("cwd.txt");
        // exit 1 = "há problemas", o código normal do ESLint quando acha algo
        fakeBin(bin, "eslint", "echo \"$@\" > " + args + "\npwd > " + cwd + "\n"
                + "while [ $# -gt 0 ]; do if [ \"$1\" = \"-o\" ]; then R=\"$2\"; fi; shift; done\n"
                + "cat > \"$R\" <<'EOF'\n" + fixture("eslint-real.json") + "\nEOF\nexit 1");

        EslintAnalyzer e = new EslintAnalyzer();
        e.enabled = true;
        e.timeoutSeconds = 30;
        e.maxFindings = 500;
        e.configPath = config.toString();
        e.pathEnv = bin.toString();
        assertTrue(e.isAvailable());

        List<Finding> fs = e.analyze(project, run()).findings();

        assertEquals(7, fs.size(), "exit 1 do ESLint é resultado normal");
        String used = Files.readString(args);
        assertTrue(used.contains("--no-config-lookup"), "config do repositório é código: nunca carregar: " + used);
        assertTrue(used.contains("-c " + config), used);
        assertTrue(used.contains("**/node_modules/**") && used.contains("**/*.d.ts"), used);
        assertEquals(project.toString(), Files.readString(cwd).trim(), "ESLint usa o diretório atual como raiz");
    }

    @Test
    void eslintSoUsaRegrasDoAngularQuandoOProjetoDependeDeAngular() throws Exception {
        Path nest = Files.createTempDirectory("nest");
        Files.writeString(nest.resolve("package.json"), "{\"dependencies\":{\"@nestjs/core\":\"^10.0.0\"}}");
        assertFalse(EslintAnalyzer.usaAngular(nest));

        Path ng = Files.createTempDirectory("ng");
        Files.createDirectories(ng.resolve("web"));
        Files.writeString(ng.resolve("web/package.json"), "{\"dependencies\":{\"@angular/core\":\"^19.0.0\"}}");
        assertTrue(EslintAnalyzer.usaAngular(ng));

        Path soDependencia = Files.createTempDirectory("dep");
        Files.createDirectories(soDependencia.resolve("node_modules/x"));
        Files.writeString(soDependencia.resolve("node_modules/x/package.json"), "{\"peerDependencies\":{\"@angular/core\":\"*\"}}");
        assertFalse(EslintAnalyzer.usaAngular(soDependencia), "package.json de dependências não conta");
    }

    @Test
    void eslintExigeOArquivoDeConfiguracaoEIgnoraErroDeExecucao() throws Exception {
        Path bin = Files.createTempDirectory("fakebin");
        fakeBin(bin, "eslint", "exit 2");
        EslintAnalyzer e = new EslintAnalyzer();
        e.enabled = true;
        e.timeoutSeconds = 30;
        e.maxFindings = 500;
        e.pathEnv = bin.toString();

        e.configPath = "/nao/existe/eslint.config.mjs";
        assertFalse(e.isAvailable(), "sem configuração não roda (e não usa a do repositório)");
        assertTrue(e.analyze(Files.createTempDirectory("proj"), run()).findings().isEmpty());

        e.configPath = Files.createTempFile("eslint.config", ".mjs").toString();
        assertTrue(e.isAvailable());
        assertTrue(e.analyze(Files.createTempDirectory("proj"), run()).findings().isEmpty(), "exit 2 = erro, sem findings");
    }
}
