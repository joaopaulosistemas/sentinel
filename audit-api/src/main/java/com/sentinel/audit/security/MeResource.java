package com.sentinel.audit.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Quem é o usuário da chamada e o que ele pode fazer; o painel usa para mostrar o nome e esconder o que não é dele. */
// Acesso: ApiKeyFilter (API_KEY) ou login OIDC protegem /api/*; qualquer usuário autenticado lê o próprio perfil.
@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Sessão", description = "Usuário autenticado e suas permissões")
public class MeResource {

    /** Resposta: {@code authMode} é oidc, api-key ou open. */
    public record Me(String authMode, String username, List<String> roles, boolean admin, boolean canExecute) {
    }

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    boolean oidcEnabled;

    @ConfigProperty(name = "sentinel.audit.api-key")
    Optional<String> apiKey;

    @Inject
    Instance<SecurityIdentity> identities;

    @GET
    @Operation(summary = "Usuário atual", description = "Nome, papéis e permissões de quem fez a chamada (login corporativo, API key ou API aberta)")
    @APIResponse(responseCode = "200", description = "Perfil do usuário autenticado")
    public Me me() {
        if (oidcEnabled) {
            SecurityIdentity identity = identities.get();
            Set<String> roles = new TreeSet<>(identity.getRoles());
            return of("oidc", identity.getPrincipal().getName(), roles);
        }
        boolean comChave = apiKey.isPresent() && !apiKey.get().isBlank();
        // Com a chave global (ou sem proteção) quem chama tem acesso total, como sempre foi.
        return of(comChave ? "api-key" : "open", comChave ? "api-key" : "anonimo", Set.of(RolePolicy.ADMIN));
    }

    static Me of(String mode, String username, Set<String> roles) {
        boolean admin = roles.contains(RolePolicy.ADMIN);
        boolean executa = admin || roles.contains(RolePolicy.EXECUTOR);
        return new Me(mode, username, List.copyOf(new TreeSet<>(roles)), admin, executa);
    }
}
