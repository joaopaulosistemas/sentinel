package com.sentinel.audit.service;

/** O dado enviado não é aceito pela regra de negócio (vira 400 na API). */
public class InvalidInputException extends DomainException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
