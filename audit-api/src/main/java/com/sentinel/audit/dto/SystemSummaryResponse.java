package com.sentinel.audit.dto;

import com.sentinel.audit.model.Stack;
import java.time.LocalDateTime;

/**
 * DTO de resposta resumida de um sistema auditado, incluindo dados da última auditoria.
 */
public class SystemSummaryResponse {

    private Long id;
    private String name;
    private Stack stack;
    private String team;
    private Double lastAuditScore;
    private LocalDateTime lastAuditDate;
    private Long openCritical;
    private Long openHigh;
    private Integer auditIntervalHours;
    private String scheduledBranch;
    private Boolean auditOnPush;
    private String profile;
    /** Nota da última auditoria completa menos a anterior; nulo se houver menos de duas. */
    private Double scoreDelta;
    /** Quantas auditorias o sistema já tem, em qualquer situação (concluída, em execução ou com falha). */
    private Integer auditCount;
    private String repoUrl;
    private String description;
    /** Há token Git salvo? O token em si nunca é devolvido. */
    private Boolean gitTokenSet;
    private String gitTokenHosts;
    /** Há segredo de webhook salvo? O segredo nunca é devolvido. */
    private Boolean webhookSecretSet;
    private Integer gitHistoryDepth;
    private Boolean notifyUrlSet;

    public Integer getGitHistoryDepth() {
        return gitHistoryDepth;
    }

    public void setGitHistoryDepth(Integer gitHistoryDepth) {
        this.gitHistoryDepth = gitHistoryDepth;
    }

    private String notifyFormat;
    private Double notifyMinScoreDrop;
    private String notifyMinSeverity;
    private String notifyPublicUrl;

    public Boolean getNotifyUrlSet() {
        return notifyUrlSet;
    }

    public void setNotifyUrlSet(Boolean notifyUrlSet) {
        this.notifyUrlSet = notifyUrlSet;
    }

    public String getNotifyFormat() {
        return notifyFormat;
    }

    public void setNotifyFormat(String notifyFormat) {
        this.notifyFormat = notifyFormat;
    }

    public Double getNotifyMinScoreDrop() {
        return notifyMinScoreDrop;
    }

    public void setNotifyMinScoreDrop(Double notifyMinScoreDrop) {
        this.notifyMinScoreDrop = notifyMinScoreDrop;
    }

    public String getNotifyMinSeverity() {
        return notifyMinSeverity;
    }

    public void setNotifyMinSeverity(String notifyMinSeverity) {
        this.notifyMinSeverity = notifyMinSeverity;
    }

    public String getNotifyPublicUrl() {
        return notifyPublicUrl;
    }

    public void setNotifyPublicUrl(String notifyPublicUrl) {
        this.notifyPublicUrl = notifyPublicUrl;
    }

    public Boolean getWebhookSecretSet() {
        return webhookSecretSet;
    }

    public void setWebhookSecretSet(Boolean webhookSecretSet) {
        this.webhookSecretSet = webhookSecretSet;
    }

    public Boolean getGitTokenSet() {
        return gitTokenSet;
    }

    public void setGitTokenSet(Boolean gitTokenSet) {
        this.gitTokenSet = gitTokenSet;
    }

    public String getGitTokenHosts() {
        return gitTokenHosts;
    }

    public void setGitTokenHosts(String gitTokenHosts) {
        this.gitTokenHosts = gitTokenHosts;
    }

    public Double getScoreDelta() {
        return scoreDelta;
    }

    public void setScoreDelta(Double scoreDelta) {
        this.scoreDelta = scoreDelta;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SystemSummaryResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Stack getStack() {
        return stack;
    }

    public void setStack(Stack stack) {
        this.stack = stack;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public Double getLastAuditScore() {
        return lastAuditScore;
    }

    public void setLastAuditScore(Double lastAuditScore) {
        this.lastAuditScore = lastAuditScore;
    }

    public LocalDateTime getLastAuditDate() {
        return lastAuditDate;
    }

    public void setLastAuditDate(LocalDateTime lastAuditDate) {
        this.lastAuditDate = lastAuditDate;
    }

    public Long getOpenCritical() {
        return openCritical;
    }

    public void setOpenCritical(Long openCritical) {
        this.openCritical = openCritical;
    }

    public Long getOpenHigh() {
        return openHigh;
    }

    public void setOpenHigh(Long openHigh) {
        this.openHigh = openHigh;
    }

    public Integer getAuditIntervalHours() {
        return auditIntervalHours;
    }

    public void setAuditIntervalHours(Integer auditIntervalHours) {
        this.auditIntervalHours = auditIntervalHours;
    }

    public String getScheduledBranch() {
        return scheduledBranch;
    }

    public void setScheduledBranch(String scheduledBranch) {
        this.scheduledBranch = scheduledBranch;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public Boolean getAuditOnPush() {
        return auditOnPush;
    }

    public void setAuditOnPush(Boolean auditOnPush) {
        this.auditOnPush = auditOnPush;
    }

    public Integer getAuditCount() {
        return auditCount;
    }

    public void setAuditCount(Integer auditCount) {
        this.auditCount = auditCount;
    }
}
