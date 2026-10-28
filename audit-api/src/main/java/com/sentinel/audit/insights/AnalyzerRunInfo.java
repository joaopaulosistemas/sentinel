package com.sentinel.audit.insights;

/**
 * Como cada analisador se saiu na auditoria: {@code OK}, {@code PARCIAL} (terminou, mas com tempo esgotado, saída anormal ou erros: resultado possivelmente incompleto),
 * {@code FALHOU} (exceção), {@code INDISPONIVEL}
 * (ferramenta ausente no ambiente), {@code SUBSTITUIDO} (outro analisador cobre o mesmo), {@code DESLIGADO} (ajuste do sistema) ou {@code NAO_SUPORTADO}
 * (não se aplica à stack).
 */
public record AnalyzerRunInfo(String name, String status, int findings, long durationMs, String note) {

    public boolean ok() {
        return "OK".equals(status);
    }

    public boolean partial() {
        return "PARCIAL".equals(status);
    }
}
