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
 */
public record AnalyzerResult(List<Finding> findings, String analyzerName, Duration duration) {
}
