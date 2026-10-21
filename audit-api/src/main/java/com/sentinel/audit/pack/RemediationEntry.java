package com.sentinel.audit.pack;

import java.util.List;

/**
 * Orientação de correção de uma regra: vale para todo rule id que <i>contenha</i> {@code key}.
 *
 * @param effort         esforço estimado da correção: {@code S} (minutos), {@code M} (horas) ou {@code L} (dias); pode ser nulo
 * @param regressionRisk risco de regressão ao corrigir: {@code LOW}, {@code MEDIUM} ou {@code HIGH}; pode ser nulo
 */
public record RemediationEntry(String key, String summary, String before, String after, List<String> refs,
                               String effort, String regressionRisk) {

    public RemediationEntry(String key, String summary, String before, String after, List<String> refs) {
        this(key, summary, before, after, refs, null, null);
    }
}
