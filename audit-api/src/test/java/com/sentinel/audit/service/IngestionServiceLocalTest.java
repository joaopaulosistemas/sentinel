package com.sentinel.audit.service;

import com.sentinel.audit.model.LocalSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A origem "pasta local" vista pelo IngestionService: desligada por padrão, presa à raiz e sem tocar a pasta de origem.
 */
class IngestionServiceLocalTest {

    @TempDir
    Path tempDir;

    private Path root;
    private IngestionService service;

    @BeforeEach
    void setUp() throws Exception {
        root = Files.createDirectories(tempDir.resolve("projetos")).toRealPath();
        service = new IngestionService();
        service.cloneBaseDir = tempDir.resolve("clones").toString();
        service.cloneTimeoutSeconds = 60;
        service.localRoot = Optional.of(root.toString());
    }

    private Path repo(String name) throws Exception {
        Path dir = Files.createDirectories(root.resolve(name));
        git(dir, "init", "-q", "-b", "main");
        Files.writeString(dir.resolve("App.java"), "class App {}\n");
        git(dir, "add", ".");
        git(dir, "commit", "-q", "-m", "primeiro");
        return dir;
    }

    private static String git(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t", "-C", dir.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), "git " + String.join(" ", args) + ": " + out);
        return out;
    }

    @Test
    void semRaizConfiguradaAOrigemLocalFicaDesligada() throws Exception {
        repo("api");
        service.localRoot = Optional.empty();

        assertFalse(service.localSourceEnabled());
        LocalSource.PathCheck check = service.checkLocalPath("api", null);
        assertFalse(check.ok());
        assertTrue(check.message().contains("SENTINEL_LOCAL_ROOT"));
        assertThrows(IllegalStateException.class, () -> service.snapshotLocal("api", null, LocalSnapshot.COMMIT, null));
        assertNull(service.localHeadSha("api", null));
    }

    @Test
    void validaENormalizaOCaminhoParaGuardarNoCadastro() throws Exception {
        repo("api");
        assertTrue(service.localSourceEnabled());
        assertEquals("api", service.validateLocalPath("  api\\ ", null));
        assertThrows(IllegalArgumentException.class, () -> service.validateLocalPath("../fora", null));
        assertThrows(IllegalArgumentException.class, () -> service.validateLocalPath("nao-existe", null));
        Files.createDirectories(root.resolve("sem-git"));
        assertThrows(IllegalArgumentException.class, () -> service.validateLocalPath("sem-git", null));
    }

    @Test
    void copiaFicaNaPastaDeClonesEApagarACopiaNaoTocaAOrigem() throws Exception {
        Path dir = repo("api");

        Path copia = service.snapshotLocal("api", null, LocalSnapshot.COMMIT, null);

        assertTrue(copia.startsWith(Path.of(service.cloneBaseDir).toAbsolutePath().normalize()));
        assertTrue(Files.exists(copia.resolve("App.java")));
        service.cleanupDirectory(copia);
        assertFalse(Files.exists(copia));
        assertTrue(Files.exists(dir.resolve("App.java")));
        assertTrue(Files.isDirectory(dir.resolve(".git")));
    }

    @Test
    void shaDaPontaBateComOGit() throws Exception {
        Path dir = repo("api");
        assertEquals(git(dir, "rev-parse", "HEAD"), service.localHeadSha("api", "main"));
    }

    @Test
    void auditoriaDePrEmPastaLocalVeApenasOsArquivosAlteradosNaBranch() throws Exception {
        Path dir = repo("api");
        git(dir, "checkout", "-q", "-b", "feature");
        Files.writeString(dir.resolve("Novo.java"), "class Novo {}\n");
        git(dir, "add", ".");
        git(dir, "commit", "-q", "-m", "novo");

        Path copia = service.snapshotLocal("api", "feature", LocalSnapshot.COMMIT, null);
        ChangedFiles.Result changed = service.changedFilesLocal("api", copia, "main");

        assertNotNull(changed.files());
        assertTrue(changed.files().contains("Novo.java"));
        assertFalse(changed.files().contains("App.java"));
    }

    @Test
    void auditoriaDePrNaoQuebraQuandoAPastaSaiuDoAr() throws Exception {
        Path dir = repo("api");
        Path copia = service.snapshotLocal("api", null, LocalSnapshot.COMMIT, null);
        service.localRoot = Optional.empty();
        assertNull(service.changedFilesLocal("api", copia, "main").files());
    }
}
