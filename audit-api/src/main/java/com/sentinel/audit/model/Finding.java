package com.sentinel.audit.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidade que representa um finding (problema encontrado) em uma execução de auditoria.
 * Cada finding está associado a um arquivo específico e inclui recomendações de correção.
 */
@Entity
@Table(name = "finding",
        indexes = {
                @Index(name = "idx_finding_audit_run_id", columnList = "audit_run_id"),
                @Index(name = "idx_finding_severity", columnList = "severity"),
                @Index(name = "idx_finding_fingerprint", columnList = "fingerprint")
        })
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "finding_seq")
    @SequenceGenerator(name = "finding_seq", sequenceName = "finding_seq", allocationSize = 1)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Severity severity;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "code_snippet", length = 2000)
    private String codeSnippet;

    @Column(name = "rule_id", length = 255)
    private String ruleId;

    @Column(name = "tool_source", length = 100)
    private String toolSource;

    @Column(name = "cve_id", length = 50)
    private String cveId;

    @Column(name = "ai_recommendation", length = 2000)
    private String aiRecommendation;

    /** Identidade estável do problema entre auditorias (não depende do número da linha). */
    @Column(length = 64)
    private String fingerprint;

    /** {@code true} se não existia na auditoria anterior da mesma branch; {@code null} = sem base de comparação. */
    @Column(name = "is_new")
    private Boolean isNew;

    /**
     * Modo sombra: o finding aparece, mas <b>não entra no score</b> nem na comparação entre auditorias.
     * Serve para medir uma regra nova (falso positivo) antes de ela passar a valer. {@code null} = não é sombra.
     */
    @Column(name = "shadow")
    private Boolean shadow;

    /** Confiança de que é um problema real; {@code null} em achados gravados antes deste campo existir (valem como média). */
    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", length = 10)
    private Confidence confidence;

    /** Por que a confiança não é a média (por exemplo, "arquivo de teste"); {@code null} quando não há o que explicar. */
    @Column(name = "confidence_reason", length = 300)
    private String confidenceReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FindingStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_run_id", nullable = false)
    private AuditRun auditRun;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = FindingStatus.OPEN;
        }
    }

    public Finding() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getCodeSnippet() {
        return codeSnippet;
    }

    public void setCodeSnippet(String codeSnippet) {
        this.codeSnippet = codeSnippet;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getToolSource() {
        return toolSource;
    }

    public void setToolSource(String toolSource) {
        this.toolSource = toolSource;
    }

    public String getCveId() {
        return cveId;
    }

    public void setCveId(String cveId) {
        this.cveId = cveId;
    }

    public String getAiRecommendation() {
        return aiRecommendation;
    }

    public void setAiRecommendation(String aiRecommendation) {
        this.aiRecommendation = aiRecommendation;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Boolean getIsNew() {
        return isNew;
    }

    public void setIsNew(Boolean isNew) {
        this.isNew = isNew;
    }

    public Boolean getShadow() {
        return shadow;
    }

    public void setShadow(Boolean shadow) {
        this.shadow = shadow;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public void setConfidence(Confidence confidence) {
        this.confidence = confidence;
    }

    public String getConfidenceReason() {
        return confidenceReason;
    }

    public void setConfidenceReason(String confidenceReason) {
        this.confidenceReason = confidenceReason;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public void setStatus(FindingStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public AuditRun getAuditRun() {
        return auditRun;
    }

    public void setAuditRun(AuditRun auditRun) {
        this.auditRun = auditRun;
    }
}
