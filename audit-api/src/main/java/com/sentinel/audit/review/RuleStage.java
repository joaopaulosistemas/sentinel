package com.sentinel.audit.review;

/** Etapas do funil de uma regra proposta. Só a partir de {@link #OBSERVING} a regra roda nas auditorias. */
public enum RuleStage {
    PROPOSED("Proposta"),
    REVIEWED("Revisada por uma pessoa"),
    SAMPLE_PASSED("Passou no teste de amostra"),
    OBSERVING("Em observação (não pesa na nota)"),
    PROMOTED("Promovida"),
    DISCARDED("Descartada");

    private final String label;

    RuleStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean terminal() {
        return this == PROMOTED || this == DISCARDED;
    }

    /** A regra entra no Semgrep nas auditorias do sistema. */
    public boolean runs() {
        return this == OBSERVING || this == PROMOTED;
    }
}
