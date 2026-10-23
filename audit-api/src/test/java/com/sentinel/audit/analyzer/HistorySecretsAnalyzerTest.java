package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Usa um repositório git real, criado no diretório temporário. */
class HistorySecretsAnalyzerTest {

    @TempDir
    Path repo;

    private void git(String... args) throws IOException, InterruptedException {
        List<String> cmd = new java.util.ArrayList<>(List.of("git", "-C", repo.toString(),
                "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), String.join(" ", args));
    }

    private HistorySecretsAnalyzer analyzer() {
        HistorySecretsAnalyzer a = new HistorySecretsAnalyzer();
        a.timeoutSeconds = 60;
        return a;
    }

    @Test
    void acusaSegredoRemovidoMasPresenteNoHistorico() throws Exception {
        git("init", "-q");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/App.java"), "class App {\n  String password = \"SuperSecreta123\";\n}\n");
        git("add", "."); git("commit", "-q", "-m", "c1");
        Files.writeString(repo.resolve("src/App.java"), "class App {\n  String password = System.getenv(\"PW\");\n}\n");
        git("commit", "-qam", "c2");

        List<Finding> findings = analyzer().analyze(repo, new AuditRun()).findings();
        assertEquals(1, findings.size());
        Finding f = findings.get(0);
        assertEquals("src/App.java", f.getFilePath());
        assertEquals(2, f.getLineNumber());
        assertTrue(f.getTitle().contains("histórico"));
        assertTrue(f.getDescription().contains("commit"));
        assertFalse(f.getCodeSnippet().contains("SuperSecreta123"));   // valor nunca é exibido
        assertEquals("secrets-history-password-assignment", f.getRuleId());
    }

    @Test
    void naoDuplicaSegredoQueAindaEstaNaVersaoAtual() throws Exception {
        git("init", "-q");
        Files.writeString(repo.resolve("App.java"), "class A { String password = \"SuperSecreta123\"; }\n");
        git("add", "."); git("commit", "-q", "-m", "c1");
        Files.writeString(repo.resolve("App.java"), "// novo\nclass A { String password = \"SuperSecreta123\"; }\n");
        git("commit", "-qam", "c2");
        assertTrue(analyzer().analyze(repo, new AuditRun()).findings().isEmpty());   // o SecretsAnalyzer cobre este caso
    }

    @Test
    void ignoraPlaceholdersETestes() throws Exception {
        git("init", "-q");
        Files.createDirectories(repo.resolve("src/test"));
        Files.writeString(repo.resolve("src/test/T.java"), "String password = \"SuperSecreta123\";\n");
        Files.writeString(repo.resolve("B.java"), "String password = \"changeme-please\";\n");
        git("add", "."); git("commit", "-q", "-m", "c1");
        git("rm", "-q", "-r", "src", "B.java"); git("commit", "-q", "-m", "c2");
        assertTrue(analyzer().analyze(repo, new AuditRun()).findings().isEmpty());
    }

    @Test
    void cloneRasoOuDesligadoNaoVarreNada() throws Exception {
        git("init", "-q");
        Files.writeString(repo.resolve("A.java"), "String password = \"SuperSecreta123\";\n");
        git("add", "."); git("commit", "-q", "-m", "c1");
        Files.writeString(repo.resolve("A.java"), "x\n"); git("commit", "-qam", "c2");
        Path shallow = Files.createTempDirectory("shallow-").resolve("clone");
        new ProcessBuilder("git", "clone", "-q", "--depth", "1", repo.toUri().toString(), shallow.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
        assertTrue(analyzer().analyze(shallow, new AuditRun()).findings().isEmpty());
    }

    @Test
    void diretorioSemGitNaoQuebra() {
        assertTrue(analyzer().analyze(repo, new AuditRun()).findings().isEmpty());
    }
}
