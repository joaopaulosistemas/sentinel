package com.sentinel.audit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuditRunCompletenessTest {

    @Test
    void auditoriaAntigaSemRegistroContaComoCompleta() {
        AuditRun run = new AuditRun();
        assertFalse(run.isIncomplete());
        assertTrue(run.getIncompleteReasonList().isEmpty());
    }

    @Test
    void guardaUmMotivoPorLinha() {
        AuditRun run = new AuditRun();
        run.setIncomplete(true);
        run.setIncompleteReasons("trivy: falhou\nosv: parcial");
        assertTrue(run.isIncomplete());
        assertEquals(List.of("trivy: falhou", "osv: parcial"), run.getIncompleteReasonList());
    }
}
