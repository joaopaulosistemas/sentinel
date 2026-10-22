package com.sentinel.audit.dto;

import java.util.List;

/** Resultado da supressão em lote: o que deu certo e o que falhou, por finding. */
public record BatchSuppressResponse(int requested, int suppressed, List<Failure> failures) {

    public record Failure(Long findingId, String message) {
    }
}
