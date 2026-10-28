package com.sentinel.audit.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Detecta o bloqueio do Maven Central (429 / Retry-After) na saída de erro do Trivy. */
class TrivyMavenLimitTest {

    @TempDir
    Path dir;

    private Path stderr(String texto) throws IOException {
        Path f = dir.resolve("err.txt");
        Files.writeString(f, texto);
        return f;
    }

    @Test
    void reconheceBloqueioDoMavenCentral() throws IOException {
        assertTrue(TrivyAnalyzer.limiteDoMaven(stderr(
                "failed to get parent pom quarkus-bom: Retry-After: 1800. The repository blocks all subsequent requests")));
        assertTrue(TrivyAnalyzer.limiteDoMaven(stderr("unexpected status code: 429 Too Many Requests")));
    }

    @Test
    void ignoraOutrasFalhas() throws IOException {
        assertFalse(TrivyAnalyzer.limiteDoMaven(stderr("x509: certificate signed by unknown authority")));
        assertFalse(TrivyAnalyzer.limiteDoMaven(dir.resolve("nao-existe.txt")));
    }
}
