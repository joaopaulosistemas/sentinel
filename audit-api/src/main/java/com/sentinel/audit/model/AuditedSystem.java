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

    /** De onde vem o código. Nulo (sistemas antigos) vale {@link SourceType#GIT_URL}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20)
    private SourceType sourceType;

    /** Pasta do projeto, relativa à raiz {@code SENTINEL_LOCAL_ROOT}; só quando a origem é {@link SourceType#LOCAL_PATH}. */
    @Column(name = "local_path", length = 500)
    private String localPath;

    /** O que auditar na pasta local. Nulo vale {@link LocalSnapshot#COMMIT}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "local_snapshot", length = 20)
    private LocalSnapshot localSnapshot;

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

    /** Token Git de leitura deste sistema, cifrado (ver SecretBox). Nunca sai pela API. */
    @Column(name = "git_token_enc", length = 1000)
    private String gitTokenEnc;

    /** Hosts que podem receber o token, separados por vírgula; vazio = github.com. */
    @Column(name = "git_token_hosts", length = 500)
    private String gitTokenHosts;

    /** Commits baixados no clone (varredura de segredos no histórico); vazio = 100; 0 ou 1 = clone raso. */
    @Column(name = "git_history_depth")
    private Integer gitHistoryDepth;

    /** Segredo dos webhooks de push deste sistema, cifrado (ver SecretBox). Nunca sai pela API. */
    @Column(name = "webhook_secret_enc", length = 1000)
    private String webhookSecretEnc;

    /** Notificações (Teams/Slack/genérico). A URL do webhook é um segredo e fica cifrada (ver SecretBox). */
    @Column(name = "notify_url_enc", length = 1000)
    private String notifyUrlEnc;

    /** slack | teams | generic; vazio = slack. */
    @Column(name = "notify_format", length = 20)
    private String notifyFormat;

    /** Queda mínima de nota (pontos) que justifica aviso; vazio = 5. */
    @Column(name = "notify_min_score_drop")
    private Double notifyMinScoreDrop;

    /** Severidade mínima dos achados novos que justificam aviso; vazio = HIGH. */
    @Column(name = "notify_min_severity", length = 20)
    private String notifyMinSeverity;

    /** Endereço do dashboard, para o link da mensagem. */
    @Column(name = "notify_public_url", length = 500)
    private String notifyPublicUrl;

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

    public String getGitTokenEnc() {
        return gitTokenEnc;
    }

    public void setGitTokenEnc(String gitTokenEnc) {
        this.gitTokenEnc = gitTokenEnc;
    }

    public String getGitTokenHosts() {
        return gitTokenHosts;
    }

    public void setGitTokenHosts(String gitTokenHosts) {
        this.gitTokenHosts = gitTokenHosts;
    }

    public String getWebhookSecretEnc() {
        return webhookSecretEnc;
    }

    public void setWebhookSecretEnc(String webhookSecretEnc) {
        this.webhookSecretEnc = webhookSecretEnc;
    }

    public boolean hasWebhookSecret() {
        return webhookSecretEnc != null && !webhookSecretEnc.isBlank();
    }

    public String getNotifyUrlEnc() {
        return notifyUrlEnc;
    }

    public void setNotifyUrlEnc(String notifyUrlEnc) {
        this.notifyUrlEnc = notifyUrlEnc;
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

    public boolean hasNotifyUrl() {
        return notifyUrlEnc != null && !notifyUrlEnc.isBlank();
    }

    public Integer getGitHistoryDepth() {
        return gitHistoryDepth;
    }

    public void setGitHistoryDepth(Integer gitHistoryDepth) {
        this.gitHistoryDepth = gitHistoryDepth;
    }

    public boolean hasGitToken() {
        return gitTokenEnc != null && !gitTokenEnc.isBlank();
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

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public SourceType effectiveSourceType() {
        return sourceType == null ? SourceType.GIT_URL : sourceType;
    }

    public boolean isLocalSource() {
        return effectiveSourceType() == SourceType.LOCAL_PATH;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public LocalSnapshot getLocalSnapshot() {
        return localSnapshot;
    }

    public void setLocalSnapshot(LocalSnapshot localSnapshot) {
        this.localSnapshot = localSnapshot;
    }

    public LocalSnapshot effectiveLocalSnapshot() {
        return localSnapshot == null ? LocalSnapshot.COMMIT : localSnapshot;
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
