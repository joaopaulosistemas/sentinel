package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.AnalyzerResult;
import com.sentinel.audit.analyzer.ArchitectureAnalyzer;
import com.sentinel.audit.analyzer.SecretsAnalyzer;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressões dos analisadores de segredos e arquitetura (falsos positivos, caminhos e observabilidade).
 */
class AnalyzerRulesTest {

    @TempDir
    Path tempDir;

    private final SecretsAnalyzer secrets = new SecretsAnalyzer();
    private final ArchitectureAnalyzer architecture = new ArchitectureAnalyzer();

    // ------------------------------------------------------------ Secrets

    @Test
    void naoDeveAcusarPlaceholders() throws IOException {
        Files.writeString(tempDir.resolve("application.yml"),
                "password: \"${DB_PASSWORD}\"\n"
                        + "db.password = \"changeme\"\n"
                        + "api_key = \"<your-api-key-here>\"\n"
                        + "secret: \"{{ vault.secret }}\"\n");
        assertTrue(secrets.analyze(tempDir, new AuditRun()).findings().isEmpty());
    }

    @Test
    void deveDetectarChaveAwsEMascararOValor() throws IOException {
        Files.writeString(tempDir.resolve("Cloud.java"), "String k = \"AKIAABCDEFGHIJKLMNOP\";\n");
        Finding f = secrets.analyze(tempDir, new AuditRun()).findings().get(0);
        assertEquals("secrets-aws-access-key", f.getRuleId());
        assertFalse(f.getCodeSnippet().contains("AKIAABCDEFGHIJKLMNOP"), "o segredo não pode aparecer no finding");
    }

    @Test
    void naoDeveIgnorarProjetoCujoCaminhoTemUmaPastaChamadaTest() throws IOException {
        // antes, o filtro olhava o caminho absoluto e ignorava tudo sob qualquer pasta "test"/"target"
        Path root = tempDir.resolve("test/target/repo");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Db.java"), "String p = \"password=\\\"x\\\"\";\nString password = \"s3nh4-real\";\n");
        assertFalse(secrets.analyze(root, new AuditRun()).findings().isEmpty());
    }

    @Test
    void deveEscanearDotEnvMasNaoOExemplo() throws IOException {
        Files.writeString(tempDir.resolve(".env"), "DB_PASSWORD=\"prod-pass-123\"\n");
        Files.writeString(tempDir.resolve(".env.example"), "DB_PASSWORD=\"prod-pass-123\"\n");
        AnalyzerResult result = secrets.analyze(tempDir, new AuditRun());
        assertEquals(1, result.findings().size());
        assertEquals(".env", result.findings().get(0).getFilePath());
    }

    @Test
    void arquivoComEncodingLatin1NaoQuebraOScan() throws IOException {
        Files.write(tempDir.resolve("Legacy.java"),
                "// comentário ção\nString password = \"abc12345\";\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        assertEquals(1, secrets.analyze(tempDir, new AuditRun()).findings().size());
    }

    // ------------------------------------------------------------ Architecture

    @Test
    void codigoHttpNaoEhMagicNumber() throws IOException {
        Files.writeString(tempDir.resolve("Api.java"),
                "public class Api {\n    int a() { return 404; }\n    int b() { return 500; }\n"
                        + "    String c() { return \"codigo 98765\"; }\n}\n");
        assertTrue(architecture.analyze(tempDir, new AuditRun()).findings().isEmpty());
    }

    @Test
    void magicNumbersSaoAgregadosEmUmFindingPorArquivo() throws IOException {
        StringBuilder sb = new StringBuilder("public class Calc {\n");
        for (int i = 0; i < 12; i++) {
            sb.append("    int m").append(i).append("() { return 86400 + ").append(7000 + i).append("; }\n");
        }
        sb.append("}\n");
        Files.writeString(tempDir.resolve("Calc.java"), sb.toString());
        long magic = architecture.analyze(tempDir, new AuditRun()).findings().stream()
                .filter(f -> "arch-magic-number".equals(f.getRuleId())).count();
        assertEquals(1L, magic);
    }

    @Test
    void gettersESettersNaoContamNoGodObject() throws IOException {
        StringBuilder sb = new StringBuilder("public class Dto {\n");
        for (int i = 0; i < 30; i++) {
            sb.append("    private String f").append(i).append(";\n")
              .append("    public String getF").append(i).append("() { return f").append(i).append("; }\n")
              .append("    public void setF").append(i).append("(String v) { this.f").append(i).append(" = v; }\n");
        }
        sb.append("}\n");
        Files.writeString(tempDir.resolve("Dto.java"), sb.toString());
        assertTrue(architecture.analyze(tempDir, new AuditRun()).findings().stream()
                .noneMatch(f -> "arch-god-object".equals(f.getRuleId())));
    }

    @Test
    void deveDetectarControllerJaxRsAcessandoRepositorio() throws IOException {
        Files.writeString(tempDir.resolve("UserResource.java"),
                "import com.x.UserRepository;\n@Path(\"/users\")\npublic class UserResource {\n}\n");
        assertTrue(architecture.analyze(tempDir, new AuditRun()).findings().stream()
                .anyMatch(f -> "arch-controller-bypass-service".equals(f.getRuleId())));
    }

    @Test
    void deveGerarFindingsDeObservabilidade() throws IOException {
        Files.writeString(tempDir.resolve("Job.java"),
                "public class Job {\n    void run() {\n        System.out.println(\"x\");\n"
                        + "        try { } catch (Exception e) { e.printStackTrace(); }\n    }\n}\n");
        var findings = architecture.analyze(tempDir, new AuditRun()).findings();
        assertTrue(findings.stream().anyMatch(f -> "obs-system-out".equals(f.getRuleId())
                && f.getCategory() == Category.OBSERVABILITY && f.getLineNumber() == 3));
        assertTrue(findings.stream().anyMatch(f -> "obs-print-stack-trace".equals(f.getRuleId())));
    }

    @Test
    void naoDeveIgnorarProjetoSobPastaChamadaTest() throws IOException {
        Path root = tempDir.resolve("test/proj");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Huge.java"), "public class Huge {\n" + "// x\n".repeat(520) + "}\n");
        assertTrue(architecture.analyze(root, new AuditRun()).findings().stream()
                .anyMatch(f -> "arch-god-object".equals(f.getRuleId())));
    }

    @Test
    void caminhosNosFindingsSaoRelativosAoProjeto() throws IOException {
        Path src = tempDir.resolve("src/main");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Job.java"), "public class Job { void r() { System.out.println(1); } }\n");
        String path = architecture.analyze(tempDir, new AuditRun()).findings().get(0).getFilePath();
        assertEquals(Path.of("src/main/Job.java").toString(), path);
    }
}
