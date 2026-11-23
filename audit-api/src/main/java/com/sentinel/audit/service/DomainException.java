package com.sentinel.audit.service;

/**
 * Erro de regra de negócio, sem nada de HTTP. A borda REST ({@code DomainExceptionMapper}) converte cada subtipo no
 * status certo; o MCP e o CLI podem tratá-lo como quiserem. Os serviços lançam isto em vez de exceções do JAX-RS para
 * a camada de negócio não depender da camada web.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
