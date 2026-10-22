package com.sentinel.audit.service;

import com.sentinel.audit.dto.AuditProgressDto;
import com.sentinel.audit.dto.AuditProgressDto.AnalyzerProgress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditProgressTrackerTest {

    @Test
    void semAuditoriaRegistradaNaoHaAndamento() {
        assertTrue(new AuditProgressTracker().snapshot(1L).isEmpty());
    }

    @Test
    void acompanhaEtapaEAnalisadoresEmOrdemDoPlano() {
        AuditProgressTracker t = new AuditProgressTracker();
        t.queued(7L);
        t.phase(7L, AuditProgressTracker.ANALISE, "iniciando");
        // um analisador rápido começa antes de o plano ser registrado: o estado dele não pode ser perdido
        t.analyzerStarted(7L, "Semgrep");
        t.planned(7L, List.of(
                new AnalyzerProgress("Dependências", "ESPERANDO", 0, 0, null),
                new AnalyzerProgress("Semgrep", "ESPERANDO", 0, 0, null),
                new AnalyzerProgress("PMD", "INDISPONIVEL", 0, 0, "ferramenta ausente")));
        t.analyzerFinished(7L, "Semgrep", "OK", 12, 900, null);

        AuditProgressDto s = t.snapshot(7L).orElseThrow();
        assertEquals("ANALISE", s.phase());
        assertEquals(List.of("Dependências", "Semgrep", "PMD"), s.analyzers().stream().map(AnalyzerProgress::name).toList());
        assertEquals("OK", s.analyzers().get(1).status());
        assertEquals(12, s.analyzers().get(1).findings());
        assertEquals("ESPERANDO", s.analyzers().get(0).status());
        assertTrue(s.events().stream().anyMatch(e -> e.message().contains("Semgrep concluído: 12 achados")));
    }

    @Test
    void falhaDeAnalisadorApareceNosEventos() {
        AuditProgressTracker t = new AuditProgressTracker();
        t.queued(3L);
        t.analyzerFinished(3L, "Trivy", "FALHOU", 0, 0, "timeout");
        assertTrue(t.snapshot(3L).orElseThrow().events().stream().anyMatch(e -> e.message().equals("Trivy falhou: timeout")));
    }

    @Test
    void aoTerminarOAndamentoSome() {
        AuditProgressTracker t = new AuditProgressTracker();
        t.queued(5L);
        t.finish(5L);
        assertTrue(t.snapshot(5L).isEmpty());
        // chamadas tardias não podem recriar nem quebrar
        t.phase(5L, AuditProgressTracker.RELATORIO, "x");
        assertTrue(t.snapshot(5L).isEmpty());
    }

    @Test
    void pedidoDeCancelamentoVaiAteOFimDaAuditoria() {
        AuditProgressTracker t = new AuditProgressTracker();
        assertFalse(t.isCancelRequested(9L));
        t.requestCancel(9L);          // pode chegar antes de o andamento existir (auditoria ainda na fila)
        assertTrue(t.isCancelRequested(9L));
        assertFalse(t.isCancelRequested(10L));
        t.finish(9L);
        assertFalse(t.isCancelRequested(9L), "ao terminar, o pedido some e não afeta outra execução");
        t.requestCancel(null);
        assertFalse(t.isCancelRequested(null));
    }
}
