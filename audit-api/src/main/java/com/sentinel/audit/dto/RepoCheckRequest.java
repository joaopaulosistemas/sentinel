package com.sentinel.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de verificação de um repositório: endereço e, opcionalmente, a branch que se pretende auditar.
 * Para repositório privado, vale o token digitado; sem ele, o salvo no sistema ({@code systemId}).
 */
public record RepoCheckRequest(@NotBlank @Size(max = RepoCheckRequest.MAX_TEXT) String repoUrl,
                               @Size(max = RepoCheckRequest.MAX_BRANCH) String branch, Long systemId,
                               @Size(max = RepoCheckRequest.MAX_TEXT) String gitToken,
                               @Size(max = RepoCheckRequest.MAX_TEXT) String gitTokenHosts) {
    static final int MAX_TEXT = 500;
    static final int MAX_BRANCH = 255;

    public RepoCheckRequest(String repoUrl, String branch) {
        this(repoUrl, branch, null, null, null);
    }
}
