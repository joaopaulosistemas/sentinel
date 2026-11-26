package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
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
        @Schema(description = "Identificador do finding.") Long id,
        @Schema(description = "Categoria do achado.") Category category,
        @Schema(description = "Severidade.") Severity severity,
        @Schema(description = "Título curto do achado.") String title,
        @Schema(description = "Explicação do problema.") String description,
        @Schema(description = "Arquivo onde foi encontrado.") String filePath,
        @Schema(description = "Linha no arquivo.") Integer lineNumber,
        @Schema(description = "Trecho de código do achado.") String codeSnippet,
        @Schema(description = "Regra que gerou o achado.") String ruleId,
        @Schema(description = "Analisador que gerou o achado.") String toolSource,
        @Schema(description = "CVE associada, quando é vulnerabilidade de dependência.") String cveId,
        @Schema(description = "Recomendação de correção.") String aiRecommendation,
        @Schema(description = "Situação do finding (por exemplo, suprimido).") FindingStatus status,
        @Schema(description = "Quando o finding foi registrado.") LocalDateTime createdAt,
        @Schema(description = "Impressão digital estável, usada para reconhecer o mesmo achado entre auditorias.") String fingerprint,
        @Schema(description = "Verdadeiro quando apareceu pela primeira vez nesta auditoria.") Boolean isNew,
        @Schema(description = "Verdadeiro quando a regra está em observação e não pesa na nota.") Boolean shadow,
        @Schema(description = "Como corrigir, quando há orientação.") RemediationDto remediation,
        @Schema(description = "Confiança do achado.") Confidence confidence,
        @Schema(description = "Por que a confiança é essa.") String confidenceReason,
        @Schema(description = "Característica da ISO/IEC 25010 que o achado afeta (security, maintainability, reliability, performance-efficiency).") String iso25010,
        @Schema(description = "Linhas reais do arquivo ao redor do achado, lidas na auditoria; ausente em segredos e quando o arquivo não pôde ser lido.") String codeContext,
        @Schema(description = "Número da primeira linha de codeContext no arquivo.") Integer codeContextStart) {

    /** Forma anterior, sem o contexto do código (fica nulo). */
    public FindingResponse(Long id, Category category, Severity severity, String title, String description, String filePath,
                           Integer lineNumber, String codeSnippet, String ruleId, String toolSource, String cveId,
                           String aiRecommendation, FindingStatus status, LocalDateTime createdAt, String fingerprint,
                           Boolean isNew, Boolean shadow, RemediationDto remediation, Confidence confidence,
                           String confidenceReason, String iso25010) {
        this(id, category, severity, title, description, filePath, lineNumber, codeSnippet, ruleId, toolSource, cveId,
                aiRecommendation, status, createdAt, fingerprint, isNew, shadow, remediation, confidence, confidenceReason,
                iso25010, null, null);
    }

    /** Forma anterior, sem a característica ISO (fica nula). */
    public FindingResponse(Long id, Category category, Severity severity, String title, String description, String filePath,
                           Integer lineNumber, String codeSnippet, String ruleId, String toolSource, String cveId,
                           String aiRecommendation, FindingStatus status, LocalDateTime createdAt, String fingerprint,
                           Boolean isNew, Boolean shadow, RemediationDto remediation, Confidence confidence,
                           String confidenceReason) {
        this(id, category, severity, title, description, filePath, lineNumber, codeSnippet, ruleId, toolSource, cveId,
                aiRecommendation, status, createdAt, fingerprint, isNew, shadow, remediation, confidence, confidenceReason, null, null, null);
    }
}
