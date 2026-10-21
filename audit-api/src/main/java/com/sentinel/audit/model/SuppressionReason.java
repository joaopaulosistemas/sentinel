package com.sentinel.audit.model;

/** Motivo pelo qual um finding foi retirado do score. */
public enum SuppressionReason {
    /** A ferramenta errou: não é um problema. */
    FALSE_POSITIVE,
    /** É um problema real, mas o risco foi aceito conscientemente (com prazo, de preferência). */
    ACCEPTED_RISK,
    /** Não será corrigido (ex.: código legado em desativação). */
    WONT_FIX
}
