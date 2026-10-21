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
        service.gitToken = Optional.empty();
        service.cloneBaseDir = tempDir.toString();
        service.allowedHosts = Optional.empty();
        service.tokenHosts = List.of("github.com");
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
    void deveRespeitarListaDeHostsPermitidos() {
        service.allowedHosts = Optional.of(List.of("git.sicoob.com.br", "github.com"));
        assertNotNull(service.validateRepoUrl("https://GitHub.com/org/repo.git"));
        assertThrows(IllegalArgumentException.class, () -> service.validateRepoUrl("https://evil.example.org/x.git"));
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
        service.gitToken = Optional.of("ghp_SEGREDO_MUITO_SECRETO");
        List<String> cmd = service.buildCloneCommand("https://github.com/org/repo.git", null, tempDir.resolve("x"));
        assertTrue(cmd.stream().noneMatch(a -> a.contains("SEGREDO")));
    }

    @Test
    void cloneInvalidoFalhaSemDeixarLixoNemVazarToken() throws IOException {
        service.gitToken = Optional.of("ghp_SEGREDO_MUITO_SECRETO");
        service.tokenHosts = List.of("127.0.0.1");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.cloneRepository("https://127.0.0.1:9/org/repo.git", "main", null));
        assertFalse(error.getMessage().contains("SEGREDO"), "mensagem de erro não pode conter o token");
        try (Stream<Path> left = Files.list(tempDir)) {
            assertEquals(0L, left.count(), "clone e log temporários devem ser removidos");
        }
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
}
