package com.sentinel.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Andamento de uma auditoria em execução, guardado no banco para que qualquer réplica da API consiga
 * mostrá-lo. Some quando a auditoria termina. {@code updatedAt} serve de batimento: uma auditoria ativa cujo
 * andamento parou de ser atualizado é considerada interrompida.
 */
@Entity
@Table(name = "audit_progress")
public class AuditProgressSnapshot {

    @Id
    @Column(name = "audit_run_id")
    private Long auditRunId;

    @Column(name = "payload", nullable = false, length = 100000)
    private String payload;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getAuditRunId() {
        return auditRunId;
    }

    public void setAuditRunId(Long auditRunId) {
        this.auditRunId = auditRunId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
