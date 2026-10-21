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
}
