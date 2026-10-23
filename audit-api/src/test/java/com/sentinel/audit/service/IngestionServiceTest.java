package com.sentinel.audit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de segurança do clone: URL, branch, linha de comando e limpeza em caso de erro.
 */
class IngestionServiceTest {

    @TempDir
    Path tempDir;

    private IngestionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionService();
        service.cloneBaseDir = tempDir.toString();
        service.cloneTimeoutSeconds = 60;
    }

    @Test
    void deveAceitarUrlHttpsValida() {
        assertEquals("github.com", service.validateRepoUrl("https://github.com/org/repo.git").getHost());
    }

    @Test
    void deveRejeitarEsquemasPerigososEUrlsMalFormadas() {
        for (String url : List.of(
                "file:///etc/passwd",
                "http://github.com/org/repo.git",
                "ssh://git@github.com/org/repo.git",
                "ext::sh -c 'touch /tmp/pwned'",
                "-oProxyCommand=touch /tmp/pwned",
                "git@github.com:org/repo.git",
                "https://",
                "",
                "   ")) {
            assertThrows(IllegalArgumentException.class, () -> service.validateRepoUrl(url));
        }
        assertThrows(IllegalArgumentException.class, () -> service.validateRepoUrl(null));
    }

    @Test
    void deveRejeitarCredenciaisNaUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateRepoUrl("https://usuario:senha@github.com/org/repo.git"));
    }

    @Test
    void deveValidarNomeDaBranch() {
        service.validateBranch("main");
        service.validateBranch("feature/nova-tela_1.2");
        service.validateBranch(null);
        service.validateBranch("");
        for (String branch : List.of("-x", "--upload-pack=touch /tmp/x", "a..b", "a b", "a;rm -rf /", "$(id)")) {
            assertThrows(IllegalArgumentException.class, () -> service.validateBranch(branch));
        }
    }

    @Test
    void comandoDeCloneNaoPermiteInjecaoDeOpcoesNemSymlinks() {
        List<String> cmd = service.buildCloneCommand("https://github.com/org/repo.git", "main", tempDir.resolve("x"));
        int separator = cmd.indexOf("--");
        assertTrue(separator > 0, "deve haver '--' antes da URL");
        assertEquals("https://github.com/org/repo.git", cmd.get(separator + 1));
        assertTrue(cmd.contains("protocol.allow=never"));
        assertTrue(cmd.contains("protocol.https.allow=always"));
        assertTrue(cmd.contains("core.symlinks=false"));
        assertTrue(cmd.contains("--depth"));
        assertEquals("main", cmd.get(cmd.indexOf("--branch") + 1));
    }

    @Test
    void tokenNuncaApareceNaLinhaDeComando() {
        List<String> cmd = service.buildCloneCommand("https://github.com/org/repo.git", null, tempDir.resolve("x"));
        assertTrue(cmd.stream().noneMatch(a -> a.contains("SEGREDO")));
        assertEquals("100", cmd.get(cmd.indexOf("--depth") + 1), "histórico padrão");
        assertEquals("1", service.buildCloneCommand("https://github.com/o/r.git", null, tempDir.resolve("x"), 0).get(
                service.buildCloneCommand("https://github.com/o/r.git", null, tempDir.resolve("x"), 0).indexOf("--depth") + 1));
    }

    @Test
    void cloneInvalidoFalhaSemDeixarLixoNemVazarToken() throws IOException {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.cloneRepository("https://127.0.0.1:9/org/repo.git", "main", "ghp_SEGREDO_MUITO_SECRETO", "127.0.0.1"));
        assertFalse(error.getMessage().contains("SEGREDO"), "mensagem de erro não pode conter o token");
        try (Stream<Path> left = Files.list(tempDir)) {
            assertEquals(0L, left.count(), "clone e log temporários devem ser removidos");
        }
    }

    @Test
    void tokenDoSistemaComSeusHostsTambemNaoVazaNoErro() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.cloneRepository("https://127.0.0.1:9/org/repo.git", "main", "ghp_DO_SISTEMA", "git.interno, 127.0.0.1"));
        assertFalse(error.getMessage().contains("DO_SISTEMA"));
    }

    @Test
    void urlInvalidaNaoChegaAExecutarGit() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cloneRepository("file:///etc", "main", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.cloneRepository("https://github.com/o/r.git", "--upload-pack=x", null));
    }

    @Test
    void cleanupRemoveArvoreInteiraSemSeguirSymlink() throws IOException {
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("keep.txt"), "x");
        Path clone = Files.createDirectories(tempDir.resolve("clone/sub"));
        Files.writeString(clone.resolve("a.txt"), "x");
        try {
            Files.createSymbolicLink(tempDir.resolve("clone/link"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            // sistema sem suporte a symlink: segue sem ele
        }
        service.cleanupDirectory(tempDir.resolve("clone"));
        assertFalse(Files.exists(tempDir.resolve("clone")));
        assertTrue(Files.exists(outside.resolve("keep.txt")), "o alvo do symlink não pode ser apagado");
    }

    @Test
    void extraiAbranchPadraoDaSaidaDoLsRemote() {
        String out = "ref: refs/heads/develop\tHEAD\nabc123\tHEAD\nabc123\trefs/heads/develop\n";
        assertEquals("develop", IngestionService.parseDefaultBranch(out));
        assertNull(IngestionService.parseDefaultBranch("abc123\tHEAD\n"));
        assertNull(IngestionService.parseDefaultBranch(null));
    }

    @Test
    void brancheListadaPrecisaSerExata() {
        String out = "abc\trefs/heads/main2\n";
        assertFalse(IngestionService.branchListed(out, "main"));
        assertTrue(IngestionService.branchListed(out, "main2"));
    }

    @Test
    void traduzOsErrosMaisComunsDoGit() {
        assertTrue(IngestionService.friendlyGitError("fatal: repository 'x' not found").contains("não encontrado"));
        assertTrue(IngestionService.friendlyGitError("fatal: Authentication failed for 'x'").contains("permissão"));
        assertTrue(IngestionService.friendlyGitError("fatal: unable to access: Could not resolve host").contains("alcançar"));
        assertEquals("Não foi possível acessar o repositório", IngestionService.friendlyGitError("???"));
    }

    @Test
    void verificacaoRejeitaUrlInsegura() {
        IngestionService.RepoCheck c = service.checkRepository("http://inseguro/repo", null);
        assertFalse(c.reachable());
        assertTrue(c.message().contains("https"));
        assertFalse(service.checkRepository("https://github.com/org/repo.git", "--upload-pack=x").reachable());
    }
}
