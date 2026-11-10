package com.sentinel.audit.analyzer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PMD 7 sai com código 5 quando algum arquivo não pôde ser analisado; o relatório continua válido. */
class PmdProcessingErrorsTest {

    @Test
    void contaArquivosComErroDeProcessamento() {
        String json = "{\"files\":[],\"processingErrors\":[{\"filename\":\"A.java\"},{\"filename\":\"B.java\"}]}";
        assertEquals(2, PmdAnalyzer.processingErrors(json));
    }

    @Test
    void relatorioSemErrosOuIlegivelRetornaZero() {
        assertEquals(0, PmdAnalyzer.processingErrors("{\"files\":[]}"));
        assertEquals(0, PmdAnalyzer.processingErrors("não é json"));
    }

    @Test
    void codigoCincoEhErroRecuperavel() {
        assertEquals(5, PmdAnalyzer.EXIT_RECOVERABLE_ERRORS);
    }

    private static final Path ROOT = Path.of("/clones/abc");

    private static String erro(String arquivo, String mensagem) {
        return "{\"filename\":\"" + ROOT.resolve(arquivo) + "\",\"message\":\"" + mensagem + "\"}";
    }

    private static String relatorio(String... erros) {
        return "{\"files\":[],\"processingErrors\":[" + String.join(",", erros) + "]}";
    }

    @Test
    void avisoCitaOArquivoEOTipoDoErro() {
        String json = relatorio(erro("src/Foo.java", "ParseException: Parse exception at line 3"));
        String nota = PmdAnalyzer.recoverableNote(json, ROOT, caminho -> false);
        assertTrue(nota.contains("1 arquivo(s): src/Foo.java (ParseException)"), nota);
        assertFalse(nota.contains("/clones/abc"), nota);
    }

    @Test
    void erroEmArquivoExcluidoNaoTornaOResultadoParcial() {
        String json = relatorio(erro("audit-rules/packs/core-security/tests/x.java", "ParseException: x"));
        Predicate<String> excluidos = caminho -> caminho.startsWith("audit-rules/packs/");
        assertNull(PmdAnalyzer.recoverableNote(json, ROOT, excluidos));
    }

    @Test
    void soContaOsArquivosNaoExcluidos() {
        String json = relatorio(erro("audit-rules/packs/a/tests/x.java", "ParseException: x"),
                erro("src/Real.java", "ParseException: y"));
        String nota = PmdAnalyzer.recoverableNote(json, ROOT, caminho -> caminho.startsWith("audit-rules/"));
        assertTrue(nota.contains("1 arquivo(s): src/Real.java"), nota);
        assertFalse(nota.contains("audit-rules"), nota);
    }

    @Test
    void listaSoOsPrimeirosArquivosEResumeOResto() {
        String json = relatorio(erro("a.java", "ParseException: 1"), erro("b.java", "ParseException: 2"),
                erro("c.java", "ParseException: 3"), erro("d.java", "ParseException: 4"));
        String nota = PmdAnalyzer.recoverableNote(json, ROOT, caminho -> false);
        assertTrue(nota.contains("4 arquivo(s): a.java (ParseException), b.java (ParseException), "
                + "c.java (ParseException) e mais 1"), nota);
    }

    @Test
    void semDetalheNoRelatorioMantemOAvisoGenerico() {
        String esperado = "o PMD não conseguiu analisar alguns arquivos; os demais foram analisados";
        assertEquals(esperado, PmdAnalyzer.recoverableNote("{\"files\":[]}", ROOT, caminho -> false));
        assertEquals(esperado, PmdAnalyzer.recoverableNote("não é json", ROOT, caminho -> false));
    }

    @Test
    void mensagemSemTipoViraErroDeLeitura() {
        String json = relatorio(erro("src/Foo.java", "falha estranha sem dois pontos"));
        assertTrue(PmdAnalyzer.recoverableNote(json, ROOT, caminho -> false).contains("(erro de leitura)"));
    }
}
