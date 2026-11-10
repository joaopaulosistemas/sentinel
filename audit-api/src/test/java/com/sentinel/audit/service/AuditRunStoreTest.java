package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.audit.insights.AuditCompleteness;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.repository.AuditRunRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditRunStoreTest {

    private final Map<Long, AuditRun> runs = new HashMap<>();
    private AuditRunStore store;

    private AuditRun run(long id, AuditStatus status) {
        AuditRun r = new AuditRun();
        r.setId(id);
        r.setStatus(status);
        runs.put(id, r);
        return r;
    }

    @BeforeEach
    void setUp() {
        store = new AuditRunStore();
        store.auditRunRepository = new AuditRunRepository() {
            @Override public AuditRun findById(Long id) { return runs.get(id); }
        };
    }

    @Test
    void cancelarPendenteOuEmExecucaoMarcaCanceladaComHorario() {
        AuditRun pending = run(1, AuditStatus.PENDING);
        AuditRun running = run(2, AuditStatus.RUNNING);

        assertTrue(store.markCancelledByUser(1L));
        assertTrue(store.markCancelledByUser(2L));

        for (AuditRun r : List.of(pending, running)) {
            assertEquals(AuditStatus.CANCELLED, r.getStatus());
            assertNotNull(r.getCompletedAt());
            assertEquals("Cancelada pelo usuário", r.getErrorMessage());
        }
    }

    @Test
    void naoCancelaAuditoriaJaEncerradaOuInexistente() {
        AuditRun done = run(3, AuditStatus.COMPLETED);

        assertFalse(store.markCancelledByUser(3L));
        assertFalse(store.markCancelledByUser(99L));
        assertEquals(AuditStatus.COMPLETED, done.getStatus());
        assertNull(done.getErrorMessage());
    }

    @Test
    void falhaGravaMotivoEHorario() {
        AuditRun r = run(4, AuditStatus.RUNNING);

        store.updateStatusFailed(4L, "repositório inacessível");

        assertEquals(AuditStatus.FAILED, r.getStatus());
        assertNotNull(r.getCompletedAt());
        assertTrue(r.getErrorMessage().contains("repositório inacessível"));
    }

    @Test
    void completudeIncompletaGravaMotivosELimpaQuandoCompleta() {
        AuditRun r = run(5, AuditStatus.COMPLETED);

        store.storeCompleteness(5L, new AuditCompleteness.Result(true, List.of("trivy: falhou")));
        assertTrue(r.isIncomplete());
        assertEquals(List.of("trivy: falhou"), r.getIncompleteReasonList());

        store.storeCompleteness(5L, new AuditCompleteness.Result(false, List.of()));
        assertFalse(r.isIncomplete());
        assertTrue(r.getIncompleteReasonList().isEmpty());
    }

    @Test
    void guardaContextoESbomEIgnoraAuditoriaInexistente() {
        AuditRun r = run(6, AuditStatus.COMPLETED);

        store.storeContext(6L, "{\"a\":1}");
        store.storeSbom(6L, "{\"bom\":true}");
        store.storeContext(77L, "{}");

        assertEquals("{\"a\":1}", r.getContextJson());
        assertEquals("{\"bom\":true}", r.getSbomJson());
    }
}
