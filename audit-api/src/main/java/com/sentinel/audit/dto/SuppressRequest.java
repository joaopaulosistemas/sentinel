package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.sentinel.audit.model.SuppressionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Pedido para retirar um finding do score, com justificativa obrigatória. */
public record SuppressRequest(
        @Schema(description = "Motivo da supressão.") @NotNull SuppressionReason reason,
        @Schema(description = "Explicação do motivo, de 10 a 1000 caracteres.", example = "Falso positivo: o valor vem de uma constante de teste.") @NotBlank @Size(min = 10, max = 1000, message = "Explique o motivo em pelo menos 10 caracteres") String justification,
        @Schema(description = "Quem pediu a supressão.", example = "maria") @Size(max = 255) String createdBy,
        @Schema(description = "Quando a supressão deixa de valer. Ausente = não expira.", example = "2027-03-01T00:00:00") LocalDateTime expiresAt) {}
