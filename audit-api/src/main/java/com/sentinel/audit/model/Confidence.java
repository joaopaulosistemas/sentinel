package com.sentinel.audit.model;

/**
 * Quanto o Sentinel confia que o achado é um problema real. Não muda a severidade: um achado HIGH de baixa confiança
 * é "provavelmente barulho, mas se for real é grave".
 */
public enum Confidence {
    /** Corroborado por mais de uma ferramenta ou vulnerabilidade conhecida (CVE). */
    HIGH,
    /** Padrão: uma ferramenta apontou, sem sinais a favor nem contra. */
    MEDIUM,
    /** Sinais de falso positivo: fora do código de produção, valor de exemplo ou regra que o time costuma recusar. */
    LOW;

    /** Fração do peso mantida quando a confiança é baixa. */
    private static final double LOW_CONFIDENCE_WEIGHT = 0.4;

    /** Peso do achado na nota quando a ponderação por confiança está ligada. */
    public double scoreWeight() {
        return this == LOW ? LOW_CONFIDENCE_WEIGHT : 1.0;
    }
}
