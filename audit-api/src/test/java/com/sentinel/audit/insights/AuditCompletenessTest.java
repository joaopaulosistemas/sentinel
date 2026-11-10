package com.sentinel.audit.insights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuditCompletenessTest {

    private static AnalyzerRunInfo run(String name, String status) {
        return new AnalyzerRunInfo(name, status, 0, 10, null);
    }

    @Test
    void contextoAusenteNaoMarcaIncompleto() {
        assertFalse(AuditCompleteness.of(null).incomplete());
    }

    @Test
    void todosOkEstaCompleto() {
        var data = new AuditContextData(null, List.of(run("semgrep", "OK"), run("trivy", "OK")));
        var result = AuditCompleteness.of(data);
        assertFalse(result.incomplete());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void falhaOuParcialMarcamIncompletoComMotivo() {
        var data = new AuditContextData(null, List.of(
                run("semgrep", "OK"), run("trivy", "FALHOU"), run("osv", "PARCIAL")));
        var result = AuditCompleteness.of(data);
        assertTrue(result.incomplete());
        assertEquals(List.of("trivy: falhou", "osv: parcial"), result.reasons());
    }

    @Test
    void ferramentaIndisponivelOuDesligadaNaoReprova() {
        var data = new AuditContextData(null, List.of(
                run("a", "INDISPONIVEL"), run("b", "DESLIGADO"), run("c", "NAO_SUPORTADO"), run("d", "SUBSTITUIDO")));
        assertFalse(AuditCompleteness.of(data).incomplete());
    }

    @Test
    void statusDesconhecidoContaComoIncompleto() {
        var data = new AuditContextData(null, List.of(run("x", "NOVO_STATUS")));
        assertTrue(AuditCompleteness.of(data).incomplete());
    }

    @Test
    void statusEnumConverteAsStringsGravadas() {
        assertEquals(AnalyzerStatus.PARCIAL, AnalyzerStatus.parse("PARCIAL"));
        assertEquals(AnalyzerStatus.DESCONHECIDO, AnalyzerStatus.parse("lixo"));
        assertEquals(AnalyzerStatus.DESCONHECIDO, AnalyzerStatus.parse(null));
    }
}
