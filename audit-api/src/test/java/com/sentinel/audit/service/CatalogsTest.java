package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A regra "inexistente = 404 com mensagem padrão" que antes se repetia em cada endpoint. */
class CatalogsTest {

    @Test
    void sistemaInexistenteViraNotFoundComAMensagemPadrao() {
        SystemCatalog catalog = new SystemCatalog();
        catalog.repository = new AuditedSystemRepository() {
            @Override
            public Optional<AuditedSystem> findByIdOptional(Long id) {
                return id == 1L ? Optional.of(new AuditedSystem()) : Optional.empty();
            }
        };

        assertNotNull(catalog.require(1L));
        NotFoundException e = assertThrows(NotFoundException.class, () -> catalog.require(2L));
        assertEquals("Sistema não encontrado: 2", e.getMessage());
        assertThrows(NotFoundException.class, () -> catalog.requireExists(3L));
    }

    @Test
    void auditoriaInexistenteViraNotFoundComAMensagemPadrao() {
        RunCatalog catalog = new RunCatalog();
        catalog.repository = new AuditRunRepository() {
            @Override
            public Optional<AuditRun> findByIdOptional(Long id) {
                return id == 7L ? Optional.of(new AuditRun()) : Optional.empty();
            }
        };

        assertNotNull(catalog.require(7L));
        NotFoundException e = assertThrows(NotFoundException.class, () -> catalog.require(8L));
        assertEquals("Auditoria não encontrada: 8", e.getMessage());
        assertThrows(NotFoundException.class, () -> catalog.requireExists(9L));
    }
}
