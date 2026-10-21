package com.sentinel.audit.dto;

import com.sentinel.audit.model.SuppressionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Pedido para retirar um finding do score, com justificativa obrigatória. */
public record SuppressRequest(
        @NotNull SuppressionReason reason,
        @NotBlank @Size(min = 10, max = 1000, message = "Explique o motivo em pelo menos 10 caracteres") String justification,
        @Size(max = 255) String createdBy,
        LocalDateTime expiresAt) {}
