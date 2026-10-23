package com.sentinel.audit.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.service.WebhookService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Recebe webhooks de push. Estes endpoints não usam a API key: a autenticação é a assinatura do GitHub
 * (HMAC-SHA256) ou o token do GitLab, conferidos com o <b>segredo cadastrado no sistema</b> dono do repositório.
 * Sistema sem segredo não recebe webhook.
 */
@Path("/api/v1/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.WILDCARD)
@Tag(name = "Webhooks", description = "Auditoria automática a cada push")
public class WebhookResource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    WebhookService webhookService;

    @POST
    @Path("/github")
    @Operation(summary = "Webhook de push do GitHub", description = "Content type application/json; segredo no campo Secret do webhook, o mesmo cadastrado no sistema")
    public Response github(@HeaderParam("X-Hub-Signature-256") String signature,
                           @HeaderParam("X-GitHub-Event") String event, byte[] body) {
        byte[] bytes = body == null ? new byte[0] : body;
        return process(bytes, "webhook-github", event,
                s -> WebhookService.validGithubSignature(webhookService.secretOf(s), bytes, signature));
    }

    @POST
    @Path("/gitlab")
    @Operation(summary = "Webhook de push do GitLab", description = "Segredo no campo Secret token do webhook, o mesmo cadastrado no sistema")
    public Response gitlab(@HeaderParam("X-Gitlab-Token") String token, byte[] body) {
        return process(body == null ? new byte[0] : body, "webhook-gitlab", "push",
                s -> WebhookService.validGitlabToken(webhookService.secretOf(s), token));
    }

    private Response process(byte[] body, String source, String event, Predicate<AuditedSystem> authentic) {
        JsonNode payload;
        try {
            payload = MAPPER.readTree(new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", "JSON inválido")).build();
        }
        // Só quem tem o segredo de um sistema com este repositório é aceito; os demais recebem 401, sem pistas.
        List<AuditedSystem> valid = webhookService.withSecret(WebhookService.repoUrls(payload)).stream()
                .filter(authentic).toList();
        if (valid.isEmpty()) {
            return Response.status(401).entity(Map.of("error", "Assinatura inválida ou sistema sem segredo de webhook")).build();
        }
        if ("ping".equals(event)) {
            return Response.ok(Map.of("message", "pong")).build();
        }
        if (event != null && !"push".equals(event)) {
            return Response.ok(Map.of("triggered", 0, "ignored", "evento " + event)).build();
        }
        WebhookService.Push push = WebhookService.parsePush(payload);
        if (push == null) {
            return Response.ok(Map.of("triggered", 0, "ignored", "não é um push em branch")).build();
        }
        Set<Long> ids = valid.stream().map(AuditedSystem::getId).collect(Collectors.toSet());
        return Response.accepted(Map.of("triggered", webhookService.trigger(push, source, ids))).build();
    }
}
