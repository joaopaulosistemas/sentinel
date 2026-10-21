package com.sentinel.audit.service;

import com.sentinel.audit.model.Finding;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Usa o git de verdade em repositórios temporários (origem local + clone), sem rede. */
class ChangedFilesTest {

    private static void git(Path dir, String... args) throws Exception {
        String[] cmd = new String[args.length + 5];
        cmd[0] = "git";
        cmd[1] = "-c"; cmd[2] = "user.name=t"; cmd[3] = "-c"; cmd[4] = "user.email=t@t";
        System.arraycopy(args, 0, cmd, 5, args.length);
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, p.waitFor(), String.join(" ", args));
    }

    private static void write(Path dir, String rel, String content) throws IOException {
        Files.createDirectories(dir.resolve(rel).getParent());
        Files.writeString(dir.resolve(rel), content);
    }

    /** origem com main (a, b) e feature (muda b, cria c/d, remove a); devolve o clone raso da feature. */
    private static Path cloneOfFeature(boolean baseMovedOn) throws Exception {
        Path origin = Files.createTempDirectory("origin").toRealPath();
        git(origin, "init", "-q", "-b", "main");
        write(origin, "a.txt", "a");
        write(origin, "src/b.java", "b");
        git(origin, "add", "-A");
        git(origin, "commit", "-qm", "base");
        git(origin, "checkout", "-q", "-b", "feature");
        write(origin, "src/b.java", "b2");
        write(origin, "c/d.ts", "d");
        Files.delete(origin.resolve("a.txt"));
        git(origin, "add", "-A");
        git(origin, "commit", "-qm", "feature");
        if (baseMovedOn) {
            git(origin, "checkout", "-q", "main");
            write(origin, "so-na-main.txt", "x");
            git(origin, "add", "-A");
            git(origin, "commit", "-qm", "main andou");
        }
        Path work = Files.createTempDirectory("work").toRealPath();
        Path clone = work.resolve("clone");
        git(work, "clone", "-q", "--depth", "1", "--single-branch", "--branch", "feature", "file://" + origin, "clone");
        return clone;
    }

    @Test
    void listaSoOQueMudouNaFeatureIgnorandoRemovidos() throws Exception {
        Path clone = cloneOfFeature(false);

        ChangedFiles.Result r = ChangedFiles.compute(clone, "main", List.of(), Map.of(), 60);

        assertEquals(Set.of("src/b.java", "c/d.ts"), r.files(), "a.txt foi removido; não há o que auditar");
        assertFalse(r.approximate());
    }

    @Test
    void mudancasSoDoBranchBaseNaoEntram() throws Exception {
        Path clone = cloneOfFeature(true);

        ChangedFiles.Result r = ChangedFiles.compute(clone, "main", List.of(), Map.of(), 60);

        assertNotNull(r.files(), r.note());
        assertFalse(r.files().contains("so-na-main.txt"), "arquivo que só a main alterou não é do PR: " + r.files());
        assertTrue(r.files().contains("src/b.java"));
    }

    @Test
    void referenciasInvalidasOuInexistentesNaoLancam() throws Exception {
        Path clone = cloneOfFeature(false);

        for (String bad : new String[]{"--upload-pack=x", "a..b", "-x", "", "a b", "x;rm -rf /", "main/", "x.lock"}) {
            assertNull(ChangedFiles.compute(clone, bad, List.of(), Map.of(), 10).files(), "'" + bad + "' deve ser recusada");
        }
        ChangedFiles.Result missing = ChangedFiles.compute(clone, "nao-existe", List.of(), Map.of(), 30);
        assertNull(missing.files());
        assertNotNull(missing.note());
        assertNull(ChangedFiles.compute(clone, null, List.of(), Map.of(), 10).files());
    }

    @Test
    void filtraAchadosPorArquivoAlterado() {
        Finding a = new Finding();
        a.setFilePath("src/b.java");
        Finding b = new Finding();
        b.setFilePath("./c/d.ts");
        Finding c = new Finding();
        c.setFilePath("outro.java");
        Finding d = new Finding();
        d.setFilePath(null);
        Finding e = new Finding();
        e.setFilePath("(dependências)");

        List<Finding> kept = ChangedFiles.filter(List.of(a, b, c, d, e), Set.of("src/b.java", "c/d.ts"));

        assertEquals(List.of(a, b), kept);
    }
}
