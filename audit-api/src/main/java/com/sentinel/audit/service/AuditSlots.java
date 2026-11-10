package com.sentinel.audit.service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Limita quantas auditorias rodam ao mesmo tempo. Cada uma clona um repositório e dispara várias ferramentas
 * externas em paralelo; sem limite, várias auditorias juntas esgotam CPU, memória e disco. As que passam do limite
 * esperam em PENDING.
 */
final class AuditSlots {

    private static final long POLL_MILLIS = 500L;

    private final Semaphore permits;

    AuditSlots(int max) {
        this.permits = new Semaphore(Math.max(1, max), true);
    }

    /** Espera uma vaga, conferindo a cada meio segundo se o usuário cancelou a auditoria. */
    void acquire(Long auditRunId, BooleanSupplier cancelled) {
        try {
            while (!permits.tryAcquire(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean()) {
                    throw new AuditCancelledException(auditRunId);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido esperando vaga para a auditoria " + auditRunId, e);
        }
        if (cancelled.getAsBoolean()) {
            permits.release();
            throw new AuditCancelledException(auditRunId);
        }
    }

    void release() {
        permits.release();
    }

    int available() {
        return permits.availablePermits();
    }
}
