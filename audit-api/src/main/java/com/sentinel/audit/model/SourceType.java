package com.sentinel.audit.model;

/** De onde o Sentinel lê o código de um sistema. */
public enum SourceType {
    /** Repositório Git remoto, por URL https:// (clonado a cada auditoria). */
    GIT_URL,
    /** Pasta local do servidor, dentro da raiz configurada em {@code SENTINEL_LOCAL_ROOT}. */
    LOCAL_PATH
}
