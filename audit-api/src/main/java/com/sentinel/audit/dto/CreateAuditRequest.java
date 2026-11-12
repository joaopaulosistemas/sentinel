package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para criação de uma nova execução de auditoria.
 */
public class CreateAuditRequest {

    @NotNull
    @Schema(description = "Sistema a auditar.", example = "1")
    private Long systemId;

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]*$",
             message = "branch inválida: use letras, números, '.', '_', '-' ou '/' e não comece com '-'")
    @Schema(description = "Branch a auditar.", example = "main")
    private String branch;

    /** Opcional: auditoria de PR. Só entram achados em arquivos alterados em relação a este branch base. */
    @Size(max = 255)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]*$",
             message = "baseRef inválida: use letras, números, '.', '_', '-' ou '/' e não comece com '-'")
    @Schema(description = "Branch base, para auditar um PR: só entram achados em arquivos alterados em relação a ela. Opcional.", example = "main")
    private String baseRef;

    @Size(max = 255)
    @Schema(description = "Quem pediu a auditoria; a identidade autenticada, quando há, tem prioridade. Ausente = dashboard.", example = "maria")
    private String triggeredBy;

    public CreateAuditRequest() {
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public void setBaseRef(String baseRef) {
        this.baseRef = baseRef;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }
}
