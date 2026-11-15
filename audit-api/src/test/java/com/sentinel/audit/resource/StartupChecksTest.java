package com.sentinel.audit.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupChecksTest {

    @Test
    void recusaApiAbertaEmProducaoSemChaveNemOidc() {
        assertTrue(StartupChecks.deveRecusarApiAberta(true, false, false, true));
    }

    @Test
    void liberaQuandoHaChave() {
        assertFalse(StartupChecks.deveRecusarApiAberta(false, false, false, true));
    }

    @Test
    void liberaQuandoOidcEstaLigado() {
        assertFalse(StartupChecks.deveRecusarApiAberta(true, true, false, true));
    }

    @Test
    void liberaComOptInExplicito() {
        assertFalse(StartupChecks.deveRecusarApiAberta(true, false, true, true));
    }

    @Test
    void naoBloqueiaForaDeProducao() {
        assertFalse(StartupChecks.deveRecusarApiAberta(true, false, false, false));
    }
}
