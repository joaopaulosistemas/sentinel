package com.sentinel.audit.dto;

import com.sentinel.audit.model.SuppressionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Suprime vários findings de uma vez com o mesmo motivo e justificativa. */
public record BatchSuppressRequest(
        @NotEmpty @Size(max = BatchSuppressRequest.MAX_IDS) List<@NotNull Long> findingIds,
        @NotNull SuppressionReason reason,
        @NotBlank @Size(min = 10, max = 1000) String justification,
        @Size(max = 255) String createdBy,
        LocalDateTime expiresAt) {

    public static final int MAX_IDS = 200;

    public SuppressRequest toSingle() {
        return new SuppressRequest(reason, justification, createdBy, expiresAt);
    }
}
