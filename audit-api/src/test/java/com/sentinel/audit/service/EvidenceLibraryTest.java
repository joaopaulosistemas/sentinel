package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceLibraryTest {

    @TempDir
    Path root;

    EvidenceLibrary lib;

    @BeforeEach
    void setUp() {
        lib = new EvidenceLibrary();
        lib.evidenceDir = Optional.of(root.toString());
    }

    private static ByteArrayInputStream in(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void salvaListaERemoveNaPastaDoSistema() throws Exception {
        lib.save("Sistema de Câmbio", "https://x/y.git", "schema.sql", in("create table t(a int);"));
        assertTrue(Files.isRegularFile(root.resolve("sistema-de-cambio").resolve("schema.sql")));
        var files = lib.list("Sistema de Câmbio", "https://x/y.git");
        assertEquals(1, files.size());
        assertEquals("schema.sql", files.get(0).name());
        assertEquals(22, files.get(0).sizeBytes());
        assertTrue(lib.delete("Sistema de Câmbio", "https://x/y.git", "schema.sql"));
        assertTrue(lib.list("Sistema de Câmbio", "https://x/y.git").isEmpty());
    }

    @Test
    void recusaNomeComCaminhoOuOculto() {
        for (String bad : new String[] {"../x.sql", "a/b.sql", "..", ".oculto", "", "a\\b.sql"}) {
            assertThrows(IllegalArgumentException.class, () -> lib.save("s", null, bad, in("x")), bad);
        }
    }

    @Test
    void recusaArquivoMaiorQueOLimite() {
        lib.maxFileBytes = 10;
        assertThrows(IllegalArgumentException.class, () -> lib.save("s", null, "big.sql", in("0123456789abc")));
        assertFalse(Files.exists(root.resolve("s").resolve("big.sql")));
    }

    @Test
    void recusaQuandoJaHaArquivosDemais() throws Exception {
        lib.maxFiles = 2;
        lib.save("s", null, "a.sql", in("1"));
        lib.save("s", null, "b.sql", in("1"));
        assertThrows(IllegalArgumentException.class, () -> lib.save("s", null, "c.sql", in("1")));
        lib.save("s", null, "a.sql", in("2")); // substituir um existente é permitido
    }

    @Test
    void semPastaConfiguradaEstaDesligado() {
        lib.evidenceDir = Optional.empty();
        assertFalse(lib.enabled());
        assertThrows(IllegalStateException.class, () -> lib.save("s", null, "a.sql", in("1")));
        assertTrue(lib.list("s", null).isEmpty());
    }
}
