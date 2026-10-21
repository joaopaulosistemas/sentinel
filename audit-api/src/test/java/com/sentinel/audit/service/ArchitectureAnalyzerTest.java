package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.AnalyzerResult;
import com.sentinel.audit.analyzer.ArchitectureAnalyzer;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Stack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para {@link ArchitectureAnalyzer}.
 */
class ArchitectureAnalyzerTest {

    @TempDir
    Path tempDir;

    private ArchitectureAnalyzer analyzer;
    private AuditRun auditRun;

    @BeforeEach
    void setUp() {
        analyzer = new ArchitectureAnalyzer();
        auditRun = buildAuditRun();
    }

    @Test
    void deveDetectarGodObjectPorNumeroDeLinhas() throws IOException {
        // Cria um arquivo Java com mais de 500 linhas
        StringBuilder sb = new StringBuilder("public class HugeClass {\n");
        for (int i = 0; i < 510; i++) {
            sb.append("    // linha ").append(i).append("\n");
        }
        sb.append("}\n");

        Path javaFile = tempDir.resolve("HugeClass.java");
        Files.writeString(javaFile, sb.toString());

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        boolean hasGodObjectFinding = result.findings().stream()
                .anyMatch(f -> f.getRuleId().equals("arch-god-object"));

        assertTrue(hasGodObjectFinding, "Deve detectar God Object em classe com mais de 500 linhas");
    }

    @Test
    void deveDetectarControllerAcessandoRepositoriodiretamente() throws IOException {
        String code =
                "import org.springframework.web.bind.annotation.RestController;\n" +
                "import com.example.UserRepository;\n" +
                "\n" +
                "@RestController\n" +
                "public class UserController {\n" +
                "    private final UserRepository userRepository;\n" +
                "}\n";

        Path javaFile = tempDir.resolve("UserController.java");
        Files.writeString(javaFile, code);

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        boolean hasBypassFinding = result.findings().stream()
                .anyMatch(f -> f.getRuleId().equals("arch-controller-bypass-service"));

        assertTrue(hasBypassFinding, "Deve detectar controller que acessa repositório diretamente");
    }

    @Test
    void naoDeveDetectarControllerSemRepositorio() throws IOException {
        String code =
                "import org.springframework.web.bind.annotation.RestController;\n" +
                "import com.example.UserService;\n" +
                "\n" +
                "@RestController\n" +
                "public class UserController {\n" +
                "    private final UserService userService;\n" +
                "}\n";

        Path javaFile = tempDir.resolve("UserController.java");
        Files.writeString(javaFile, code);

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        boolean hasBypassFinding = result.findings().stream()
                .anyMatch(f -> f.getRuleId().equals("arch-controller-bypass-service"));

        assertFalse(hasBypassFinding, "Não deve alertar quando controller usa apenas service");
    }

    @Test
    void deveDetectarMagicNumber() throws IOException {
        String code =
                "public class TimeoutConfig {\n" +
                "    public int getTimeout() {\n" +
                "        return 86400; // segundos em um dia\n" +
                "    }\n" +
                "}\n";

        Path javaFile = tempDir.resolve("TimeoutConfig.java");
        Files.writeString(javaFile, code);

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        boolean hasMagicNumber = result.findings().stream()
                .anyMatch(f -> f.getRuleId().equals("arch-magic-number"));

        assertTrue(hasMagicNumber, "Deve detectar magic number maior que 100");
    }

    @Test
    void naoDeveAlertar_paraClasseNormal() throws IOException {
        String code =
                "public class UserService {\n" +
                "    public String getUser(Long id) {\n" +
                "        return \"user\";\n" +
                "    }\n" +
                "}\n";

        Path javaFile = tempDir.resolve("UserService.java");
        Files.writeString(javaFile, code);

        AnalyzerResult result = analyzer.analyze(tempDir, auditRun);

        assertTrue(result.findings().isEmpty(), "Classe simples não deve gerar findings arquiteturais");
    }

    private AuditRun buildAuditRun() {
        AuditedSystem system = new AuditedSystem();
        system.setStack(Stack.JAVA);

        AuditRun run = new AuditRun();
        run.setSystem(system);
        return run;
    }
}
