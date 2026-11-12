package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

/** Resultado da supressão em lote: o que deu certo e o que falhou, por finding. */
public record BatchSuppressResponse(@Schema(description = "Quantos findings foram pedidos.") int requested, @Schema(description = "Quantos foram suprimidos.") int suppressed, @Schema(description = "Os que falharam, com o motivo.") List<Failure> failures) {

    public record Failure(@Schema(description = "Finding que falhou.") Long findingId, @Schema(description = "Motivo da falha.") String message) {
    }
}
