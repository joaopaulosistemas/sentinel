package com.sentinel.audit.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Aplica {@link RolePolicy} depois que o Quarkus já validou o token (assinatura, emissor, validade e audiência).
 * Só age com o login corporativo ligado ({@code quarkus.oidc.tenant-enabled=true}); com API_KEY ou sem proteção
 * (desenvolvimento) não faz nada, como antes.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class RoleAuthorizationFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    boolean oidcEnabled;

    @Inject
    Instance<SecurityIdentity> identities;

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (!oidcEnabled || "OPTIONS".equalsIgnoreCase(ctx.getMethod())) {
            return;
        }
        RolePolicy.Need need = RolePolicy.need(ctx.getMethod(), ctx.getUriInfo().getPath());
        if (need == RolePolicy.Need.PUBLIC) {
            return;
        }
        SecurityIdentity identity = identities.isResolvable() ? identities.get() : null;
        if (identity == null || identity.isAnonymous()) {
            ctx.abortWith(resposta(Response.Status.UNAUTHORIZED, "Token ausente ou inválido"));
            return;
        }
        if (!RolePolicy.allows(need, identity.getRoles())) {
            ctx.abortWith(resposta(Response.Status.FORBIDDEN, mensagem(need)));
        }
    }

    private static String mensagem(RolePolicy.Need need) {
        return need == RolePolicy.Need.ADMIN
                ? "Sem permissão: esta ação é do papel sentinel-admin"
                : "Sem permissão: é preciso o papel sentinel-executor ou sentinel-admin";
    }

    private static Response resposta(Response.Status status, String message) {
        return Response.status(status).type("application/json")
                .entity("{\"message\":\"" + message + "\"}").build();
    }
}
