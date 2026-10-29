package com.sentinel.audit.actions;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Pedido do Claude que só vale depois de uma pessoa aprovar no dashboard. */
@Entity
@Table(name = "pending_action", indexes = @Index(name = "idx_pending_action_system", columnList = "system_id"))
public class PendingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pending_action_seq")
    @SequenceGenerator(name = "pending_action_seq", sequenceName = "pending_action_seq", allocationSize = 1)
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PendingKind kind;

    /** Texto montado pelo Sentinel (nunca copiado do Claude) para a pessoa saber o que está aprovando. */
    @Column(nullable = false, length = 600)
    private String summary;

    /** Argumentos já validados (JSON). */
    @Column(nullable = false, length = 2000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PendingStatus status = PendingStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    @SuppressWarnings("PMD.ImmutableField") // a JPA preenche o campo ao carregar
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "result_note", length = 500)
    private String resultNote;

    @Column(name = "result_audit_run_id")
    private Long resultAuditRunId;

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public PendingKind getKind() { return kind; }
    public void setKind(PendingKind kind) { this.kind = kind; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public PendingStatus getStatus() { return status; }
    public void setStatus(PendingStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getResultNote() { return resultNote; }
    public void setResultNote(String resultNote) { this.resultNote = resultNote; }
    public Long getResultAuditRunId() { return resultAuditRunId; }
    public void setResultAuditRunId(Long id) { this.resultAuditRunId = id; }
}
