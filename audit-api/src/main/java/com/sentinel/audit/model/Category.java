package com.sentinel.audit.model;

/**
 * Categoria de um finding de auditoria.
 */
public enum Category {
    SECURITY,
    QUALITY,
    ARCHITECTURE,
    DEPENDENCY,
    OBSERVABILITY,
    /** Consultas lentas, memória, concorrência e uso de recursos. */
    PERFORMANCE
}
