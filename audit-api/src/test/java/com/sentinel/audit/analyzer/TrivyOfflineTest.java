package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O modo offline do Trivy ({@code TRIVY_OFFLINE}) e a repetição sem rede depois do 429 do Maven Central, com um {@code trivy} falso. */
class TrivyOfflineTest {

    @TempDir
    Path dir;

    /** Um trivy que responde 429 quando chamado com rede e grava um relatório vazio quando chamado com --offline-scan. */
    private Path trivyQueLimitaOMaven() throws IOException {
        Path bin = Files.createDirectory(dir.resolve("bin"));
        Path f = bin.resolve("trivy");
        Files.writeString(f, """
                #!/bin/sh
                echo "$@" >> "%s"
                OFFLINE=0
                while [ $# -gt 0 ]; do
                  case "$1" in
                    --offline-scan) OFFLINE=1 ;;
                    --output) OUT="$2" ;;
                  esac
                  shift
                done
                if [ "$OFFLINE" = 0 ]; then
                  echo "failed to get parent pom: Retry-After: 1800 (429 Too Many Requests)" >&2
                  exit 1
                fi
                echo '{"Results":[]}' > "$OUT"
                """.formatted(chamadas()));
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rwxr-xr-x"));
        return bin;
    }

    private Path chamadas() {
        return dir.resolve("chamadas.txt");
    }

    private List<String> linhasDeChamada() throws IOException {
        return Files.exists(chamadas()) ? Files.readAllLines(chamadas()) : List.of();
    }

    private TrivyAnalyzer analisador(Path bin, boolean offline) {
        TrivyAnalyzer t = new TrivyAnalyzer();
        t.enabled = true;
        t.offline = offline;
        t.scanners = "vuln";
        t.timeoutSeconds = 30;
        t.pathEnv = bin.toString();
        return t;
    }

    private static AuditRun run() {
        AuditRun r = new AuditRun();
        r.setId(1L);
        return r;
    }

    @Test
    void semOpcaoTentaComRedeERepeteOfflineNoLimiteDoMavenAvisandoDoCache() throws IOException {
        AnalyzerResult r = analisador(trivyQueLimitaOMaven(), false).analyze(Files.createDirectory(dir.resolve("p")), run());

        List<String> linhas = linhasDeChamada();
        assertEquals(2, linhas.size(), "uma tentativa com rede e a repetição offline: " + linhas);
        assertFalse(linhas.get(0).contains("--offline-scan"));
        assertTrue(linhas.get(1).contains("--offline-scan"));
        assertEquals(TrivyAnalyzer.NOTA_CACHE_LOCAL, r.partialNote());
    }

    @Test
    void comTrivyOfflineVaiDiretoSemRedeESemOAvisoDeLimite() throws IOException {
        AnalyzerResult r = analisador(trivyQueLimitaOMaven(), true).analyze(Files.createDirectory(dir.resolve("p")), run());

        List<String> linhas = linhasDeChamada();
        assertEquals(1, linhas.size(), "não deve tentar o Maven Central: " + linhas);
        assertTrue(linhas.get(0).contains("--offline-scan"));
        assertNull(r.partialNote());
    }

    @Test
    void comandoSoLevaOfflineScanQuandoPedido() {
        TrivyAnalyzer t = analisador(dir, false);
        Path exe = Path.of("/usr/bin/trivy");
        Path report = Path.of("/tmp/r.json");
        Path projeto = Path.of("/work/proj");

        assertFalse(t.comando(exe, projeto, report, false).contains("--offline-scan"));
        List<String> offline = t.comando(exe, projeto, report, true);
        assertTrue(offline.contains("--offline-scan"));
        assertEquals("/work/proj", offline.get(offline.size() - 1), "o caminho do projeto continua por último");
    }

    @Test
    void avisoDeLimiteDizQueOCacheIncompletoDeixaDependenciasDeFora() {
        String nota = TrivyAnalyzer.NOTA_CACHE_LOCAL;
        assertTrue(nota.contains("se o cache não cobre este projeto"));
        assertTrue(nota.contains("scripts/aquecer-cache-maven.sh"));
    }
}
