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
 *
 * @param repoUrl Obrigatória (e https://) quando a origem é GIT_URL; vazia quando é LOCAL_PATH.
 * @param sourceType Ausente = GIT_URL (compatível com quem já usa a API).
 * @param localPath Pasta do projeto relativa à raiz do servidor. Obrigatória quando a origem é LOCAL_PATH.
 * @param localSnapshot O que auditar na pasta local. Ausente = COMMIT.
 * @param profile Perfil de regras (ver profiles/ nos pacotes). Vazio = "default".
 * @param gitToken Token Git de leitura. Vazio/ausente = mantém o que já está salvo. Nunca é devolvido pela API.
 * @param gitTokenHosts Hosts que recebem o token, separados por vírgula (padrão: github.com).
 * @param webhookSecret Segredo dos webhooks de push (GitHub "Secret" / GitLab "Secret token"). Vazio = mantém o salvo.
 * @param notifyUrl URL do webhook de notificação (Teams/Slack). Vazio = mantém a salva.
 * @param gitHistoryDepth Commits do histórico a baixar (vazio = 100; 0 ou 1 = clone raso, sem varrer o histórico).
 * @param clearGitToken Apaga o token salvo.
 */
public record CreateSystemRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 500) @Pattern(regexp = "^$|^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/[^\\s]+$", message = "repoUrl deve ser uma URL https:// de repositório Git") String repoUrl,
        SourceType sourceType,
        @Size(max = 500) String localPath,
        LocalSnapshot localSnapshot,
        @NotNull Stack stack,
        String team,
        @Size(max = 63) @Pattern(regexp = "^$|^[a-z0-9][a-z0-9-]{1,62}$", message = "perfil inválido (minúsculas, números e hífen)") String profile,
        @Size(max = 500) String gitToken,
        @Size(max = 500) @Pattern(regexp = "^$|^[A-Za-z0-9.-]+(\\s*,\\s*[A-Za-z0-9.-]+)*$", message = "hosts inválidos: use nomes separados por vírgula") String gitTokenHosts,
        @Size(max = 200) String webhookSecret,
        Boolean clearWebhookSecret,
        @Size(max = 800) String notifyUrl,
        Boolean clearNotifyUrl,
        @Pattern(regexp = "^$|^(?i)(slack|teams|generic)$", message = "formato deve ser slack, teams ou generic") String notifyFormat,
        @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") Double notifyMinScoreDrop,
        @Pattern(regexp = "^$|^(?i)(critical|high|medium|low|info)$", message = "severidade inválida") String notifyMinSeverity,
        @Size(max = 500) @Pattern(regexp = "^$|^https?://[^\\s]+$", message = "use um endereço http(s)://") String notifyPublicUrl,
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(5000) Integer gitHistoryDepth,
        Boolean clearGitToken) {

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
}
