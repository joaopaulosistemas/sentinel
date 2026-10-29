package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.structure.CommitMessages;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Mensagens de commit: a regra de cada assunto e o analisador sobre um histórico de Git de verdade. */
class CommitMessageAnalyzerTest {

    @Test
    void assuntoBomPassaEMauApontaOMotivo() {
        assertNull(CommitMessages.problem("feat: tela Guia de código"));
        assertNull(CommitMessages.problem("fix(api)!: corrige o filtro de severidade"));
        assertNull(CommitMessages.problem("feat: " + "a".repeat(80) + " (#12)"));
        assertNull(CommitMessages.problem("Merge branch 'main' into feat/x"));
        assertNull(CommitMessages.problem("Revert \"feat: algo\""));
        assertTrue(CommitMessages.problem("corrige o filtro").contains("fora do padrão"));
        assertTrue(CommitMessages.problem("wip").contains("fora do padrão"));
        assertTrue(CommitMessages.problem("fix: wip").contains("sem informação"));
        assertTrue(CommitMessages.problem("feat: " + "a".repeat(100)).contains("100"));
        assertNotNull(CommitMessages.problem(" "));
    }

    @Test
    void relatorioContaOsAvaliadosEIgnoraMerge() {
        String log = "aaaaaaa1\u001ffeat: ok\nbbbbbbb2\u001fajustes\nccccccc3\u001fMerge branch 'x'\nsem-separador\nddddddd4\u001ffix: ok";
        CommitMessages.Report r = CommitMessages.check(CommitMessages.parse(log), 5);

        assertEquals(3, r.checked());
        assertEquals(1, r.bad());
        assertEquals(33, r.percent());
        assertEquals(1, r.examples().size());
        assertTrue(r.examples().get(0).startsWith("bbbbbbb: ajustes"));
    }

    @Test
    void historicoQuaseTodoForaDoPadraoViraUmAchadoSo(@TempDir Path root) throws Exception {
        Assumptions.assumeTrue(git(root, "init", "-q"), "git indisponível");
        git(root, "config", "user.email", "t@t");
        git(root, "config", "user.name", "t");
        for (int i = 0; i < 24; i++) {
            Files.writeString(root.resolve("a.txt"), "v" + i);
            git(root, "add", "-A");
            git(root, "commit", "-q", "-m", i % 4 == 0 ? "feat: mudança " + i : "ajustes " + i);
        }

        List<Finding> fs = new CommitMessageAnalyzer().analyze(root, new AuditRun()).findings();

        assertEquals(1, fs.size(), fs.toString());
        assertEquals("sentinel-commit-fora-do-padrao", fs.get(0).getRuleId());
        assertEquals(Severity.LOW, fs.get(0).getSeverity());
        assertTrue(fs.get(0).getTitle().startsWith("75%"));
    }

    @Test
    void historicoNoPadraoOuCurtoNaoApontaNada(@TempDir Path root) throws Exception {
        Assumptions.assumeTrue(git(root, "init", "-q"), "git indisponível");
        git(root, "config", "user.email", "t@t");
        git(root, "config", "user.name", "t");
        for (int i = 0; i < 24; i++) {
            Files.writeString(root.resolve("a.txt"), "v" + i);
            git(root, "add", "-A");
            git(root, "commit", "-q", "-m", "fix: mudança " + i);
        }
        assertTrue(new CommitMessageAnalyzer().analyze(root, new AuditRun()).findings().isEmpty());

        Path semGit = Files.createTempDirectory("sentinel-sem-git-");
        assertTrue(new CommitMessageAnalyzer().analyze(semGit, new AuditRun()).findings().isEmpty());
        assertTrue(new CommitMessageAnalyzer().supports(Stack.JAVA));
    }

    private static boolean git(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", dir.toString()));
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
