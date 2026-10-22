package com.sentinel.audit.dto;

import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.Severity;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO de resposta representando uma execução de auditoria com seu resultado.
 */
public class AuditRunResponse {

    private Long id;
    private Long systemId;
    private String systemName;
    private String branch;
    private String baseRef;
    private AuditStatus status;
    private RiskScoreDto riskScore;
    private Map<Severity, Long> findingsSummary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    /** Só vem preenchido enquanto a auditoria está em execução. */
    private AuditProgressDto progress;

    public AuditRunResponse() {
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public AuditProgressDto getProgress() {
        return progress;
    }

    public void setProgress(AuditProgressDto progress) {
        this.progress = progress;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public void setBaseRef(String baseRef) {
        this.baseRef = baseRef;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public void setStatus(AuditStatus status) {
        this.status = status;
    }

    public RiskScoreDto getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(RiskScoreDto riskScore) {
        this.riskScore = riskScore;
    }

    public Map<Severity, Long> getFindingsSummary() {
        return findingsSummary;
    }

    public void setFindingsSummary(Map<Severity, Long> findingsSummary) {
        this.findingsSummary = findingsSummary;
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
}
