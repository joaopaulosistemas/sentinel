package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.sentinel.audit.model.SuppressionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Suprime vários findings de uma vez com o mesmo motivo e justificativa. */
public record BatchSuppressRequest(
        @Schema(description = "Identificadores dos findings a suprimir (até 200).") @NotEmpty @Size(max = BatchSuppressRequest.MAX_IDS) List<@NotNull Long> findingIds,
        @Schema(description = "Motivo da supressão, igual para todos.") @NotNull SuppressionReason reason,
        @Schema(description = "Explicação do motivo, de 10 a 1000 caracteres.", example = "Código gerado; não é mantido à mão.") @NotBlank @Size(min = 10, max = 1000) String justification,
        @Schema(description = "Quem pediu a supressão.", example = "maria") @Size(max = 255) String createdBy,
        @Schema(description = "Quando a supressão deixa de valer. Ausente = não expira.", example = "2027-03-01T00:00:00") LocalDateTime expiresAt) {

    public static final int MAX_IDS = 200;

    public SuppressRequest toSingle() {
        return new SuppressRequest(reason, justification, createdBy, expiresAt);
    }
}
