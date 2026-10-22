package com.sentinel.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedido de verificação de um repositório: endereço e, opcionalmente, a branch que se pretende auditar. */
public record RepoCheckRequest(@NotBlank @Size(max = 500) String repoUrl, @Size(max = 255) String branch) {}
