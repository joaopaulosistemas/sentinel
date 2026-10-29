package com.sentinel.audit.dto;

import com.sentinel.audit.model.Confidence;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.pack.RemediationCatalog;

/** Converte a entidade {@link Finding} no DTO de resposta (inclui a orientação de correção). */
public final class FindingMapper {

    private FindingMapper() {}

    public static FindingResponse toResponse(Finding f) {
        FindingResponse dto = new FindingResponse();
        dto.setId(f.getId());
        dto.setCategory(f.getCategory());
        dto.setSeverity(f.getSeverity());
        dto.setTitle(f.getTitle());
        dto.setDescription(f.getDescription());
        dto.setFilePath(f.getFilePath());
        dto.setLineNumber(f.getLineNumber());
        dto.setCodeSnippet(f.getCodeSnippet());
        dto.setRuleId(f.getRuleId());
        dto.setToolSource(f.getToolSource());
        dto.setCveId(f.getCveId());
        dto.setAiRecommendation(f.getAiRecommendation());
        dto.setStatus(f.getStatus());
        dto.setCreatedAt(f.getCreatedAt());
        dto.setFingerprint(f.getFingerprint());
        dto.setIsNew(f.getIsNew());
        dto.setShadow(Boolean.TRUE.equals(f.getShadow()));
        dto.setConfidence(f.getConfidence() == null ? Confidence.MEDIUM : f.getConfidence());
        dto.setConfidenceReason(f.getConfidenceReason());
        dto.setRemediation(RemediationCatalog.forFinding(f));
        return dto;
    }
}
