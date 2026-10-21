package com.sentinel.audit.dto;

import com.sentinel.audit.model.FindingSuppression;
import com.sentinel.audit.model.SuppressionReason;

import java.time.LocalDateTime;

public record SuppressionResponse(Long id, String fingerprint, SuppressionReason reason, String justification,
                                  String createdBy, LocalDateTime createdAt, LocalDateTime expiresAt,
                                  boolean active, String ruleId, String filePath, String title) {

    public static SuppressionResponse from(FindingSuppression s, LocalDateTime now) {
        return new SuppressionResponse(s.getId(), s.getFingerprint(), s.getReason(), s.getJustification(),
                s.getCreatedBy(), s.getCreatedAt(), s.getExpiresAt(), s.isActive(now),
                s.getRuleId(), s.getFilePath(), s.getTitle());
    }
}
