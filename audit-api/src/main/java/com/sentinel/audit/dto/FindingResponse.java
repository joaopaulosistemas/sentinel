package com.sentinel.audit.dto;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import java.time.LocalDateTime;

/**
 * DTO de resposta representando um finding de auditoria.
 */
public class FindingResponse {

    private Long id;
    private Category category;
    private Severity severity;
    private String title;
    private String description;
    private String filePath;
    private Integer lineNumber;
    private String codeSnippet;
    private String ruleId;
    private String toolSource;
    private String cveId;
    private String aiRecommendation;
    private FindingStatus status;
    private LocalDateTime createdAt;
    private String fingerprint;
    private Boolean isNew;
    private Boolean shadow;
    private RemediationDto remediation;

    public FindingResponse() {
    }

    public Boolean getShadow() {
        return shadow;
    }

    public void setShadow(Boolean shadow) {
        this.shadow = shadow;
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

    public FindingStatus getStatus() {
        return status;
    }

    public void setStatus(FindingStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    /** {@code true} = não existia na auditoria anterior; {@code null} = sem auditoria anterior para comparar. */
    public Boolean getIsNew() {
        return isNew;
    }

    public void setIsNew(Boolean isNew) {
        this.isNew = isNew;
    }

    public RemediationDto getRemediation() {
        return remediation;
    }

    public void setRemediation(RemediationDto remediation) {
        this.remediation = remediation;
    }
}
