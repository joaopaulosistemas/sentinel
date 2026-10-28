package com.sentinel.audit.analyzer;

import com.sentinel.audit.insights.AnalyzerRunInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Zero achados" só pode parecer limpo se a ferramenta realmente terminou: o resto é PARCIAL, com o motivo. */
class AnalyzerHonestStatusTest {

    @TempDir
    Path tmp;

    @Test
    void resultadoSemNotaNaoEParcial() {
        AnalyzerResult r = new AnalyzerResult(List.of(), "X", Duration.ZERO);
        assertFalse(r.partial());
        assertNull(r.partialNote());
    }

    @Test
    void resultadoComNotaEParcial() {
        AnalyzerResult r = new AnalyzerResult(List.of(), "X", Duration.ZERO, "excedeu o tempo");
        assertTrue(r.partial());
        assertFalse(new AnalyzerResult(List.of(), "X", Duration.ZERO, "  ").partial());
    }

    @Test
    void semgrepComJsonLimpoNaoEParcial() {
        assertNull(SemgrepAnalyzer.problemasNoJson("{\"results\":[],\"errors\":[]}"));
        // aviso de parse parcial é comum em código real e não conta
        assertNull(SemgrepAnalyzer.problemasNoJson(
                "{\"results\":[],\"errors\":[{\"type\":\"PartialParsing\",\"level\":\"warn\",\"message\":\"x\"}]}"));
    }

    @Test
    void semgrepComErroGraveOuTimeoutEParcial() {
        String grave = SemgrepAnalyzer.problemasNoJson(
                "{\"results\":[],\"errors\":[{\"type\":\"SemgrepError\",\"level\":\"error\",\"message\":\"regra invalida\"}]}");
        assertNotNull(grave);
        assertTrue(grave.contains("1 erro(s)") && grave.contains("regra invalida"), grave);

        String timeout = SemgrepAnalyzer.problemasNoJson(
                "{\"results\":[],\"errors\":[{\"type\":\"Timeout\",\"level\":\"warn\",\"message\":\"demorou\"}]}");
        assertNotNull(timeout);
        assertTrue(timeout.contains("1 timeout(s)"), timeout);
    }

    @Test
    void semgrepComJsonIlegivelEParcial() {
        assertEquals("saída JSON do Semgrep ilegível", SemgrepAnalyzer.problemasNoJson("{nao e json"));
    }

    @Test
    void falhaDescreveTimeoutECodigoComFimDoStderr() throws IOException {
        assertEquals("excedeu o tempo limite de 30s e foi encerrado", ExternalTools.falha(-1, 30, null));
        Path err = tmp.resolve("err.txt");
        Files.writeString(err, "linha 1\n\n   linha  2 com   espaços\n");
        assertEquals("terminou com código 2 — linha 1 linha 2 com espaços", ExternalTools.falha(2, 30, err));
    }

    @Test
    void tailLimitaOTamanhoEMantemOFim() throws IOException {
        Path err = tmp.resolve("err.txt");
        Files.writeString(err, "a".repeat(1000) + "FIM");
        String t = ExternalTools.tail(err, 10);
        assertTrue(t.startsWith("…") && t.endsWith("FIM"), t);
        assertEquals("", ExternalTools.tail(tmp.resolve("nao-existe"), 10));
    }

    @Test
    void ferramentaQueSaiComErroViraParcialNoStderrCapturado() throws Exception {
        Path err = Files.createTempFile(tmp, "err", ".txt");
        int code = ExternalTools.run(List.of("sh", "-c", "echo falha-de-teste >&2; exit 3"), null, java.util.Map.of(), 10, null, err);
        assertEquals(3, code);
        assertTrue(ExternalTools.falha(code, 10, err).contains("falha-de-teste"));
    }

    @Test
    void statusParcialNaoContaComoOkMasEReconhecido() {
        AnalyzerRunInfo parcial = new AnalyzerRunInfo("Semgrep", "PARCIAL", 0, 10, "timeout");
        assertFalse(parcial.ok());
        assertTrue(parcial.partial());
        assertTrue(new AnalyzerRunInfo("Semgrep", "OK", 0, 10, null).ok());
    }
}
