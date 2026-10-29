package com.sentinel.audit.pack;

import java.util.List;

/** Orientação de correção de um finding: o que fazer, exemplo antes/depois, referências, esforço e risco de regressão. */
public record RemediationDto(String summary, String before, String after, List<String> references,
                             String effort, String regressionRisk) {

    public RemediationDto(String summary, String before, String after, List<String> references) {
        this(summary, before, after, references, null, null);
    }
}
