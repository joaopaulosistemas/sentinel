package com.sentinel.audit.dto;

import com.sentinel.audit.pack.RemediationDto;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Confidence;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import java.time.LocalDateTime;

/**
 * DTO de resposta representando um finding de auditoria.
 */
public record FindingResponse(
        Long id,
        Category category,
        Severity severity,
        String title,
        String description,
        String filePath,
        Integer lineNumber,
        String codeSnippet,
        String ruleId,
        String toolSource,
        String cveId,
        String aiRecommendation,
        FindingStatus status,
        LocalDateTime createdAt,
        String fingerprint,
        Boolean isNew,
        Boolean shadow,
        RemediationDto remediation,
        Confidence confidence,
        String confidenceReason) {
}
