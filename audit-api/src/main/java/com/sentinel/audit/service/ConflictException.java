package com.sentinel.audit.service;

/** O pedido conflita com o estado atual, por exemplo auditoria já em andamento (vira 409 na API). */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
