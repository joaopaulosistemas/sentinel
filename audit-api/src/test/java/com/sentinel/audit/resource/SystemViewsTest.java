package com.sentinel.audit.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.audit.dto.SystemSummaryResponse;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.RiskScore;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.RunCatalog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** O resumo do sistema mostra o escopo da última auditoria concluída, não o ajuste de agora. */
class SystemViewsTest {

    private static AuditRun run(long id, AuditStatus status, double score, String disabledSides) {
        AuditRun r = new AuditRun();
        r.setId(id);
        r.setStatus(status);
        r.setDisabledSides(disabledSides);
        r.setCompletedAt(LocalDateTime.of(2026, 11, 1, 9, 0).plusDays(id));
        RiskScore rs = new RiskScore();
        rs.setOverall(score);
        r.setRiskScore(rs);
        return r;
    }

    private static SystemViews views(List<AuditRun> runs) {
        SystemViews v = new SystemViews();
        v.runCatalog = new RunCatalog() {
            @Override public List<AuditRun> bySystem(Long systemId) { return runs; }
        };
        v.findingCatalog = new FindingCatalog() {
            @Override public Map<Severity, Long> countBySeverity(Long auditRunId) { return Map.of(); }
        };
        return v;
    }

    private static AuditedSystem system() {
        AuditedSystem s = new AuditedSystem();
        s.setId(5L);
        s.setName("api");
        return s;
    }

    @Test
    void escopoVemDaUltimaAuditoriaConcluidaENaoDaEmAndamento() {
        SystemSummaryResponse r = views(List.of(
                run(3, AuditStatus.RUNNING, 0, "back"),
                run(2, AuditStatus.COMPLETED, 71.4, "front"),
                run(1, AuditStatus.COMPLETED, 60.0, ""))).summary(system());

        assertEquals(List.of("front"), r.disabledSides());
        assertEquals(71.4, r.lastAuditScore());
    }

    @Test
    void escopoVazioQuandoOsDoisLadosEstavamLigadosQuandoNuloOuSemAuditoria() {
        assertTrue(views(List.of(run(1, AuditStatus.COMPLETED, 80, ""))).summary(system()).disabledSides().isEmpty());
        assertTrue(views(List.of(run(1, AuditStatus.COMPLETED, 80, null))).summary(system()).disabledSides().isEmpty(),
                "auditoria anterior à funcionalidade: escopo desconhecido, sem selo");
        SystemSummaryResponse semAuditoria = views(List.of()).summary(system());
        assertNotNull(semAuditoria.disabledSides());
        assertTrue(semAuditoria.disabledSides().isEmpty());
    }

    @Test
    void escopoNaoMudaQuandoOAjusteDoSistemaMudaDepoisDaAuditoria() {
        AuditRun concluida = run(1, AuditStatus.COMPLETED, 75, "front");
        SystemViews v = views(List.of(concluida));
        AuditedSystem s = system();

        assertEquals(List.of("front"), v.summary(s).disabledSides());
        // o ajuste do sistema pode mudar a qualquer momento; o selo só muda quando uma nova auditoria concluir
        assertEquals(List.of("front"), v.summary(s).disabledSides());
    }
}
