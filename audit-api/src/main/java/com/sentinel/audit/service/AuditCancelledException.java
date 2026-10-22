package com.sentinel.audit.service;

/** Sinaliza, dentro da orquestração, que o usuário cancelou a auditoria; não é uma falha. */
class AuditCancelledException extends RuntimeException {
    AuditCancelledException(Long auditRunId) {
        super("Auditoria " + auditRunId + " cancelada pelo usuário");
    }
}
