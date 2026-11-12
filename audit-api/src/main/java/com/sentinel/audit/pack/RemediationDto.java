package com.sentinel.audit.pack;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

/** Orientação de correção de um finding: o que fazer, exemplo antes/depois, referências, esforço e risco de regressão. */
public record RemediationDto(@Schema(description = "O que fazer.") String summary, @Schema(description = "Exemplo do problema.") String before, @Schema(description = "Exemplo corrigido.") String after, @Schema(description = "Referências.") List<String> references,
                             @Schema(description = "Esforço: S, M ou L.") String effort, @Schema(description = "Risco de regressão.") String regressionRisk) {

    public RemediationDto(String summary, String before, String after, List<String> references) {
        this(summary, before, after, references, null, null);
    }
}
