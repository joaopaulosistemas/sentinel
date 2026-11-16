package com.sentinel.audit.security;

import static com.sentinel.audit.security.RolePolicy.ADMIN;
import static com.sentinel.audit.security.RolePolicy.EXECUTOR;
import static com.sentinel.audit.security.RolePolicy.Need;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RolePolicyTest {

    @Test
    void saudeWebhooksEForaDeApiSaoPublicos() {
        assertEquals(Need.PUBLIC, RolePolicy.need("GET", "/api/v1/health"));
        assertEquals(Need.PUBLIC, RolePolicy.need("POST", "api/v1/webhooks/github"));
        assertEquals(Need.PUBLIC, RolePolicy.need("POST", "/mcp/sistema"));
        assertEquals(Need.PUBLIC, RolePolicy.need("GET", "/q/openapi"));
    }

    @Test
    void qualquerUsuarioAutenticadoLeOProprioPerfil() {
        assertEquals(Need.ANY_AUTHENTICATED, RolePolicy.need("GET", "/api/v1/me"));
        assertTrue(RolePolicy.allows(Need.ANY_AUTHENTICATED, Set.of()));
    }

    @Test
    void leituraEExecucaoSaoDoExecutor() {
        assertEquals(Need.READ, RolePolicy.need("GET", "/api/v1/systems"));
        assertEquals(Need.EXECUTE, RolePolicy.need("POST", "/api/v1/audits"));
        assertEquals(Need.EXECUTE, RolePolicy.need("POST", "/api/v1/audits/7/cancel"));
        assertTrue(RolePolicy.allows(Need.READ, Set.of(EXECUTOR)));
        assertTrue(RolePolicy.allows(Need.EXECUTE, Set.of(EXECUTOR)));
    }

    @Test
    void cadastrarERegrasESupressoesSaoSoDoAdmin() {
        for (String[] r : new String[][] {{"POST", "/api/v1/systems"}, {"PUT", "/api/v1/systems/3/profile"},
                {"DELETE", "/api/v1/systems/3"}, {"POST", "/api/v1/findings/9/suppress"}, {"PUT", "/api/v1/rules/x"}}) {
            assertEquals(Need.ADMIN, RolePolicy.need(r[0], r[1]), r[0] + " " + r[1]);
        }
        assertFalse(RolePolicy.allows(Need.ADMIN, Set.of(EXECUTOR)));
        assertTrue(RolePolicy.allows(Need.ADMIN, Set.of(ADMIN)));
    }

    @Test
    void configuracoesSaoSoDoAdminAteParaLeitura() {
        assertEquals(Need.ADMIN, RolePolicy.need("GET", "/api/v1/settings"));
        assertEquals(Need.ADMIN, RolePolicy.need("PUT", "/api/v1/settings"));
        assertEquals(Need.ADMIN, RolePolicy.need("DELETE", "/api/v1/settings/CHAVE"));
    }

    @Test
    void semPapelNadaAlemDoProprioPerfil() {
        assertFalse(RolePolicy.allows(Need.READ, Set.of()));
        assertFalse(RolePolicy.allows(Need.EXECUTE, Set.of("outro")));
        assertFalse(RolePolicy.allows(Need.ADMIN, Set.of()));
    }

    @Test
    void adminPodeTudoQueOExecutorPode() {
        for (Need n : Need.values()) {
            if (RolePolicy.allows(n, Set.of(EXECUTOR))) {
                assertTrue(RolePolicy.allows(n, Set.of(ADMIN)), n.name());
            }
        }
    }

    @Test
    void caminhoComBarrasEConsultaENormalizado() {
        assertEquals(Need.ADMIN, RolePolicy.need("get", "//api/v1/settings/"));
        assertEquals(Need.READ, RolePolicy.need("get", "/api/v1/systems?x=1"));
    }
}
