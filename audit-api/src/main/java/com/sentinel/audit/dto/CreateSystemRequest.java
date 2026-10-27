package com.sentinel.audit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.model.SourceType;
import com.sentinel.audit.model.Stack;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para criação de um novo sistema auditado.
 */
public class CreateSystemRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    /** Obrigatória (e https://) quando a origem é GIT_URL; vazia quando é LOCAL_PATH. */
    @Size(max = 500)
    @Pattern(regexp = "^$|^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/[^\\s]+$",
             message = "repoUrl deve ser uma URL https:// de repositório Git")
    private String repoUrl;

    /** Ausente = GIT_URL (compatível com quem já usa a API). */
    private SourceType sourceType;

    /** Pasta do projeto relativa à raiz do servidor. Obrigatória quando a origem é LOCAL_PATH. */
    @Size(max = 500)
    private String localPath;

    /** O que auditar na pasta local. Ausente = COMMIT. */
    private LocalSnapshot localSnapshot;

    @JsonIgnore
    @AssertTrue(message = "Informe o endereço do repositório (https://)")
    public boolean isRepoUrlPresentForGit() {
        return sourceType == SourceType.LOCAL_PATH || (repoUrl != null && !repoUrl.isBlank());
    }

    @JsonIgnore
    @AssertTrue(message = "Informe a pasta do projeto")
    public boolean isLocalPathPresentForLocal() {
        return sourceType != SourceType.LOCAL_PATH || (localPath != null && !localPath.isBlank());
    }

    @NotNull
    private Stack stack;

    private String team;

    /** Perfil de regras (ver profiles/ nos pacotes). Vazio = "default". */
    @Size(max = 63)
    @Pattern(regexp = "^$|^[a-z0-9][a-z0-9-]{1,62}$", message = "perfil inválido (minúsculas, números e hífen)")
    private String profile;

    /** Token Git de leitura. Vazio/ausente = mantém o que já está salvo. Nunca é devolvido pela API. */
    @Size(max = 500)
    private String gitToken;

    /** Hosts que recebem o token, separados por vírgula (padrão: github.com). */
    @Size(max = 500)
    @Pattern(regexp = "^$|^[A-Za-z0-9.-]+(\\s*,\\s*[A-Za-z0-9.-]+)*$", message = "hosts inválidos: use nomes separados por vírgula")
    private String gitTokenHosts;

    /** Segredo dos webhooks de push (GitHub "Secret" / GitLab "Secret token"). Vazio = mantém o salvo. */
    @Size(max = 200)
    private String webhookSecret;

    private Boolean clearWebhookSecret;

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public Boolean getClearWebhookSecret() {
        return clearWebhookSecret;
    }

    public void setClearWebhookSecret(Boolean clearWebhookSecret) {
        this.clearWebhookSecret = clearWebhookSecret;
    }

    /** URL do webhook de notificação (Teams/Slack). Vazio = mantém a salva. */
    @Size(max = 800)
    private String notifyUrl;

    private Boolean clearNotifyUrl;

    @Pattern(regexp = "^$|^(?i)(slack|teams|generic)$", message = "formato deve ser slack, teams ou generic")
    private String notifyFormat;

    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.DecimalMax("100")
    private Double notifyMinScoreDrop;

    @Pattern(regexp = "^$|^(?i)(critical|high|medium|low|info)$", message = "severidade inválida")
    private String notifyMinSeverity;

    @Size(max = 500)
    @Pattern(regexp = "^$|^https?://[^\\s]+$", message = "use um endereço http(s)://")
    private String notifyPublicUrl;

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public Boolean getClearNotifyUrl() {
        return clearNotifyUrl;
    }

    public void setClearNotifyUrl(Boolean clearNotifyUrl) {
        this.clearNotifyUrl = clearNotifyUrl;
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

    /** Commits do histórico a baixar (vazio = 100; 0 ou 1 = clone raso, sem varrer o histórico). */
    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(5000)
    private Integer gitHistoryDepth;

    public Integer getGitHistoryDepth() {
        return gitHistoryDepth;
    }

    public void setGitHistoryDepth(Integer gitHistoryDepth) {
        this.gitHistoryDepth = gitHistoryDepth;
    }

    /** Apaga o token salvo. */
    private Boolean clearGitToken;

    public CreateSystemRequest() {
    }

    public String getGitToken() {
        return gitToken;
    }

    public void setGitToken(String gitToken) {
        this.gitToken = gitToken;
    }

    public String getGitTokenHosts() {
        return gitTokenHosts;
    }

    public void setGitTokenHosts(String gitTokenHosts) {
        this.gitTokenHosts = gitTokenHosts;
    }

    public Boolean getClearGitToken() {
        return clearGitToken;
    }

    public void setClearGitToken(Boolean clearGitToken) {
        this.clearGitToken = clearGitToken;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
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

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
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
}
