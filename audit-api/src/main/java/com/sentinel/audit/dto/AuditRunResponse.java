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
    private String triggeredBy;
    /** Perfil de regras do sistema (o efetivo, "default" quando não há). */
    private String profile;
    private String commitSha;
    private Long linesOfCode;
    /** Achados ativos (sem os suprimidos). */
    private Long totalFindings;
    /** Novos desde a auditoria anterior e resolvidos desde ela; nulos quando não há anterior ou não foi calculado. */
    private Integer newFindings;
    private Integer resolvedFindings;
    private Long previousAuditId;
    private Double scoreDelta;

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

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public Long getLinesOfCode() {
        return linesOfCode;
    }

    public void setLinesOfCode(Long linesOfCode) {
        this.linesOfCode = linesOfCode;
    }

    public Long getTotalFindings() {
        return totalFindings;
    }

    public void setTotalFindings(Long totalFindings) {
        this.totalFindings = totalFindings;
    }

    public Integer getNewFindings() {
        return newFindings;
    }

    public void setNewFindings(Integer newFindings) {
        this.newFindings = newFindings;
    }

    public Integer getResolvedFindings() {
        return resolvedFindings;
    }

    public void setResolvedFindings(Integer resolvedFindings) {
        this.resolvedFindings = resolvedFindings;
    }

    public Long getPreviousAuditId() {
        return previousAuditId;
    }

    public void setPreviousAuditId(Long previousAuditId) {
        this.previousAuditId = previousAuditId;
    }

    public Double getScoreDelta() {
        return scoreDelta;
    }

    public void setScoreDelta(Double scoreDelta) {
        this.scoreDelta = scoreDelta;
    }
}
