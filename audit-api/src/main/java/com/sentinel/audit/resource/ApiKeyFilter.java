package com.sentinel.audit.resource;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Protege a API com uma chave estática quando {@code sentinel.audit.api-key} (env {@code API_KEY}) está definida.
 * <p>
 * A chave é aceita no header {@code X-API-Key} ou como {@code Authorization: Bearer <chave>}.
 * O endpoint de health check é sempre público. Sem chave configurada, a API fica aberta
 * (adequado apenas para desenvolvimento local): o log de inicialização avisa quando isso ocorre.
 * <p>
 * Em produção prefira OIDC/SSO (quarkus-oidc); esta chave é uma proteção mínima, não um sistema de usuários.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyFilter implements ContainerRequestFilter {

    private static final String HEALTH_PATH = "api/v1/health";
    /** Webhooks se autenticam por assinatura HMAC / token próprio (WEBHOOK_SECRET), não pela API key. */
    private static final String WEBHOOK_PATH = "api/v1/webhooks/";

    @ConfigProperty(name = "sentinel.audit.api-key")
    Optional<String> apiKey;

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (apiKey.isEmpty() || apiKey.get().isBlank()) {
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(ctx.getMethod())) {
            return; // preflight de CORS
        }
        String path = ctx.getUriInfo().getPath();
        if (path != null) {
            String normalized = path.replaceAll("^/+", "");
            if (normalized.startsWith(HEALTH_PATH) || normalized.startsWith(WEBHOOK_PATH)) {
                return;
            }
        }

        String provided = ctx.getHeaderString("X-API-Key");
        if (provided == null) {
            String auth = ctx.getHeaderString("Authorization");
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                provided = auth.substring(7).trim();
            }
        }

        if (provided == null || !constantTimeEquals(provided, apiKey.get())) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Bearer")
                    .entity("{\"error\":\"API key ausente ou inválida\"}")
                    .type("application/json")
                    .build());
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
