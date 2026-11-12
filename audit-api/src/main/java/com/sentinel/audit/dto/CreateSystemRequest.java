package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
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
        @Schema(description = "Nome do sistema na lista e nos relatórios.", example = "Pagamentos") @NotBlank @Size(max = 255) String name,
        @Schema(description = "Texto livre sobre o sistema.") String description,
        @Schema(description = "Endereço https:// do repositório Git; obrigatório quando a origem é GIT_URL, vazio quando é LOCAL_PATH.", example = "https://github.com/acme/pagamentos") @Size(max = 500) @Pattern(regexp = "^$|^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/[^\\s]+$", message = "repoUrl deve ser uma URL https:// de repositório Git") String repoUrl,
        @Schema(description = "De onde vem o código. Ausente = GIT_URL.") SourceType sourceType,
        @Schema(description = "Pasta do projeto, relativa à raiz do servidor; obrigatória quando a origem é LOCAL_PATH.", example = "projetos/pagamentos") @Size(max = 500) String localPath,
        @Schema(description = "O que auditar na pasta local. Ausente = COMMIT.") LocalSnapshot localSnapshot,
        @Schema(description = "Tecnologia principal do sistema.") @NotNull Stack stack,
        @Schema(description = "Equipe responsável.", example = "Time Pagamentos") String team,
        @Schema(description = "Perfil de regras do sistema (minúsculas, números e hífen). Vazio = default.", example = "default") @Size(max = 63) @Pattern(regexp = "^$|^[a-z0-9][a-z0-9-]{1,62}$", message = "perfil inválido (minúsculas, números e hífen)") String profile,
        @Schema(description = "Token Git de leitura. Vazio ou ausente mantém o que já está salvo; a API nunca o devolve.") @Size(max = 500) String gitToken,
        @Schema(description = "Hosts que recebem o token, separados por vírgula. Padrão: github.com.", example = "github.com") @Size(max = 500) @Pattern(regexp = "^$|^[A-Za-z0-9.-]+(\\s*,\\s*[A-Za-z0-9.-]+)*$", message = "hosts inválidos: use nomes separados por vírgula") String gitTokenHosts,
        @Schema(description = "Segredo dos webhooks de push. Vazio mantém o salvo.") @Size(max = 200) String webhookSecret,
        @Schema(description = "true apaga o segredo de webhook salvo.") Boolean clearWebhookSecret,
        @Schema(description = "URL do webhook de notificação (Teams, Slack ou genérico). Vazio mantém a salva.") @Size(max = 800) String notifyUrl,
        @Schema(description = "true apaga a URL de notificação salva.") Boolean clearNotifyUrl,
        @Schema(description = "Formato da notificação: slack, teams ou generic.", example = "slack") @Pattern(regexp = "^$|^(?i)(slack|teams|generic)$", message = "formato deve ser slack, teams ou generic") String notifyFormat,
        @Schema(description = "Queda mínima de nota (0 a 100) para notificar.", example = "5") @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") Double notifyMinScoreDrop,
        @Schema(description = "Severidade mínima para notificar: critical, high, medium, low ou info.", example = "high") @Pattern(regexp = "^$|^(?i)(critical|high|medium|low|info)$", message = "severidade inválida") String notifyMinSeverity,
        @Schema(description = "Endereço http(s) público do Sentinel, usado nos links das notificações.", example = "https://sentinel.exemplo.com.br") @Size(max = 500) @Pattern(regexp = "^$|^https?://[^\\s]+$", message = "use um endereço http(s)://") String notifyPublicUrl,
        @Schema(description = "Commits do histórico a baixar (0 a 5000). Vazio = 100; 0 ou 1 = clone raso, sem varrer o histórico.", example = "100") @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(5000) Integer gitHistoryDepth,
        @Schema(description = "true apaga o token Git salvo.") Boolean clearGitToken) {

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
