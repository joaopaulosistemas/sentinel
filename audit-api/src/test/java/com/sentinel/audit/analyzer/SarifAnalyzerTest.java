package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.PackFixtures;
import com.sentinel.audit.pack.PackLoader;
import com.sentinel.audit.pack.RulePack;
import com.sentinel.audit.pack.ToolSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SarifAnalyzerTest {

    private static final Path PROJ = Path.of("/proj");

    private static AuditRun run() {
        AuditRun r = new AuditRun();
        r.setId(1L);
        return r;
    }

    private static String fixture() throws IOException {
        try (InputStream in = SarifAnalyzerTest.class.getResourceAsStream("/lint/semgrep-real.sarif")) {
            assertNotNull(in, "fixture ausente");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ToolSpec spec(String id, String input, List<String> cmd, Map<String, Severity> map) {
        return new ToolSpec(id, id, cmd, input, Category.QUALITY, map, 30);
    }

    @Test
    void leOSarifRealDoSemgrep() throws Exception {
        List<Finding> fs = new SarifAnalyzer().parse(fixture(), spec("ext", "*.sarif", null, Map.of()), PROJ, run());

        assertEquals(4, fs.size());
        for (Finding f : fs) {
            assertTrue(f.getRuleId().startsWith("ext-"), f.getRuleId());
            assertNotNull(f.getFilePath());
            assertFalse(f.getFilePath().startsWith("/"), "caminho relativo: " + f.getFilePath());
            assertTrue(f.getLineNumber() > 0);
            assertNotNull(f.getTitle());
            assertEquals("ext", f.getToolSource());
            assertEquals(Category.QUALITY, f.getCategory());
        }
    }

    @Test
    void severidadeVemDoMapaDoPacoteDepoisDoNivelDaRegra() throws Exception {
        String sarif = fixture();
        List<Finding> padrao = new SarifAnalyzer().parse(sarif, spec("ext", "*", null, Map.of()), PROJ, run());
        List<Finding> mapeado = new SarifAnalyzer().parse(sarif, spec("ext", "*", null,
                Map.of("warning", Severity.CRITICAL, "error", Severity.CRITICAL, "note", Severity.CRITICAL)), PROJ, run());

        assertTrue(padrao.stream().anyMatch(f -> f.getSeverity() != Severity.CRITICAL), "sem mapa usa o padrão por nível");
        assertTrue(mapeado.stream().allMatch(f -> f.getSeverity() == Severity.CRITICAL));
    }

    @Test
    void securitySeverityTemPrioridadeSobreONivel() throws Exception {
        String sarif = """
                {"runs":[{"tool":{"driver":{"rules":[
                  {"id":"R1","properties":{"security-severity":"9.5"}},
                  {"id":"R2","properties":{"security-severity":"7.5"}},
                  {"id":"R3","properties":{"security-severity":"5.0"}},
                  {"id":"R4","properties":{"security-severity":"2.0"}},
                  {"id":"R5","defaultConfiguration":{"level":"error"}}]}},
                 "results":[
                  {"ruleId":"R1","level":"note","message":{"text":"a"},"locations":[{"physicalLocation":{"artifactLocation":{"uri":"A.java"},"region":{"startLine":3}}}]},
                  {"ruleId":"R2","message":{"text":"b"}},
                  {"ruleId":"R3","message":{"text":"c"}},
                  {"ruleId":"R4","message":{"text":"d"}},
                  {"ruleId":"R5","message":{"text":"e"}},
                  {"ruleId":"R6","level":"none","message":{"text":"f"}}]}]}
                """;
        List<Finding> fs = new SarifAnalyzer().parse(sarif, spec("t", "*", null, Map.of()), PROJ, run());

        assertEquals(List.of(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.HIGH, Severity.INFO),
                fs.stream().map(Finding::getSeverity).toList());
        assertEquals("A.java", fs.get(0).getFilePath());
        assertEquals(3, fs.get(0).getLineNumber());
    }

    @Test
    void caminhosEmFormatoDeUriViramRelativosAoProjeto() {
        String abs = PROJ.toAbsolutePath().toString();
        for (String uri : List.of("file://" + abs + "/src/A.java", "file:" + abs + "/src/A.java", abs + "/src/A.java", "src/A.java")) {
            String sarif = "{\"runs\":[{\"tool\":{\"driver\":{\"rules\":[]}},\"results\":[{\"ruleId\":\"R\",\"message\":{\"text\":\"m\"},"
                    + "\"locations\":[{\"physicalLocation\":{\"artifactLocation\":{\"uri\":\"" + uri + "\"},\"region\":{\"startLine\":2}}}]}]}]}";
            List<Finding> fs = new SarifAnalyzer().parse(sarif, spec("t", "*", null, Map.of()), PROJ.toAbsolutePath(), run());
            assertEquals(java.nio.file.Path.of("src", "A.java").toString(), fs.get(0).getFilePath(), uri);
        }
    }

    @Test
    void ignoraSuprimidosSemIdEJsonInvalido() {
        String sarif = """
                {"runs":[{"results":[
                  {"ruleId":"A","message":{"text":"x"},"suppressions":[{"kind":"inSource"}]},
                  {"message":{"text":"sem id"}},
                  {"ruleId":"B","message":{"text":"ok"}}]}]}
                """;
        SarifAnalyzer a = new SarifAnalyzer();
        ToolSpec s = spec("t", "*", null, Map.of());

        assertEquals(List.of("t-B"), a.parse(sarif, s, PROJ, run()).stream().map(Finding::getRuleId).toList());
        assertEquals(List.of(), a.parse("{isso não é json", s, PROJ, run()));
        assertEquals(List.of(), a.parse("", s, PROJ, run()));
        assertEquals(List.of(), a.parse(null, s, PROJ, run()));
    }

    @Test
    void limitaOTotalDeResultadosPorFerramenta() {
        StringBuilder sb = new StringBuilder("{\"runs\":[{\"results\":[");
        for (int i = 0; i < 1500; i++) {
            sb.append(i > 0 ? "," : "").append("{\"ruleId\":\"R").append(i).append("\",\"message\":{\"text\":\"m\"}}");
        }
        sb.append("]}]}");
        assertEquals(1000, new SarifAnalyzer().parse(sb.toString(), spec("t", "*", null, Map.of()), PROJ, run()).size());
    }

    // ------------------------------------------------------------------ pacote + modo input

    private static AuditPolicy policyWithTools(String toolsYaml) throws IOException {
        Path root = PackFixtures.tempRoot();
        Path dir = PackFixtures.pack(root, "ferramentas", "1.0.0", true);
        PackFixtures.write(dir.resolve("tools/t.yml"), toolsYaml);
        RulePack p = PackLoader.load(dir);
        return AuditPolicy.defaults(List.of(p));
    }

    @Test
    void modoInputLeArquivosDoProjetoEIgnoraPastasIgnoradas() throws Exception {
        Path proj = Files.createTempDirectory("proj").toRealPath();
        Files.createDirectories(proj.resolve("reports"));
        Files.writeString(proj.resolve("reports/a.sarif"), fixture());
        Files.createDirectories(proj.resolve("node_modules/x"));
        Files.writeString(proj.resolve("node_modules/x/b.sarif"), fixture());
        Files.writeString(proj.resolve("reports/ignorar.txt"), fixture());

        AuditPolicy pol = policyWithTools("- id: ci\n  input: \"reports/*.sarif\"\n");
        AnalyzerResult r = new SarifAnalyzer().analyze(proj, run(), pol);

        assertEquals(4, r.findings().size(), "só reports/a.sarif");
        assertEquals("SARIF", r.analyzerName());
    }

    @Test
    void semPoliticaOuSemFerramentasNaoFazNada() throws Exception {
        Path proj = Files.createTempDirectory("proj").toRealPath();
        assertEquals(0, new SarifAnalyzer().analyze(proj, run()).findings().size());
        assertEquals(0, new SarifAnalyzer().analyze(proj, run(), AuditPolicy.defaults(List.of())).findings().size());
    }

    @Test
    void arquivoSarifQuebradoNaoDerrubaAsOutrasFontes() throws Exception {
        Path proj = Files.createTempDirectory("proj").toRealPath();
        Files.writeString(proj.resolve("quebrado.sarif"), "{{{");
        Files.writeString(proj.resolve("bom.sarif"), fixture());
        AuditPolicy pol = policyWithTools("- id: ci\n  input: \"*.sarif\"\n");
        assertEquals(4, new SarifAnalyzer().analyze(proj, run(), pol).findings().size());
    }

    // ------------------------------------------------------------------ modo command

    private Path fakeTool(Path dir) throws IOException {
        Path exe = dir.resolve("fake-sarif");
        // grava o SARIF no caminho recebido como último argumento
        Files.writeString(exe, "#!/bin/sh\ncp \"" + writeFixture(dir) + "\" \"$1\"\n");
        assertTrue(exe.toFile().setExecutable(true));
        return exe;
    }

    private Path writeFixture(Path dir) throws IOException {
        Path f = dir.resolve("saida.sarif");
        Files.writeString(f, fixture());
        return f;
    }

    @Test
    void modoCommandSoRodaComAutorizacaoEExecutaSemShell() throws Exception {
        Path bin = Files.createTempDirectory("bin").toRealPath();
        Path exe = fakeTool(bin);
        Path proj = Files.createTempDirectory("proj").toRealPath();
        AuditPolicy pol = policyWithTools("- id: ext\n  command: [\"" + exe + "\", \"{output}\"]\n");

        SarifAnalyzer bloqueado = new SarifAnalyzer();
        assertEquals(0, bloqueado.analyze(proj, run(), pol).findings().size(), "allow-tools desligado por padrão");

        SarifAnalyzer liberado = new SarifAnalyzer();
        liberado.allowTools = true;
        assertEquals(4, liberado.analyze(proj, run(), pol).findings().size());
    }

    @Test
    void executavelInexistenteOuQueNaoGeraArquivoEIgnorado() throws Exception {
        Path proj = Files.createTempDirectory("proj").toRealPath();
        SarifAnalyzer a = new SarifAnalyzer();
        a.allowTools = true;
        a.pathEnv = "/nada";

        AuditPolicy semExe = policyWithTools("- id: xx\n  command: [ferramenta-que-nao-existe, \"{output}\"]\n");
        assertEquals(0, a.analyze(proj, run(), semExe).findings().size());

        AuditPolicy vazio = policyWithTools("- id: yy\n  command: [\"/bin/true\", \"{output}\"]\n");
        assertEquals(0, a.analyze(proj, run(), vazio).findings().size(), "terminou sem escrever SARIF");
    }

    @Test
    void metacaracteresNoProjetoNaoViramComandos() throws Exception {
        Path base = Files.createTempDirectory("proj").toRealPath();
        Path proj = Files.createDirectories(base.resolve("a;touch pwned"));
        Path bin = Files.createTempDirectory("bin").toRealPath();
        Files.writeString(bin.resolve("t"), "#!/bin/sh\necho \"$1\" > \"$2\"\n");
        bin.resolve("t").toFile().setExecutable(true);
        AuditPolicy pol = policyWithTools("- id: zz\n  command: [\"" + bin.resolve("t") + "\", \"{project}\", \"{output}\"]\n");

        SarifAnalyzer a = new SarifAnalyzer();
        a.allowTools = true;
        a.analyze(proj, run(), pol);

        assertFalse(Files.exists(base.resolve("pwned")) || Files.exists(proj.resolve("pwned")), "sem shell, sem injeção");
    }

    // ------------------------------------------------------------------ stdout, applies-to, {files}, pacote confiável

    @Test
    void modoStdoutCapturaOSarifImpressoPelaFerramenta() throws Exception {
        Path bin = Files.createTempDirectory("bin").toRealPath();
        Path exe = bin.resolve("imprime");
        Files.writeString(exe, "#!/bin/sh\ncat \"" + writeFixture(bin) + "\"\n");
        exe.toFile().setExecutable(true);
        Path proj = Files.createTempDirectory("proj").toRealPath();
        AuditPolicy pol = policyWithTools("- id: pr\n  stdout: true\n  command: [\"" + exe + "\"]\n");

        SarifAnalyzer a = new SarifAnalyzer();
        a.allowTools = true;
        assertEquals(4, a.analyze(proj, run(), pol).findings().size());
    }

    @Test
    void appliesToSoRodaQuandoOProjetoTemOTipoDeArquivo() throws Exception {
        Path bin = Files.createTempDirectory("bin").toRealPath();
        Path marca = bin.resolve("rodou");
        Path exe = bin.resolve("marca");
        Files.writeString(exe, "#!/bin/sh\necho \"$@\" > \"" + marca + "\"\ncp \"" + writeFixture(bin) + "\" \"$1\"\n");
        exe.toFile().setExecutable(true);
        AuditPolicy pol = policyWithTools("- id: so-py\n  applies-to: [\"**/*.py\"]\n  command: [\"" + exe + "\", \"{output}\"]\n");
        SarifAnalyzer a = new SarifAnalyzer();
        a.allowTools = true;

        Path semPy = Files.createTempDirectory("proj").toRealPath();
        Files.writeString(semPy.resolve("a.js"), "1");
        assertEquals(0, a.analyze(semPy, run(), pol).findings().size());
        assertFalse(Files.exists(marca), "nem chegou a executar");

        Path comPy = Files.createTempDirectory("proj").toRealPath();
        Files.writeString(comPy.resolve("app.py"), "1");
        assertEquals(4, a.analyze(comPy, run(), pol).findings().size());
        assertTrue(Files.exists(marca));
    }

    @Test
    void filesViramArgumentosRelativosSemNomesQueSeriamOpcoes() throws Exception {
        Path bin = Files.createTempDirectory("bin").toRealPath();
        Path marca = bin.resolve("args");
        Path exe = bin.resolve("lista");
        Files.writeString(exe, "#!/bin/sh\nprintf '%s\\n' \"$@\" > \"" + marca + "\"\ncat \"" + writeFixture(bin) + "\"\n");
        exe.toFile().setExecutable(true);
        AuditPolicy pol = policyWithTools("- id: ls\n  stdout: true\n  applies-to: [\"**/Dockerfile\", \"**/*.yaml\"]\n"
                + "  command: [\"" + exe + "\", \"{files}\"]\n");
        Path proj = Files.createTempDirectory("proj").toRealPath();
        Files.createDirectories(proj.resolve("svc/node_modules"));
        Files.writeString(proj.resolve("Dockerfile"), "FROM x");
        Files.writeString(proj.resolve("svc/Dockerfile"), "FROM y");
        Files.writeString(proj.resolve("svc/node_modules/Dockerfile"), "FROM z");
        Files.writeString(proj.resolve("-evil.yaml"), "x: 1");
        Files.writeString(proj.resolve("ok.yaml"), "x: 1");
        SarifAnalyzer a = new SarifAnalyzer();
        a.allowTools = true;

        a.analyze(proj, run(), pol);

        assertEquals(List.of("Dockerfile", "ok.yaml", "svc/Dockerfile"), Files.readAllLines(marca),
                "zero pastas casa, node_modules fica de fora e '-evil.yaml' nunca vira opção");
    }

    @Test
    void globComDoisAsterisksNoMeioCasaZeroPastas() throws Exception {
        Path proj = Files.createTempDirectory("proj").toRealPath();
        Files.createDirectories(proj.resolve("k8s"));
        Files.createDirectories(proj.resolve("a/k8s/sub"));
        Files.writeString(proj.resolve("k8s/pod.yaml"), "x");
        Files.writeString(proj.resolve("a/k8s/sub/deep.yaml"), "x");
        Files.writeString(proj.resolve("other.yaml"), "x");

        assertEquals(List.of("a/k8s/sub/deep.yaml", "k8s/pod.yaml"),
                SarifAnalyzer.matchingFiles(proj, List.of("**/k8s/**/*.yaml")));
    }

    @Test
    void pacoteConfiavelRodaSemAllowToolsMasOutroNao() throws Exception {
        Path bin = Files.createTempDirectory("bin").toRealPath();
        Path exe = fakeTool(bin);
        Path proj = Files.createTempDirectory("proj").toRealPath();
        AuditPolicy pol = policyWithTools("- id: ext\n  command: [\"" + exe + "\", \"{output}\"]\n");

        assertEquals(0, new SarifAnalyzer().analyze(proj, run(), pol).findings().size());
        assertEquals(0, new SarifAnalyzer().analyze(proj, run(), pol.withTrustedToolPacks(java.util.Set.of("outro-pacote")))
                .findings().size(), "confiança é por id de pacote");
        assertEquals(4, new SarifAnalyzer().analyze(proj, run(), pol.withTrustedToolPacks(java.util.Set.of("ferramentas")))
                .findings().size());
    }
}
