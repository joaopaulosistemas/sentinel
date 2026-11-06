package com.sentinel.audit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cópia paralela da árvore de trabalho: sem git, só arquivos e pastas de verdade. */
class WorkingTreeCopierTest {

    @TempDir
    Path tempDir;

    private Path src;
    private Path target;

    @BeforeEach
    void setUp() throws IOException {
        src = Files.createDirectories(tempDir.resolve("origem")).toRealPath();
        target = Files.createDirectories(tempDir.resolve("destino")).toRealPath();
    }

    private void copy(List<String> files, LocalSource.CopyProgress progress) throws Exception {
        new WorkingTreeCopier(src, target).copyAll(files, 30, progress);
    }

    @Test
    void copiaArquivosEmSubpastasEAvisaOAndamento() throws Exception {
        List<String> files = new ArrayList<>();
        for (int i = 0; i < WorkingTreeCopier.PROGRESS_EVERY * 2 + 5; i++) {
            Path dir = Files.createDirectories(src.resolve("m" + (i % 7)));
            Files.writeString(dir.resolve("F" + i + ".txt"), "conteudo " + i);
            files.add("m" + (i % 7) + "/F" + i + ".txt");
        }
        List<long[]> avisos = new ArrayList<>();

        copy(files, (done, total) -> {
            synchronized (avisos) {
                avisos.add(new long[] {done, total});
            }
        });

        for (int i = 0; i < files.size(); i++) {
            assertEquals("conteudo " + i, Files.readString(target.resolve("m" + (i % 7)).resolve("F" + i + ".txt")));
        }
        assertEquals(2, avisos.size(), "um aviso a cada PROGRESS_EVERY arquivos");
        assertTrue(avisos.stream().allMatch(a -> a[1] == files.size()));
    }

    @Test
    void ignoraCaminhoQueEscapaDaPastaELinkSimbolico() throws Exception {
        Files.writeString(src.resolve("ok.txt"), "ok");
        Path fora = Files.writeString(tempDir.resolve("fora.txt"), "segredo");
        Files.createSymbolicLink(src.resolve("atalho.txt"), fora);

        copy(List.of("ok.txt", "../fora.txt", "atalho.txt", "/etc/passwd", "inexistente.txt"), LocalSource.CopyProgress.NONE);

        assertTrue(Files.exists(target.resolve("ok.txt")));
        assertFalse(Files.exists(target.resolve("atalho.txt")));
        assertFalse(Files.exists(target.resolve("fora.txt")));
        assertFalse(Files.exists(target.resolve("inexistente.txt")));
    }

    @Test
    void falhaDeGravacaoSobeComoIOExceptionComACausaPreservada() throws Exception {
        Files.createDirectories(src.resolve("pasta"));
        Files.writeString(src.resolve("pasta/a.txt"), "a");
        Files.writeString(target.resolve("pasta"), "arquivo no lugar da pasta");   // createDirectories vai falhar

        IOException e = assertThrows(IOException.class, () -> copy(List.of("pasta/a.txt"), LocalSource.CopyProgress.NONE));

        assertInstanceOf(java.util.concurrent.ExecutionException.class, e.getCause());
    }

    @Test
    void semArquivosNaoFazNada() throws Exception {
        copy(List.of(), LocalSource.CopyProgress.NONE);

        try (var children = Files.list(target)) {
            assertEquals(0, children.count());
        }
    }
}
