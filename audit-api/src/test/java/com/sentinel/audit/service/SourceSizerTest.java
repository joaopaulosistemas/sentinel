package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSizerTest {

    @TempDir
    Path dir;

    @Test
    void contaSoLinhasNaoVaziasDasTecnologiasAuditadas() throws Exception {
        Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(dir.resolve("src/main/java/A.java"), "class A {\n\n  int x;\n}\n");
        Files.writeString(dir.resolve("src/app.ts"), "export const a = 1;\n");
        Files.writeString(dir.resolve("README.md"), "# nao conta\n");

        assertEquals(4, SourceSizer.countLines(dir));
    }

    @Test
    void ignoraTestesDependenciasEArquivosMinificados() throws Exception {
        Files.createDirectories(dir.resolve("node_modules/x"));
        Files.createDirectories(dir.resolve("src/test/java"));
        Files.writeString(dir.resolve("node_modules/x/i.js"), "a\nb\nc\n");
        Files.writeString(dir.resolve("src/test/java/T.java"), "class T {}\n");
        Files.writeString(dir.resolve("bundle.min.js"), "var a=1;\n");
        Files.writeString(dir.resolve("a.spec.ts"), "it()\n");
        Files.writeString(dir.resolve("ok.css"), "a { color: red; }\n");

        assertEquals(1, SourceSizer.countLines(dir));
    }

    @Test
    void pastaInexistenteDevolveZero() {
        assertEquals(0, SourceSizer.countLines(dir.resolve("nao-existe")));
    }
}
