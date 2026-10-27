package com.sentinel.audit.service;

import com.sentinel.audit.model.LocalSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Origem "pasta local" com git de verdade: caminho preso à raiz, verificação da pasta e cópia que nunca toca a origem.
 */
class LocalSourceTest {

    @TempDir
    Path tempDir;

    private Path root;
    private Path work;
    private LocalSource source;

    @BeforeEach
    void setUp() throws Exception {
        root = Files.createDirectories(tempDir.resolve("projetos")).toRealPath();
        work = Files.createDirectories(tempDir.resolve("copias"));
        source = new LocalSource(root, 60);
    }

    /** Repositório com um commit (App.java) e, opcionalmente, mudanças sem commit. */
    private Path repo(String name) throws Exception {
        Path dir = Files.createDirectories(root.resolve(name));
        git(dir, "init", "-q", "-b", "main");
        Files.writeString(dir.resolve("App.java"), "class App { int v = 1; }\n");
        Files.writeString(dir.resolve(".gitignore"), "target/\n");
        git(dir, "add", ".");
        git(dir, "commit", "-q", "-m", "primeiro");
        return dir;
    }

    private static void git(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t", "-C", dir.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), "git " + String.join(" ", args) + ": " + out);
    }

    private static void sujar(Path dir) throws IOException {
        Files.writeString(dir.resolve("App.java"), "class App { int v = 2; }\n");   // alterado e não commitado
        Files.writeString(dir.resolve("Novo.java"), "class Novo {}\n");              // novo e não versionado
        Files.createDirectories(dir.resolve("target"));
        Files.writeString(dir.resolve("target/saida.class"), "lixo");               // ignorado pelo .gitignore
    }

    // ------------------------------------------------------------------ raiz

    @Test
    void semRaizConfiguradaORecursoFicaDesligado() {
        assertTrue(LocalSource.ofRoot(null, 10).isEmpty());
        assertTrue(LocalSource.ofRoot("   ", 10).isEmpty());
        assertTrue(LocalSource.ofRoot("relativa/pasta", 10).isEmpty());
        assertTrue(LocalSource.ofRoot(tempDir.resolve("nao-existe").toString(), 10).isEmpty());
        assertTrue(LocalSource.ofRoot(root.toString(), 10).isPresent());
    }

    // ------------------------------------------------------------------ caminho

    @Test
    void aceitaPastaDentroDaRaizEOCaminhoComoOServidorOEnxerga() throws Exception {
        Path dir = repo("api");
        assertEquals(dir.toRealPath(), source.resolve("api"));
        assertEquals(dir.toRealPath(), source.resolve("  api/ "));
        assertEquals(dir.toRealPath(), source.resolve(root + "/api"));
        assertEquals(dir.toRealPath(), source.resolve("./api"));
    }

    @Test
    void recusaCaminhosQueEscapamDaRaiz() throws Exception {
        repo("api");
        for (String bad : List.of("..", "../outra", "api/../../outra", "/etc", "/etc/passwd", ".", "", "   ", "api/..")) {
            assertThrows(IllegalArgumentException.class, () -> source.resolve(bad), "deveria recusar: '" + bad + "'");
        }
        assertThrows(IllegalArgumentException.class, () -> source.resolve(null));
        assertThrows(IllegalArgumentException.class, () -> source.resolve("api\0x"));
    }

    @Test
    void recusaLinkSimbolicoQueApontaParaForaDaRaiz() throws Exception {
        Path fora = Files.createDirectories(tempDir.resolve("fora"));
        Files.createSymbolicLink(root.resolve("atalho"), fora);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> source.resolve("atalho"));
        assertTrue(e.getMessage().contains("fora da raiz"));
    }

    @Test
    void recusaPastaInexistenteEArquivo() throws Exception {
        Files.writeString(root.resolve("arquivo.txt"), "x");
        assertThrows(IllegalArgumentException.class, () -> source.resolve("nao-existe"));
        assertThrows(IllegalArgumentException.class, () -> source.resolve("arquivo.txt"));
    }

    @Test
    void mensagemDeErroNaoRevelaOCaminhoDoServidor() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> source.resolve("nao-existe"));
        assertFalse(e.getMessage().contains(tempDir.toString()));
    }

    // ------------------------------------------------------------------ verificação

    @Test
    void informaBranchArquivosEAlteracoesNaoCommitadas() throws Exception {
        Path dir = repo("api");
        LocalSource.PathCheck limpo = source.inspect("api", null);
        assertTrue(limpo.ok());
        assertEquals("main", limpo.defaultBranch());
        assertEquals(2, limpo.fileCount());
        assertEquals(0, limpo.uncommittedFiles());

        sujar(dir);
        LocalSource.PathCheck sujo = source.inspect("api", "main");
        assertTrue(sujo.ok());
        assertEquals(Boolean.TRUE, sujo.branchFound());
        assertEquals(2, sujo.uncommittedFiles());   // App.java alterado + Novo.java (target/ é ignorado)
    }

    @Test
    void dizQuandoABranchNaoExiste() throws Exception {
        repo("api");
        assertEquals(Boolean.FALSE, source.inspect("api", "develop").branchFound());
    }

    @Test
    void recusaPastaSemGitESubpastaDeRepositorio() throws Exception {
        Files.createDirectories(root.resolve("solta"));
        Files.writeString(root.resolve("solta/a.txt"), "x");
        LocalSource.PathCheck semGit = source.inspect("solta", null);
        assertFalse(semGit.ok());
        assertTrue(semGit.message().contains("repositório Git"));

        Path dir = repo("api");
        Files.createDirectories(dir.resolve("modulo"));
        LocalSource.PathCheck sub = source.inspect("api/modulo", null);
        assertFalse(sub.ok());
        assertTrue(sub.message().contains("raiz do repositório"));
    }

    @Test
    void verificacaoComCaminhoInvalidoNaoLancaExcecao() {
        LocalSource.PathCheck r = source.inspect("../fora", null);
        assertFalse(r.ok());
        assertNotNull(r.message());
        assertFalse(source.inspect("api", "-oProxyCommand=x").ok());
    }

    // ------------------------------------------------------------------ cópia: último commit

    @Test
    void copiaDoCommitIgnoraAlteracoesNaoCommitadasEPreservaAOrigem() throws Exception {
        Path dir = repo("api");
        sujar(dir);

        Path copia = source.snapshot("api", null, LocalSnapshot.COMMIT, null, work);

        assertTrue(copia.startsWith(work));
        assertEquals("class App { int v = 1; }\n", Files.readString(copia.resolve("App.java")));
        assertFalse(Files.exists(copia.resolve("Novo.java")));
        assertTrue(Files.isDirectory(copia.resolve(".git")));
        // a origem continua exatamente como estava
        assertEquals("class App { int v = 2; }\n", Files.readString(dir.resolve("App.java")));
        assertTrue(Files.exists(dir.resolve("Novo.java")));
    }

    @Test
    void apagarACopiaNaoAlteraAOrigem() throws Exception {
        Path dir = repo("api");
        Path copia = source.snapshot("api", null, LocalSnapshot.COMMIT, null, work);
        LocalSource.deleteTree(copia);
        assertFalse(Files.exists(copia));
        assertTrue(Files.exists(dir.resolve("App.java")));
        assertTrue(Files.isDirectory(dir.resolve(".git")));
    }

    @Test
    void copiaDoCommitFuncionaComEspacoNoNomeDaPasta() throws Exception {
        repo("meu sistema");
        Path copia = source.snapshot("meu sistema", null, LocalSnapshot.COMMIT, null, work);
        assertTrue(Files.exists(copia.resolve("App.java")));
    }

    @Test
    void copiaDoCommitRespeitaABranchEIgnoraAOutra() throws Exception {
        Path dir = repo("api");
        git(dir, "checkout", "-q", "-b", "feature");
        Files.writeString(dir.resolve("Feature.java"), "class Feature {}\n");
        git(dir, "add", ".");
        git(dir, "commit", "-q", "-m", "feature");
        git(dir, "checkout", "-q", "main");

        Path daFeature = source.snapshot("api", "feature", LocalSnapshot.COMMIT, null, work);
        assertTrue(Files.exists(daFeature.resolve("Feature.java")));
        Path daMain = source.snapshot("api", "main", LocalSnapshot.COMMIT, null, work);
        assertFalse(Files.exists(daMain.resolve("Feature.java")));
    }

    @Test
    void naoCopiaDeBranchInexistente() throws Exception {
        repo("api");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> source.snapshot("api", "nao-existe", LocalSnapshot.COMMIT, null, work));
        assertTrue(e.getMessage().contains("não existe"));
        assertEquals(0, Files.list(work).count());   // nada ficou para trás
    }

    @Test
    void historicoCopiadoRespeitaAProfundidade() throws Exception {
        Path dir = repo("api");
        for (int i = 0; i < 3; i++) {
            Files.writeString(dir.resolve("v.txt"), "v" + i);
            git(dir, "add", ".");
            git(dir, "commit", "-q", "-m", "c" + i);
        }
        Path rasa = source.snapshot("api", null, LocalSnapshot.COMMIT, 1, work);
        Path funda = source.snapshot("api", null, LocalSnapshot.COMMIT, 50, work);
        assertEquals(1, commits(rasa));
        assertEquals(4, commits(funda));
    }

    private static long commits(Path repo) throws Exception {
        Process p = new ProcessBuilder("git", "-C", repo.toString(), "rev-list", "--count", "HEAD").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        p.waitFor(30, TimeUnit.SECONDS);
        return Long.parseLong(out);
    }

    // ------------------------------------------------------------------ cópia: pasta como está

    @Test
    void copiaDaPastaComoEstaInclueMudancasENaoVersionadosEIgnoraOsIgnorados() throws Exception {
        Path dir = repo("api");
        sujar(dir);

        Path copia = source.snapshot("api", null, LocalSnapshot.TREE, null, work);

        assertEquals("class App { int v = 2; }\n", Files.readString(copia.resolve("App.java")));
        assertTrue(Files.exists(copia.resolve("Novo.java")));
        assertFalse(Files.exists(copia.resolve("target")));
        assertFalse(Files.exists(copia.resolve(".git")));
    }

    @Test
    void copiaDaPastaComoEstaNaoSegueLinksSimbolicosNemFalhaComArquivoApagado() throws Exception {
        Path dir = repo("api");
        Path segredo = Files.writeString(tempDir.resolve("segredo.txt"), "senha");
        Files.createSymbolicLink(dir.resolve("atalho.txt"), segredo);
        Files.delete(dir.resolve(".gitignore"));          // versionado e apagado depois do commit

        Path copia = source.snapshot("api", null, LocalSnapshot.TREE, null, work);

        assertTrue(Files.exists(copia.resolve("App.java")));
        assertFalse(Files.exists(copia.resolve("atalho.txt")));
        assertFalse(Files.exists(copia.resolve(".gitignore")));
    }

    @Test
    void copiaDaPastaComoEstaNaoSegueDiretorioQueVirouLinkParaFora() throws Exception {
        Path dir = repo("api");
        Files.createDirectories(dir.resolve("lib"));
        Files.writeString(dir.resolve("lib/Util.java"), "class Util {}\n");
        git(dir, "add", ".");
        git(dir, "commit", "-q", "-m", "lib");
        Path fora = Files.createDirectories(tempDir.resolve("fora"));
        Files.writeString(fora.resolve("Util.java"), "class Roubado {}\n");
        // a pasta lib/ é trocada por um link para fora: lib/Util.java agora "existe" lá fora
        try (var walk = Files.walk(dir.resolve("lib"))) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
        Files.createSymbolicLink(dir.resolve("lib"), fora);

        Path copia = source.snapshot("api", null, LocalSnapshot.TREE, null, work);

        assertFalse(Files.exists(copia.resolve("lib/Util.java")), "não pode copiar conteúdo de fora da pasta");
    }

    // ------------------------------------------------------------------ SHA

    @Test
    void shaDaPontaDaBranchBateComOGit() throws Exception {
        Path dir = repo("api");
        Process p = new ProcessBuilder("git", "-C", dir.toString(), "rev-parse", "HEAD").redirectErrorStream(true).start();
        String esperado = new String(p.getInputStream().readAllBytes()).strip();
        p.waitFor(30, TimeUnit.SECONDS);

        assertEquals(esperado, source.headSha("api", "main"));
        assertEquals(esperado, source.headSha("api", null));
        assertNull(source.headSha("api", "nao-existe"));
        assertNull(source.headSha("../fora", "main"));
    }

    @Test
    void ofRootAceitaRaizValida() {
        Optional<LocalSource> s = LocalSource.ofRoot(root.toString(), 10);
        assertTrue(s.isPresent());
    }
}
