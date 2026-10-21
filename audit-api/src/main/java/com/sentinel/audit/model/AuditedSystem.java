package com.sentinel.audit.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade que representa um sistema de software cadastrado para auditoria.
 * Cada sistema pode ter múltiplas execuções de auditoria associadas.
 */
@Entity
@Table(name = "audited_system")
public class AuditedSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audited_system_seq")
    @SequenceGenerator(name = "audited_system_seq", sequenceName = "audited_system_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Stack stack;

    @Column(length = 255)
    private String team;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** A cada quantas horas auditar automaticamente; {@code null} ou 0 = desligado. */
    @Column(name = "audit_interval_hours")
    private Integer auditIntervalHours;

    /** Branch usada nas auditorias agendadas e disparada por push. Vazio = "main". */
    @Column(name = "scheduled_branch", length = 255)
    private String scheduledBranch;

    /** Auditar automaticamente a cada push na {@link #scheduledBranch} (via webhook do GitHub/GitLab). */
    @Column(name = "audit_on_push")
    private Boolean auditOnPush;

    /** Perfil de regras (pacotes, sobrescritas e limites). Vazio = "default". */
    @Column(name = "profile", length = 63)
    private String profile;

    @OneToMany(mappedBy = "system", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AuditRun> runs = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public AuditedSystem() {
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    /** Perfil usado nas auditorias: o configurado ou "default". */
    public String effectiveProfile() {
        return profile == null || profile.isBlank() ? "default" : profile;
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

    public Boolean getAuditOnPush() {
        return auditOnPush;
    }

    public void setAuditOnPush(Boolean auditOnPush) {
        this.auditOnPush = auditOnPush;
    }

    /** Branch efetiva das auditorias automáticas. */
    public String effectiveScheduledBranch() {
        return scheduledBranch == null || scheduledBranch.isBlank() ? "main" : scheduledBranch.trim();
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<AuditRun> getRuns() {
        return runs;
    }

    public void setRuns(List<AuditRun> runs) {
        this.runs = runs;
    }
}
