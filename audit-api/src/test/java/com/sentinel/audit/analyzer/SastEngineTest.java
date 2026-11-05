package com.sentinel.audit.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Escolha do motor (Opengrep ou Semgrep) e as diferenças de linha de comando entre os dois. */
class SastEngineTest {

    private static void executavel(Path dir, String nome) throws IOException {
        Path f = Files.createFile(dir.resolve(nome));
        assertTrue(f.toFile().setExecutable(true));
    }

    @Test
    void semMotorNoPathNaoAcha(@TempDir Path vazio) {
        assertTrue(SastEngine.find(vazio.toString(), null).isEmpty());
        assertTrue(SastEngine.find(null, null).isEmpty());
        assertTrue(SastEngine.find("  ", null).isEmpty());
    }

    @Test
    void opengrepVemAntesDoSemgrep(@TempDir Path dir) throws IOException {
        executavel(dir, "semgrep");
        executavel(dir, "opengrep");
        SastEngine.Engine e = SastEngine.find(dir.toString(), null).orElseThrow();
        assertTrue(e.opengrep());
        assertEquals("Opengrep", e.displayName());
    }

    @Test
    void semgrepServeDeAlternativaQuandoNaoHaOpengrep(@TempDir Path dir) throws IOException {
        executavel(dir, "semgrep");
        SastEngine.Engine e = SastEngine.find(dir.toString(), null).orElseThrow();
        assertFalse(e.opengrep());
        assertEquals("Semgrep", e.displayName());
    }

    @Test
    void variavelForcaOMotor(@TempDir Path dir) throws IOException {
        executavel(dir, "semgrep");
        executavel(dir, "opengrep");
        assertFalse(SastEngine.find(dir.toString(), "semgrep").orElseThrow().opengrep());
        assertTrue(SastEngine.find(dir.toString(), " OpenGrep ").orElseThrow().opengrep());
    }

    @Test
    void motorForcadoAusenteNaoCaiNoOutro(@TempDir Path dir) throws IOException {
        executavel(dir, "semgrep");
        Optional<SastEngine.Engine> e = SastEngine.find(dir.toString(), "opengrep");
        assertTrue(e.isEmpty(), "pedir o Opengrep e achar só o Semgrep não deve trocar de motor em silêncio");
    }

    @Test
    void opengrepNaoRecebeMetricsMasSemgrepRecebe(@TempDir Path dir) {
        // o Opengrep recusa --metrics ("unknown option") e sai com código 2 sem rodar nenhuma regra
        assertEquals(List.of("--disable-version-check"), new SastEngine.Engine(dir, true).quietFlags());
        assertEquals(List.of("--metrics=off", "--disable-version-check"), new SastEngine.Engine(dir, false).quietFlags());
    }

    @Test
    void ambienteDoProcessoForcaUtf8ESemTelemetria() {
        Map<String, String> env = new HashMap<>();
        SastEngine.prepare(env);
        assertEquals("C.UTF-8", env.get("LC_ALL"));      // sem isso o Opengrep falha nas regras com acento
        assertEquals("off", env.get("SEMGREP_SEND_METRICS"));
        assertEquals("0", env.get("SEMGREP_ENABLE_VERSION_CHECK"));
    }

    @Test
    void onomeDoAnalisadorEOpengrepEONomeAntigoContaComoOMesmo() {
        assertEquals("Opengrep", SastEngine.TOOL_NAME);
        assertTrue(SastEngine.isSastTool("Opengrep"));
        assertTrue(SastEngine.isSastTool("semgrep"), "achados antigos ainda podem trazer o nome antigo");
        assertFalse(SastEngine.isSastTool("PMD"));
        assertFalse(SastEngine.isSastTool(null));
        assertTrue(SastEngine.sameTool("Semgrep", "Opengrep"));
        assertTrue(SastEngine.sameTool("PMD", "pmd"));
        assertFalse(SastEngine.sameTool("PMD", "Opengrep"));
        assertFalse(SastEngine.sameTool(null, "Opengrep"));
    }
}
