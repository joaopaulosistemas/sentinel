package com.sentinel.audit.service;

import com.sentinel.audit.dto.CreateSystemRequest;
import com.sentinel.audit.dto.ScheduleRequest;
import com.sentinel.audit.model.AuditedSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Casos de uso do cadastro de sistemas, sem banco nem HTTP: o catálogo, o gravador e a limpeza são substituídos. */
class SystemRegistrationTest {

    private final List<String> events = new ArrayList<>();
    private AuditedSystem stored;
    private SystemRegistration registration;

    @BeforeEach
    void setUp() {
        stored = new AuditedSystem();
        stored.setName("Pagamentos");
        events.clear();

        registration = new SystemRegistration();
        registration.catalog = new SystemCatalog() {
            @Override
            public AuditedSystem require(Long id) {
                if (id != 1L) {
                    throw new ResourceNotFoundException("Sistema não encontrado: " + id);
                }
                return stored;
            }

            @Override
            public void add(AuditedSystem system) {
                events.add("add");
            }

            @Override
            public void remove(AuditedSystem system) {
                events.add("remove");
            }
        };
        registration.writer = new SystemWriter() {
            @Override
            public void apply(AuditedSystem system, CreateSystemRequest request, boolean create) {
                events.add("apply:" + create);
            }

            @Override
            public String validProfile(String profile) {
                if ("inexistente".equals(profile)) {
                    throw new InvalidInputException("Perfil não encontrado: " + profile);
                }
                return profile == null || profile.isBlank() ? null : profile.trim();
            }
        };
        registration.cleanup = new SystemCleanup() {
            @Override
            public void purgeRelated(Long systemId) {
                events.add("purge:" + systemId);
            }
        };
    }

    @Test
    void criarGravaOsDadosEDepoisPersiste() {
        AuditedSystem created = registration.create(null);

        assertEquals(List.of("apply:true", "add"), events);
        assertNotNull(created);
    }

    @Test
    void editarGravaSemForcarPerfilEDevolveOSistemaDoCatalogo() {
        AuditedSystem updated = registration.update(1L, null);

        assertSame(stored, updated);
        assertEquals(List.of("apply:false"), events);
    }

    @Test
    void removerLimpaOQueOutrosModulosGuardamAntesDeRemoverOSistema() {
        registration.delete(1L);

        assertEquals(List.of("purge:1", "remove"), events);
    }

    @Test
    void sistemaInexistenteNaoChegaAGravarNemRemover() {
        assertThrows(ResourceNotFoundException.class, () -> registration.update(9L, null));
        assertThrows(ResourceNotFoundException.class, () -> registration.delete(9L));
        assertThrows(ResourceNotFoundException.class, () -> registration.updateSchedule(9L, new ScheduleRequest(1, null, null)));
        assertTrue(events.isEmpty());
    }

    @Test
    void agendamentoComIntervaloZeroOuVazioDesliga() {
        registration.updateSchedule(1L, new ScheduleRequest(24, " develop ", true));
        assertEquals(24, stored.getAuditIntervalHours());
        assertEquals("develop", stored.getScheduledBranch());
        assertTrue(stored.getAuditOnPush());

        registration.updateSchedule(1L, new ScheduleRequest(0, "  ", null));
        assertNull(stored.getAuditIntervalHours());
        assertNull(stored.getScheduledBranch());
        assertEquals(false, stored.getAuditOnPush());

        registration.updateSchedule(1L, new ScheduleRequest(null, null, false));
        assertNull(stored.getAuditIntervalHours());
    }

    @Test
    void perfilValidoEGravadoEVazioVoltaAoPadrao() {
        registration.updateProfile(1L, " seguranca ");
        assertEquals("seguranca", stored.getProfile());

        registration.updateProfile(1L, "");
        assertNull(stored.getProfile());
    }

    @Test
    void perfilDesconhecidoEErroDeEntradaENaoMudaOSistema() {
        registration.updateProfile(1L, "seguranca");

        assertThrows(InvalidInputException.class, () -> registration.updateProfile(1L, "inexistente"));
        assertEquals("seguranca", stored.getProfile());
    }
}
