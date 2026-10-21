package com.sentinel.audit.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Supressão de um finding para um sistema: auditorias futuras com o mesmo {@code fingerprint}
 * marcam o finding como {@link FindingStatus#SUPPRESSED} e o excluem do score.
 * Guarda quem decidiu, por quê e até quando.
 */
@Entity
@Table(name = "finding_suppression",
        uniqueConstraints = @UniqueConstraint(name = "uk_suppression_system_fp", columnNames = {"system_id", "fingerprint"}),
        indexes = @Index(name = "idx_suppression_system", columnList = "system_id"))
public class FindingSuppression {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "finding_suppression_seq")
    @SequenceGenerator(name = "finding_suppression_seq", sequenceName = "finding_suppression_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_id", nullable = false)
    private AuditedSystem system;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SuppressionReason reason;

    @Column(nullable = false, length = 1000)
    private String justification;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Depois desta data a supressão deixa de valer e o finding volta a aparecer. {@code null} = sem prazo. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Cópia para exibição (o finding original pode sumir das próximas auditorias)
    @Column(name = "rule_id", length = 255)
    private String ruleId;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(length = 500)
    private String title;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isActive(LocalDateTime now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AuditedSystem getSystem() { return system; }
    public void setSystem(AuditedSystem system) { this.system = system; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public SuppressionReason getReason() { return reason; }
    public void setReason(SuppressionReason reason) { this.reason = reason; }
    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
