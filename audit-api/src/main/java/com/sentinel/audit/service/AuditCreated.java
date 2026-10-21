package com.sentinel.audit.service;

/**
 * Evento CDI disparado quando uma auditoria é criada.
 * <p>
 * É observado com {@code TransactionPhase.AFTER_SUCCESS}, garantindo que a execução
 * só comece depois do commit do registro (evita corrida entre a criação e a análise).
 *
 * @param auditRunId identificador da auditoria criada
 */
public record AuditCreated(Long auditRunId) {
}
