package com.sentinel.audit.model;

/**
 * Status de execução de uma auditoria.
 */
public enum AuditStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    /** Interrompida pelo usuário antes de terminar. */
    CANCELLED
}
