package com.sentinel.audit.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.service.WebhookService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Recebe webhooks de push. Estes endpoints não usam a API key: a autenticação é a assinatura do GitHub
 * (HMAC-SHA256) ou o token do GitLab, com o segredo em {@code WEBHOOK_SECRET}. Sem segredo configurado ficam desativados.
 */
@Path("/api/v1/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.WILDCARD)
@Tag(name = "Webhooks", description = "Auditoria automática a cada push")
public class WebhookResource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(name = "sentinel.audit.webhook.secret")
    Optional<String> secret;

    @Inject
    WebhookService webhookService;

    @POST
    @Path("/github")
    @Operation(summary = "Webhook de push do GitHub", description = "Content type application/json; segredo no campo Secret do webhook")
    public Response github(@HeaderParam("X-Hub-Signature-256") String signature,
                           @HeaderParam("X-GitHub-Event") String event, byte[] body) {
        if (disabled()) {
            return disabledResponse();
        }
        if (!WebhookService.validGithubSignature(secret.get(), body == null ? new byte[0] : body, signature)) {
            return unauthorized();
        }
        if ("ping".equals(event)) {
            return Response.ok(Map.of("message", "pong")).build();
        }
        if (event != null && !"push".equals(event)) {
            return Response.ok(Map.of("triggered", 0, "ignored", "evento " + event)).build();
        }
        return handle(body, "webhook-github");
    }

    @POST
    @Path("/gitlab")
    @Operation(summary = "Webhook de push do GitLab", description = "Segredo no campo Secret token do webhook")
    public Response gitlab(@HeaderParam("X-Gitlab-Token") String token, byte[] body) {
        if (disabled()) {
            return disabledResponse();
        }
        if (!WebhookService.validGitlabToken(secret.get(), token)) {
            return unauthorized();
        }
        return handle(body, "webhook-gitlab");
    }

    private Response handle(byte[] body, String source) {
        JsonNode payload;
        try {
            payload = MAPPER.readTree(new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", "JSON inválido")).build();
        }
        WebhookService.Push push = WebhookService.parsePush(payload);
        if (push == null) {
            return Response.ok(Map.of("triggered", 0, "ignored", "não é um push em branch")).build();
        }
        int started = webhookService.trigger(push, source);
        return Response.accepted(Map.of("triggered", started)).build();
    }

    private boolean disabled() {
        return secret.isEmpty() || secret.get().isBlank();
    }

    private static Response disabledResponse() {
        return Response.status(503).entity(Map.of("error", "Webhooks desativados: defina WEBHOOK_SECRET")).build();
    }

    private static Response unauthorized() {
        return Response.status(401).entity(Map.of("error", "Assinatura inválida")).build();
    }
}
