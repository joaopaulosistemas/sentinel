package com.sentinel.audit.analyzer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
