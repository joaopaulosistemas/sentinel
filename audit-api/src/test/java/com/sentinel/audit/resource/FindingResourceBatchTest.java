package com.sentinel.audit.resource;

import com.sentinel.audit.dto.BatchSuppressRequest;
import com.sentinel.audit.dto.BatchSuppressResponse;
import com.sentinel.audit.model.SuppressionReason;
import com.sentinel.audit.service.FindingLifecycleService;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingResourceBatchTest {

    /** Serviço que falha para o id 2 e registra os demais. */
    static class Fake extends FindingLifecycleService {
        final List<Long> seen = new ArrayList<>();

        @Override
        public com.sentinel.audit.model.FindingSuppression suppress(Long id, com.sentinel.audit.dto.SuppressRequest r) {
            if (id == 2L) {
                throw new NotFoundException("Finding não encontrado: 2");
            }
            seen.add(id);
            return null;
        }
    }

    @Test
    void umFalhoNaoImpedeOsDemaisEIdsRepetidosContamUmaVez() {
        Fake fake = new Fake();
        var req = new BatchSuppressRequest(List.of(1L, 2L, 3L, 3L), SuppressionReason.FALSE_POSITIVE,
                "justificativa longa o bastante", "ana", null);

        BatchSuppressResponse r = FindingSuppressionResource.runBatch(req, fake);

        assertEquals(3, r.requested());
        assertEquals(2, r.suppressed());
        assertEquals(List.of(1L, 3L), fake.seen);
        assertEquals(1, r.failures().size());
        assertEquals(2L, r.failures().get(0).findingId());
        assertTrue(r.failures().get(0).message().contains("não encontrado"));
    }
}
