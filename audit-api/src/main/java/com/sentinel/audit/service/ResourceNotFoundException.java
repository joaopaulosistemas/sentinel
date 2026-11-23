package com.sentinel.audit.service;

/** O sistema, a auditoria, o finding ou outro item pedido não existe (vira 404 na API). */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
