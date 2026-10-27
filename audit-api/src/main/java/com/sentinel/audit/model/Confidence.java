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

    /** Peso do achado na nota quando a ponderação por confiança está ligada. */
    public double scoreWeight() {
        return this == LOW ? 0.4 : 1.0;
    }
}
