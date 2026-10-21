package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.AnalyzerResult;
import com.sentinel.audit.analyzer.SecretsAnalyzer;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para {@link SecretsAnalyzer}.
 */
class SecretsAnalyzerTest {

    @TempDir
    Path tempDir;

    private SecretsAnalyzer analyzer;
    private AuditRun auditRun;

    @BeforeEach
    void setUp() {
        analyzer = new SecretsAnalyzer();
        auditRun = buildAuditRun();
    }

    @Test
    void deveDetectarPasswordHardcoded() throws IOException {
        Path file = tempDir.resolve("config.properties");
        Files.writeString(file, "datasource.password=\"minha-senha-secreta\"\n");

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        assertFalse(result.findings().isEmpty(), "Deve encontrar pelo menos um finding de senha");
        Finding f = result.findings().get(0);
        assertEquals(Category.SECURITY, f.getCategory());
        assertEquals(Severity.CRITICAL, f.getSeverity());
    }

    @Test
    void deveDetectarApiKeyHardcoded() throws IOException {
        Path file = tempDir.resolve("application.yml");
        Files.writeString(file, "api_key: \"sk-abc12345678901234\"\n");

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        assertTrue(result.findings().stream()
                .anyMatch(f -> f.getRuleId().contains("api-key")),
                "Deve encontrar finding de api_key");
    }

    @Test
    void deveDetectarPrivateKeyHeader() throws IOException {
        Path file = tempDir.resolve("secret.pem");
        Files.writeString(file, "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAK...\n-----END RSA PRIVATE KEY-----\n");

        // .pem não está na lista de extensões escaneadas; usar .properties para testar o padrão
        Path javaFile = tempDir.resolve("KeyLoader.java");
        Files.writeString(javaFile, "String key = \"-----BEGIN RSA PRIVATE KEY-----\";\n");

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        assertTrue(result.findings().stream()
                .anyMatch(f -> f.getRuleId().contains("private-key")),
                "Deve encontrar finding de chave privada");
    }

    @Test
    void naoDeveEncontrarFindingsEmCodigoLimpo() throws IOException {
        Path file = tempDir.resolve("Service.java");
        Files.writeString(file,
                "public class Service {\n" +
                "    @ConfigProperty(name=\"datasource.password\")\n" +
                "    String password;\n" +
                "}\n");

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        assertTrue(result.findings().isEmpty(),
                "Não deve encontrar findings em código que usa injeção de configuração");
    }

    @Test
    void deveIgnorarDiretorioNodeModules() throws IOException {
        Path nodeModules = tempDir.resolve("node_modules");
        Files.createDirectories(nodeModules);
        Path file = nodeModules.resolve("config.js");
        Files.writeString(file, "password = \"super-secret-123\"\n");

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        assertTrue(result.findings().isEmpty(),
                "Não deve escanear arquivos dentro de node_modules");
    }

    private AuditRun buildAuditRun() {
        AuditedSystem system = new AuditedSystem();
        system.setStack(Stack.JAVA);

        AuditRun run = new AuditRun();
        run.setSystem(system);
        return run;
    }
}
