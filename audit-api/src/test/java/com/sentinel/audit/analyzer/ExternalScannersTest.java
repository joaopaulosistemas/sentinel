package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.Fingerprints;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Gitleaks e Trivy: leitura da saída real de cada ferramenta, execução com executáveis falsos e substituição dos internos. */
class ExternalScannersTest {

    private static final String SECRET = "AKIAIOSFODNN7EXAMPLE";

    private static final String GITLEAKS_JSON = """
            [
              {"Description":"AWS Access Key","StartLine":12,"EndLine":12,"Match":"key = AKIAIOSFODNN7EXAMPLE",
               "Secret":"AKIAIOSFODNN7EXAMPLE","File":"/work/proj/src/Config.java","Commit":"0123456789abcdef",
               "RuleID":"aws-access-token","Fingerprint":"0123:src/Config.java:aws-access-token:12"},
              {"Description":"AWS Access Key","StartLine":9,"Match":"key = AKIAIOSFODNN7EXAMPLE",
               "Secret":"AKIAIOSFODNN7EXAMPLE","File":"/work/proj/src/Config.java","Commit":"fedcba9876543210","RuleID":"aws-access-token"},
              {"Description":"Private Key","StartLine":1,"Secret":"-----BEGIN RSA PRIVATE KEY-----abc","Match":"x",
               "File":"keys/id_rsa","Commit":"","RuleID":"private-key"}
            ]""";

    private static final String TRIVY_JSON = """
            {"SchemaVersion":2,"Results":[
              {"Target":"pom.xml","Class":"lang-pkgs","Type":"pom","Vulnerabilities":[
                {"VulnerabilityID":"CVE-2021-44228","PkgName":"org.apache.logging.log4j:log4j-core","InstalledVersion":"2.14.1",
                 "FixedVersion":"2.15.0","Severity":"CRITICAL","Title":"Log4Shell","Description":"RCE via JNDI",
                 "PrimaryURL":"https://avd.aquasec.com/nvd/cve-2021-44228"},
                {"VulnerabilityID":"GHSA-xxxx-yyyy-zzzz","PkgName":"foo:bar","InstalledVersion":"1.0","Severity":"UNKNOWN"}]},
              {"Target":"Dockerfile","Class":"config","Type":"dockerfile","Misconfigurations":[
                {"ID":"DS002","AVDID":"AVD-DS-0002","Title":"Image user should not be 'root'",
                 "Description":"Running as root is risky","Message":"Specify at least 1 USER command in Dockerfile",
                 "Resolution":"Add 'USER <non root user>'","Severity":"HIGH","CauseMetadata":{"StartLine":3,"EndLine":3}}]},
              {"Target":"clean.txt","Class":"secret"}
            ]}""";

    private static AuditRun run() {
        AuditRun r = new AuditRun();
        r.setId(1L);
        return r;
    }

    // ------------------------------------------------------------------ Gitleaks: leitura

    @Test
    void gitleaksNuncaGuardaOSegredoEUnificaRepeticoesNoHistorico() {
        List<Finding> fs = new GitleaksAnalyzer().parse(GITLEAKS_JSON, Path.of("/work/proj"), run());

        assertEquals(2, fs.size());                      // mesmo segredo, mesmo arquivo, dois commits = um finding
        Finding aws = fs.get(0);
        assertEquals("gitleaks-aws-access-token", aws.getRuleId());
        assertEquals("src/Config.java", aws.getFilePath());          // caminho absoluto vira relativo
        assertEquals(12, aws.getLineNumber());
        assertEquals(Severity.HIGH, aws.getSeverity());
        assertEquals(Category.SECURITY, aws.getCategory());
        assertTrue(aws.getDescription().contains("01234567"));       // commit curto
        for (Finding f : fs) {
            String all = f.getTitle() + f.getDescription() + f.getCodeSnippet() + f.getRuleId();
            assertFalse(all.contains(SECRET), "o segredo vazou no finding");
            assertFalse(all.contains("BEGIN RSA"), "o segredo vazou no finding");
        }
        assertTrue(aws.getCodeSnippet().startsWith("segredo sha256:"));
    }

    @Test
    void fingerprintDoSegredoNaoDependeDaLinhaNemDoCommit() {
        String a = GITLEAKS_JSON;
        String b = GITLEAKS_JSON.replace("\"StartLine\":12", "\"StartLine\":99").replace("0123456789abcdef", "aaaaaaaaaaaaaaaa");
        Finding fa = new GitleaksAnalyzer().parse(a, Path.of("/work/proj"), run()).get(0);
        Finding fb = new GitleaksAnalyzer().parse(b, Path.of("/work/proj"), run()).get(0);
        assertEquals(Fingerprints.compute(fa), Fingerprints.compute(fb));

        String outroSegredo = GITLEAKS_JSON.replace(SECRET, "AKIAABCDEFGHIJKLMNOP");
        Finding fc = new GitleaksAnalyzer().parse(outroSegredo, Path.of("/work/proj"), run()).get(0);
        assertNotEquals(Fingerprints.compute(fa), Fingerprints.compute(fc));    // outro segredo = outro problema
    }

    @Test
    void gitleaksToleraRelatorioVazioOuInvalido() {
        GitleaksAnalyzer g = new GitleaksAnalyzer();
        assertTrue(g.parse("", Path.of("/p"), run()).isEmpty());
        assertTrue(g.parse("[]", Path.of("/p"), run()).isEmpty());
        assertTrue(g.parse("{\"nao\":\"lista\"}", Path.of("/p"), run()).isEmpty());
        assertTrue(g.parse("isto não é json", Path.of("/p"), run()).isEmpty());
    }

    // ------------------------------------------------------------------ Trivy: leitura

    @Test
    void trivyMapeiaVulnerabilidadesEMisconfiguracoes() {
        List<Finding> fs = new TrivyAnalyzer().parse(TRIVY_JSON, Path.of("/work/proj"), run());
        assertEquals(3, fs.size());

        Finding log4j = fs.get(0);
        assertEquals(Category.DEPENDENCY, log4j.getCategory());
        assertEquals(Severity.CRITICAL, log4j.getSeverity());
        assertEquals("CVE-2021-44228", log4j.getCveId());
        assertEquals("CVE-2021-44228", log4j.getRuleId());
        assertEquals("pom.xml", log4j.getFilePath());
        assertTrue(log4j.getDescription().contains("2.15.0"));
        assertTrue(log4j.getTitle().contains("2.14.1"));

        assertEquals(Severity.INFO, fs.get(1).getSeverity());                     // UNKNOWN
        assertTrue(fs.get(1).getDescription().contains("Ainda não há versão corrigida"));

        Finding docker = fs.get(2);
        assertEquals(Category.SECURITY, docker.getCategory());
        assertEquals(Severity.HIGH, docker.getSeverity());
        assertEquals("trivy-AVD-DS-0002", docker.getRuleId());
        assertEquals("Dockerfile", docker.getFilePath());
        assertEquals(3, docker.getLineNumber());
        assertTrue(docker.getDescription().contains("USER <non root user>"));
    }

    @Test
    void atualizarAVersaoNaoCriaFindingNovoNoTrivy() {
        Finding antes = new TrivyAnalyzer().parse(TRIVY_JSON, Path.of("/p"), run()).get(0);
        Finding depois = new TrivyAnalyzer().parse(TRIVY_JSON.replace("2.14.1", "2.14.2"), Path.of("/p"), run()).get(0);
        assertEquals(Fingerprints.compute(antes), Fingerprints.compute(depois));
    }

    @Test
    void trivyToleraSaidaSemResultadosOuInvalida() {
        TrivyAnalyzer t = new TrivyAnalyzer();
        assertTrue(t.parse("{}", Path.of("/p"), run()).isEmpty());
        assertTrue(t.parse("{\"Results\":null}", Path.of("/p"), run()).isEmpty());
        assertTrue(t.parse("quebrado", Path.of("/p"), run()).isEmpty());
        assertTrue(t.parse(null, Path.of("/p"), run()).isEmpty());
    }

    // ------------------------------------------------------------------ execução com executáveis falsos

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
    void gitleaksExecutadoDePontaAPontaApagaORelatorioComOSegredo() throws Exception {
        Path bin = Files.createTempDirectory("fakebin");
        Path project = Files.createTempDirectory("proj");
        Path args = bin.resolve("args.txt");
        // grava os argumentos e escreve o relatório no --report-path, como o Gitleaks faz
        fakeBin(bin, "gitleaks", "echo \"$@\" > " + args + "\n"
                + "while [ $# -gt 0 ]; do if [ \"$1\" = \"--report-path\" ]; then R=\"$2\"; fi; shift; done\n"
                + "cat > \"$R\" <<'EOF'\n" + GITLEAKS_JSON + "\nEOF");
        long antes = leftovers("gitleaks-");

        GitleaksAnalyzer g = new GitleaksAnalyzer();
        g.enabled = true;
        g.timeoutSeconds = 30;
        g.pathEnv = bin.toString();
        assertTrue(g.isAvailable());

        AnalyzerResult r = g.analyze(project, run());

        assertEquals(2, r.findings().size());
        String used = Files.readString(args);
        assertTrue(used.contains("--no-git"), "sem .git deve usar --no-git: " + used);
        assertTrue(used.contains("--exit-code 0"));
        assertFalse(used.contains("--redact"));
        assertEquals(antes, leftovers("gitleaks-"), "relatório temporário com segredo não foi apagado");

        Files.createDirectory(project.resolve(".git"));
        g.analyze(project, run());
        assertFalse(Files.readString(args).contains("--no-git"), "com .git deve varrer o histórico");
    }

    @Test
    void ferramentaAusenteOuComFalhaNaoQuebraNemGeraFindings() throws Exception {
        Path vazio = Files.createTempDirectory("semnada");
        Path project = Files.createTempDirectory("proj");

        GitleaksAnalyzer g = new GitleaksAnalyzer();
        g.enabled = true;
        g.timeoutSeconds = 5;
        g.pathEnv = vazio.toString();
        assertFalse(g.isAvailable());
        assertTrue(g.analyze(project, run()).findings().isEmpty());

        TrivyAnalyzer t = new TrivyAnalyzer();
        t.enabled = true;
        t.scanners = "vuln,misconfig";
        t.timeoutSeconds = 5;
        t.pathEnv = vazio.toString();
        assertFalse(t.isAvailable());
        assertTrue(t.analyze(project, run()).findings().isEmpty());

        fakeBin(vazio, "trivy", "exit 2");                         // falha (ex.: sem base de vulnerabilidades)
        t.pathEnv = vazio.toString();
        assertTrue(t.isAvailable());
        assertTrue(t.analyze(project, run()).findings().isEmpty());

        t.enabled = false;                                         // desligado por configuração
        assertFalse(t.isAvailable());
    }

    @Test
    void trivyExecutadoDePontaAPontaEComTimeout() throws Exception {
        Path bin = Files.createTempDirectory("fakebin");
        Path project = Files.createTempDirectory("proj");
        Path args = bin.resolve("args.txt");
        fakeBin(bin, "trivy", "echo \"$@\" > " + args + "\n"
                + "while [ $# -gt 0 ]; do if [ \"$1\" = \"--output\" ]; then R=\"$2\"; fi; shift; done\n"
                + "cat > \"$R\" <<'EOF'\n" + TRIVY_JSON + "\nEOF");

        TrivyAnalyzer t = new TrivyAnalyzer();
        t.enabled = true;
        t.scanners = "misconfig, vuln, invalido, vuln";
        t.timeoutSeconds = 30;
        t.pathEnv = bin.toString();

        assertEquals(3, t.analyze(project, run()).findings().size());
        String used = Files.readString(args);
        assertTrue(used.contains("--scanners misconfig,vuln"), used);       // limpa, sem repetição nem lixo
        assertTrue(used.contains("--skip-dirs node_modules,.git"));
        assertTrue(used.contains(project.toString()));

        Path lento = Files.createTempDirectory("fakeslow");
        fakeBin(lento, "trivy", "exec sleep 30");
        t.pathEnv = lento.toString();
        t.timeoutSeconds = 1;
        t.graceSeconds = 0;
        long inicio = System.nanoTime();
        assertTrue(t.analyze(project, run()).findings().isEmpty());
        assertTrue((System.nanoTime() - inicio) / 1_000_000_000L < 20, "deveria ter encerrado no timeout");
    }

    @Test
    void trivySoSubstituiOScaInternoQuandoOScannerVulnEstaLigado() {
        TrivyAnalyzer t = new TrivyAnalyzer();
        t.scanners = "vuln,misconfig";
        assertEquals(List.of(OWASPDependencyAnalyzer.class), t.replaces());
        t.scanners = "misconfig";
        assertTrue(t.replaces().isEmpty());
        assertEquals(List.of(SecretsAnalyzer.class, HistorySecretsAnalyzer.class), new GitleaksAnalyzer().replaces());
    }
}
