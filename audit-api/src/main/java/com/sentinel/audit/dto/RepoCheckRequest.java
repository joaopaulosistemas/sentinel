package com.sentinel.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de verificação de um repositório: endereço e, opcionalmente, a branch que se pretende auditar.
 * Para repositório privado, vale o token digitado; sem ele, o salvo no sistema ({@code systemId}).
 */
public record RepoCheckRequest(@NotBlank @Size(max = 500) String repoUrl, @Size(max = 255) String branch,
                               Long systemId, @Size(max = 500) String gitToken, @Size(max = 500) String gitTokenHosts) {
    public RepoCheckRequest(String repoUrl, String branch) {
        this(repoUrl, branch, null, null, null);
    }
}
