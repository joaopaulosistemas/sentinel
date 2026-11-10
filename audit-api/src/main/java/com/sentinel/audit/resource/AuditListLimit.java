package com.sentinel.audit.resource;

/** Limites da lista de auditorias de um sistema: sem eles, cada abertura da tela carregava e comparava o histórico inteiro. */
final class AuditListLimit {

    static final int MIN = 1;
    static final int MAX = 500;
    static final int DEFAULT = 100;
    /** Só as auditorias mais recentes recebem a comparação com a anterior (novos, resolvidos, variação da nota). */
    static final int WITH_DIFF = 10;

    private AuditListLimit() {
    }

    static int clamp(int requested) {
        return Math.max(MIN, Math.min(MAX, requested));
    }

    static boolean withDiff(int position) {
        return position < WITH_DIFF;
    }
}
