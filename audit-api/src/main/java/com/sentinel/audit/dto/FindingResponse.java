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
        @Schema(description = "Por que a confiança é essa.") String confidenceReason) {
}
