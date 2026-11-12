package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedido de verificação de uma pasta local: o caminho (relativo à raiz do servidor) e, opcionalmente, a branch. */
public record PathCheckRequest(@Schema(description = "Pasta a verificar, relativa à raiz do servidor.", example = "projetos/pagamentos") @NotBlank @Size(max = RepoCheckRequest.MAX_TEXT) String path,
                               @Schema(description = "Branch que se pretende auditar.", example = "main") @Size(max = RepoCheckRequest.MAX_BRANCH) String branch) {
}
