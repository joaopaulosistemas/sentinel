package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.sentinel.audit.model.FindingSuppression;
import com.sentinel.audit.model.SuppressionReason;

import java.time.LocalDateTime;

public record SuppressionResponse(@Schema(description = "Identificador da supressão.") Long id, @Schema(description = "Impressão digital do finding suprimido.") String fingerprint, @Schema(description = "Motivo da supressão.") SuppressionReason reason, @Schema(description = "Explicação do motivo.") String justification,
                                  @Schema(description = "Quem pediu a supressão.") String createdBy, @Schema(description = "Quando foi criada.") LocalDateTime createdAt, @Schema(description = "Quando deixa de valer; ausente = não expira.") LocalDateTime expiresAt,
                                  @Schema(description = "Verdadeiro enquanto a supressão está valendo.") boolean active, @Schema(description = "Regra do finding suprimido.") String ruleId, @Schema(description = "Arquivo do finding suprimido.") String filePath, @Schema(description = "Título do finding suprimido.") String title) {

    public static SuppressionResponse from(FindingSuppression s, LocalDateTime now) {
        return new SuppressionResponse(s.getId(), s.getFingerprint(), s.getReason(), s.getJustification(),
                s.getCreatedBy(), s.getCreatedAt(), s.getExpiresAt(), s.isActive(now),
                s.getRuleId(), s.getFilePath(), s.getTitle());
    }
}
