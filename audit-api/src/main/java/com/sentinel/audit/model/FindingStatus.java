package com.sentinel.audit.model;

/**
 * Status de um finding individual.
 */
public enum FindingStatus {
    OPEN,
    ACKNOWLEDGED,
    FIXED,
    /** Marcado como falso positivo / risco aceito: fora do score e da listagem padrão. */
    SUPPRESSED
}
