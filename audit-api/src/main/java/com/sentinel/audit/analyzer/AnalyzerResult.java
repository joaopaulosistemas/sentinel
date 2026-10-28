package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.Finding;
import java.time.Duration;
import java.util.List;

/**
 * Resultado imutável produzido por um {@link CodeAnalyzer}.
 *
 * @param findings     lista de findings encontrados
 * @param analyzerName nome do analyzer que produziu o resultado
 * @param duration     tempo de execução da análise
 * @param partialNote  {@code null} quando a ferramenta terminou normalmente; senão o motivo pelo qual o resultado
 *                     pode estar incompleto (tempo esgotado, saída anormal, relatório ilegível). O orquestrador
 *                     mostra o analisador como PARCIAL em vez de OK, para "0 achados" não parecer "código limpo".
 */
public record AnalyzerResult(List<Finding> findings, String analyzerName, Duration duration, String partialNote) {

    public AnalyzerResult(List<Finding> findings, String analyzerName, Duration duration) {
        this(findings, analyzerName, duration, null);
    }

    public boolean partial() {
        return partialNote != null && !partialNote.isBlank();
    }
}
