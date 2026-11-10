package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Betterleaks 1.x: leitura do relatório real, execução com executável falso, opt-in e substituição do Gitleaks. */
class BetterleaksAnalyzerTest {

    private static final String SECRET = "AKIAIOSFODNN7ABCDEFG";

    /** Formato da saída real do betterleaks 1.10.0 (mesma lista do Gitleaks; campos demais omitidos). */
    private static final String BETTERLEAKS_JSON = """
            [
              {"RuleID":"aws-access-token","Description":"Identified a pattern that may indicate AWS credentials",
               "StartLine":1,"EndLine":1,"Match":"aws_access_key_id = \\"AKIAIOSFODNN7ABCDEFG\\"",
               "Secret":"AKIAIOSFODNN7ABCDEFG","File":"/work/proj/src/Config.java","Commit":"531b8f7aabbccdd"},
              {"RuleID":"aws-access-token","Description":"Identified a pattern that may indicate AWS credentials",
               "StartLine":4,"Match":"aws_access_key_id = \\"AKIAIOSFODNN7ABCDEFG\\"",
               "Secret":"AKIAIOSFODNN7ABCDEFG","File":"/work/proj/src/Config.java","Commit":"0123456789abcdef"}
            ]
            """;

    private static AuditRun run() {
        AuditRun r = new AuditRun();
        r.setId(1L);
        return r;
    }

    private static Path fakeBin(Path dir, String name, String body) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rwxr-xr-x"));
        return f;
    }

    private static long leftovers(String prefix) throws IOException {
        try (Stream<Path> s = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return s.filter(p -> p.getFileName().toString().startsWith(prefix)).count();
        }
    }

    @Test
    void leOrelatorioNuncaGuardaOSegredoEUnificaRepeticoesNoHistorico() {
        List<Finding> fs = new BetterleaksAnalyzer().parse(BETTERLEAKS_JSON, Path.of("/work/proj"), run());

        assertEquals(1, fs.size());                      // mesmo segredo, mesmo arquivo, dois commits = um finding
        Finding f = fs.get(0);
        assertEquals("betterleaks-aws-access-token", f.getRuleId());
        assertEquals("Betterleaks", f.getToolSource());
        assertEquals("src/Config.java", f.getFilePath());
        assertEquals(Severity.HIGH, f.getSeverity());
        assertEquals(Category.SECURITY, f.getCategory());
        assertTrue(f.getDescription().startsWith("O Betterleaks detectou"));
        assertTrue(f.getDescription().contains("531b8f7a"));          // commit curto
        String all = f.getTitle() + f.getDescription() + f.getCodeSnippet() + f.getRuleId();
        assertFalse(all.contains(SECRET), "o segredo vazou no finding");
        assertTrue(f.getCodeSnippet().startsWith("segredo sha256:"));
    }

    @Test
    void toleraRelatorioVazioOuInvalido() {
        BetterleaksAnalyzer b = new BetterleaksAnalyzer();
        assertTrue(b.parse("", Path.of("/p"), run()).isEmpty());
        assertTrue(b.parse("[]", Path.of("/p"), run()).isEmpty());
        assertTrue(b.parse("{\"nao\":\"lista\"}", Path.of("/p"), run()).isEmpty());
        assertTrue(b.parse("isto não é json", Path.of("/p"), run()).isEmpty());
    }

    @Test
    void gitleaksEBetterleaksUsamOMesmoLeitorMasRegrasComPrefixoProprio() {
        String json = BETTERLEAKS_JSON;
        Finding g = new GitleaksAnalyzer().parse(json, Path.of("/work/proj"), run()).get(0);
        Finding b = new BetterleaksAnalyzer().parse(json, Path.of("/work/proj"), run()).get(0);
        assertEquals("gitleaks-aws-access-token", g.getRuleId());
        assertEquals("betterleaks-aws-access-token", b.getRuleId());
        assertEquals(g.getCodeSnippet(), b.getCodeSnippet());          // mesmo hash do segredo
    }

    @Test
    void vemDesligadoPorPadraoMesmoComOExecutavelNoPath() throws Exception {
        Path bin = Files.createTempDirectory("fakebin");
        fakeBin(bin, "betterleaks", "exit 0");

        BetterleaksAnalyzer b = new BetterleaksAnalyzer();
        b.pathEnv = bin.toString();
        assertFalse(b.enabled, "o Betterleaks só roda se for ligado");
        assertFalse(b.isAvailable());

        b.enabled = true;
        assertTrue(b.isAvailable());
        b.pathEnv = Files.createTempDirectory("semnada").toString();
        assertFalse(b.isAvailable(), "ligado mas sem o executável: indisponível");
    }

    @Test
    void ligadoSubstituiOGitleaksEOsAnalisadoresInternosDeSegredo() {
        List<Class<? extends CodeAnalyzer>> r = new BetterleaksAnalyzer().replaces();
        assertTrue(r.contains(GitleaksAnalyzer.class));
        assertTrue(r.contains(SecretsAnalyzer.class));
        assertTrue(r.contains(HistorySecretsAnalyzer.class));
        assertTrue(new GitleaksAnalyzer().replaces().stream().noneMatch(BetterleaksAnalyzer.class::isAssignableFrom));
    }

    @Test
    void executadoDePontaAPontaUsaDirOuGitEApagaORelatorioComOSegredo() throws Exception {
        Path bin = Files.createTempDirectory("fakebin");
        Path project = Files.createTempDirectory("proj");
        Path args = bin.resolve("args.txt");
        // grava os argumentos e escreve o relatório no --report-path, como o betterleaks faz
        fakeBin(bin, "betterleaks", "echo \"$@\" > " + args + "\n"
                + "while [ $# -gt 0 ]; do if [ \"$1\" = \"--report-path\" ]; then R=\"$2\"; fi; shift; done\n"
                + "cat > \"$R\" <<'EOF'\n" + BETTERLEAKS_JSON + "\nEOF");
        long antes = leftovers("betterleaks-");

        BetterleaksAnalyzer b = new BetterleaksAnalyzer();
        b.enabled = true;
        b.timeoutSeconds = 30;
        b.pathEnv = bin.toString();

        AnalyzerResult r = b.analyze(project, run());

        assertEquals(1, r.findings().size());
        String used = Files.readString(args);
        assertTrue(used.startsWith("dir " + project), "sem .git deve varrer só os arquivos: " + used);
        assertTrue(used.contains("--exit-code 0"));
        assertTrue(used.contains("--report-format json"));
        assertFalse(used.contains("--unsafe-auto-load-config"), "nunca carregar a config do projeto auditado");
        assertFalse(used.contains("--validation"), "nunca consultar o provedor da credencial");
        assertEquals(antes, leftovers("betterleaks-"), "relatório temporário com segredo não foi apagado");

        Files.createDirectory(project.resolve(".git"));
        b.analyze(project, run());
        assertTrue(Files.readString(args).startsWith("git " + project), "com .git deve varrer o histórico");
    }

    @Test
    void ausenteOuComFalhaNaoQuebraNemGeraFindings() throws Exception {
        Path vazio = Files.createTempDirectory("semnada");
        Path project = Files.createTempDirectory("proj");

        BetterleaksAnalyzer b = new BetterleaksAnalyzer();
        b.enabled = true;
        b.timeoutSeconds = 5;
        b.pathEnv = vazio.toString();
        assertTrue(b.analyze(project, run()).findings().isEmpty());

        fakeBin(vazio, "betterleaks", "exit 2");
        assertTrue(b.isAvailable());
        assertTrue(b.analyze(project, run()).findings().isEmpty());
    }
}
