package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditRetentionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);

    private static AuditRun run(long id, int daysAgo, AuditStatus status) {
        AuditRun r = new AuditRun();
        r.setId(id);
        r.setStatus(status);
        r.setStartedAt(NOW.minusDays(daysAgo));
        return r;
    }

    @Test
    void semLimiteDeIdadeNadaExpira() {
        List<AuditRun> runs = List.of(run(3, 1, AuditStatus.COMPLETED), run(2, 400, AuditStatus.COMPLETED));
        assertTrue(AuditRetention.expired(runs, 5, 0, NOW).isEmpty());
    }

    @Test
    void expiraAsAntigasMasPreservaAsMaisRecentes() {
        List<AuditRun> runs = new ArrayList<>(List.of(
                run(5, 1, AuditStatus.COMPLETED), run(4, 100, AuditStatus.COMPLETED),
                run(3, 200, AuditStatus.COMPLETED), run(2, 300, AuditStatus.COMPLETED), run(1, 400, AuditStatus.COMPLETED)));
        List<Long> ids = AuditRetention.expired(runs, 3, 90, NOW).stream().map(AuditRun::getId).toList();
        assertEquals(List.of(2L, 1L), ids, "as 3 mais novas ficam mesmo passando da idade");
    }

    @Test
    void auditoriaEmAndamentoNuncaExpira() {
        List<AuditRun> runs = List.of(run(3, 1, AuditStatus.COMPLETED), run(2, 500, AuditStatus.RUNNING),
                run(1, 500, AuditStatus.PENDING));
        assertTrue(AuditRetention.expired(runs, 1, 30, NOW).isEmpty());
    }

    @Test
    void respeitaOLimiteMinimoDePreservadas() {
        List<AuditRun> runs = List.of(run(2, 400, AuditStatus.COMPLETED), run(1, 500, AuditStatus.COMPLETED));
        assertTrue(AuditRetention.expired(runs, 5, 30, NOW).isEmpty());
        assertEquals(1, AuditRetention.expired(runs, 1, 30, NOW).size());
    }
}
