package com.sentinel.audit.insights;

/**
 * Como cada analisador se saiu na auditoria: {@code OK}, {@code FALHOU} (exceção), {@code INDISPONIVEL}
 * (ferramenta ausente no ambiente), {@code SUBSTITUIDO} (outro analisador cobre o mesmo) ou {@code NAO_SUPORTADO}
 * (não se aplica à stack).
 */
public record AnalyzerRunInfo(String name, String status, int findings, long durationMs, String note) {

    public boolean ok() {
        return "OK".equals(status);
    }
}
