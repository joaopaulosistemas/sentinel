package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de verificação de um repositório: endereço e, opcionalmente, a branch que se pretende auditar.
 * Para repositório privado, vale o token digitado; sem ele, o salvo no sistema ({@code systemId}).
 */
public record RepoCheckRequest(@Schema(description = "Endereço do repositório a verificar.", example = "https://github.com/acme/pagamentos")
                               @NotBlank @Size(max = RepoCheckRequest.MAX_TEXT) String repoUrl,
                               @Schema(description = "Branch que se pretende auditar.", example = "main") @Size(max = RepoCheckRequest.MAX_BRANCH) String branch, @Schema(description = "Sistema cujo token salvo vale quando nenhum token é digitado.") Long systemId,
                               @Schema(description = "Token digitado para repositório privado.") @Size(max = RepoCheckRequest.MAX_TEXT) String gitToken,
                               @Schema(description = "Hosts que recebem o token, separados por vírgula.", example = "github.com") @Size(max = RepoCheckRequest.MAX_TEXT) String gitTokenHosts) {
    static final int MAX_TEXT = 500;
    static final int MAX_BRANCH = 255;

    public RepoCheckRequest(String repoUrl, String branch) {
        this(repoUrl, branch, null, null, null);
    }
}
