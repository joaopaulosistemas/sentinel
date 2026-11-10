package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AuditSlotsTest {

    @Test
    void respeitaOLimiteEEsperaALiberacao() throws Exception {
        AuditSlots slots = new AuditSlots(1);
        slots.acquire(1L, () -> false);
        assertEquals(0, slots.available());

        AtomicBoolean second = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            slots.acquire(2L, () -> false);
            second.set(true);
            done.countDown();
        });
        t.start();

        assertFalse(done.await(300, TimeUnit.MILLISECONDS), "a segunda auditoria deve esperar na fila");
        slots.release();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(second.get());
    }

    @Test
    void cancelarEnquantoEsperaLiberaAFilaSemTomarVaga() {
        AuditSlots slots = new AuditSlots(1);
        slots.acquire(1L, () -> false);

        assertThrows(AuditCancelledException.class, () -> slots.acquire(2L, () -> true));
        assertEquals(0, slots.available());
        slots.release();
        assertEquals(1, slots.available());
    }

    @Test
    void limiteInvalidoVira1() {
        assertEquals(1, new AuditSlots(0).available());
    }
}
