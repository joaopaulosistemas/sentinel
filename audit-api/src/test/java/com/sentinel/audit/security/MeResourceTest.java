package com.sentinel.audit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MeResourceTest {

    @Test
    void adminPodeTudo() {
        MeResource.Me me = MeResource.of("oidc", "admin", Set.of(RolePolicy.ADMIN));
        assertTrue(me.admin());
        assertTrue(me.canExecute());
    }

    @Test
    void executorExecutaMasNaoEAdmin() {
        MeResource.Me me = MeResource.of("oidc", "executor", Set.of(RolePolicy.EXECUTOR, "offline_access"));
        assertFalse(me.admin());
        assertTrue(me.canExecute());
        assertEquals(java.util.List.of("offline_access", "sentinel-executor"), me.roles());
    }

    @Test
    void semPapelDoSentinelNaoFazNada() {
        MeResource.Me me = MeResource.of("oidc", "visitante", Set.of("default-roles-sentinel"));
        assertFalse(me.admin());
        assertFalse(me.canExecute());
    }
}
