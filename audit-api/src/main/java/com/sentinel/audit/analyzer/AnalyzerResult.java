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
 * @param infoNote     {@code null} ou um resumo do que o analisador fez (ex.: quais ferramentas rodaram e quais não);
 *                     aparece na tela de acompanhamento mesmo quando o resultado está OK, sem mudar o status
 */
public record AnalyzerResult(List<Finding> findings, String analyzerName, Duration duration, String partialNote, String infoNote) {

    public AnalyzerResult(List<Finding> findings, String analyzerName, Duration duration, String partialNote) {
        this(findings, analyzerName, duration, partialNote, null);
    }

    public AnalyzerResult(List<Finding> findings, String analyzerName, Duration duration) {
        this(findings, analyzerName, duration, null, null);
    }

    public boolean hasInfo() {
        return infoNote != null && !infoNote.isBlank();
    }

    public boolean partial() {
        return partialNote != null && !partialNote.isBlank();
    }
}
