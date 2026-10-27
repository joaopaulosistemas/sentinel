package com.sentinel.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedido de verificação de uma pasta local: o caminho (relativo à raiz do servidor) e, opcionalmente, a branch. */
public record PathCheckRequest(@NotBlank @Size(max = 500) String path, @Size(max = 255) String branch) {
}
