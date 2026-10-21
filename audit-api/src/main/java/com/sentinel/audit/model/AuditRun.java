package com.sentinel.audit.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade que representa uma execução de auditoria de um sistema.
 * Uma execução contém múltiplos findings e um score de risco calculado.
 */
@Entity
@Table(name = "audit_run",
        indexes = {
                @Index(name = "idx_audit_run_system_id", columnList = "system_id"),
                @Index(name = "idx_audit_run_status", columnList = "status")
        })
public class AuditRun {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_run_seq")
    @SequenceGenerator(name = "audit_run_seq", sequenceName = "audit_run_seq", allocationSize = 1)
    private Long id;

    @Column(length = 255)
    private String branch;

    /** Auditoria de PR: só vale o que mudou em relação a esta referência (branch base). Nula = auditoria completa. */
    @Column(name = "base_ref", length = 255)
    private String baseRef;

    @Column(name = "triggered_by", length = 255)
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditStatus status;

    @Embedded
    private RiskScore riskScore;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Motivo da falha (preenchido apenas quando o status é FAILED). */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** SBOM CycloneDX (JSON) das dependências encontradas nesta auditoria. */
    @Column(name = "sbom_json", columnDefinition = "TEXT")
    private String sbomJson;

    /** Mapa do projeto e situação dos analisadores (JSON), usados para montar os insights. */
    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false)
    private AuditedSystem system;

    @OneToMany(mappedBy = "auditRun", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Finding> findings = new ArrayList<>();

    public AuditRun() {
    }

    public String getSbomJson() {
        return sbomJson;
    }

    public void setSbomJson(String sbomJson) {
        this.sbomJson = sbomJson;
    }

    public String getContextJson() {
        return contextJson;
    }

    public void setContextJson(String contextJson) {
        this.contextJson = contextJson;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public void setBaseRef(String baseRef) {
        this.baseRef = baseRef;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public void setStatus(AuditStatus status) {
        this.status = status;
    }

    public RiskScore getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(RiskScore riskScore) {
        this.riskScore = riskScore;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public AuditedSystem getSystem() {
        return system;
    }

    public void setSystem(AuditedSystem system) {
        this.system = system;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public void setFindings(List<Finding> findings) {
        this.findings = findings;
    }
}
