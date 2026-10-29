package com.sentinel.audit.dto;

import com.sentinel.audit.model.Confidence;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.pack.RemediationCatalog;

/** Converte a entidade {@link Finding} no DTO de resposta (inclui a orientação de correção). */
public final class FindingMapper {

    private FindingMapper() {}

    public static FindingResponse toResponse(Finding f) {
        return new FindingResponse(f.getId(), f.getCategory(), f.getSeverity(), f.getTitle(), f.getDescription(),
                f.getFilePath(), f.getLineNumber(), f.getCodeSnippet(), f.getRuleId(), f.getToolSource(), f.getCveId(),
                f.getAiRecommendation(), f.getStatus(), f.getCreatedAt(), f.getFingerprint(), f.getIsNew(),
                Boolean.TRUE.equals(f.getShadow()), RemediationCatalog.forFinding(f),
                f.getConfidence() == null ? Confidence.MEDIUM : f.getConfidence(), f.getConfidenceReason());
    }
}
