package com.sentinel.audit.resource;

import jakarta.annotation.security.PermitAll;
import com.sentinel.audit.service.RulePlaygroundService;
import com.sentinel.audit.service.RuleQualityService;
import com.sentinel.audit.service.RuleQualityService.RuleQuality;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/rules")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Rules", description = "Qualidade das regras de análise")
public class RuleResource {

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_UNPROCESSABLE = 422;


    public record PlaygroundRequest(
            @Schema(description = "Regra Semgrep em YAML.") @NotNull String rule,
            @Schema(description = "Trecho de código sobre o qual a regra é executada.") @NotNull String code,
            @Schema(description = "Extensão do arquivo do trecho, que define a linguagem.", example = ".java") @NotNull String extension) {}

    @Inject
    RuleQualityService qualityService;

    @Inject
    RulePlaygroundService playground;

    @GET
    @Path("/quality")
    @Transactional
    @Operation(summary = "Qualidade das regras",
            description = "Taxa de falso positivo por regra, com base nas supressões do time; marca as que merecem revisão")
    public List<RuleQuality> quality() {
        return qualityService.compute();
    }

    @POST
    @Path("/playground")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Laboratório de regras",
            description = "Valida uma regra Semgrep (mesmas checagens dos pacotes) e a executa sobre um trecho de código")
    @APIResponse(responseCode = "200", description = "Resultado da validação e da execução da regra",
                content = {@Content(mediaType = "application/json", schema = @Schema(type = SchemaType.OBJECT))})
    @APIResponse(responseCode = "400", description = "Regra ou trecho inválido",
                content = {@Content(mediaType = "application/json", schema = @Schema(ref = "ApiKeyError"))})
    @APIResponse(responseCode = "422", description = "A regra não pôde ser executada",
                content = {@Content(mediaType = "application/json", schema = @Schema(ref = "ApiKeyError"))})
    @APIResponse(responseCode = "429", description = "Já há um teste em andamento; tente de novo em instantes",
                content = {@Content(mediaType = "application/json", schema = @Schema(ref = "ApiKeyError"))})
    @APIResponse(responseCode = "503", description = "O motor de regras não está disponível",
                content = {@Content(mediaType = "application/json", schema = @Schema(ref = "ApiKeyError"))})
    public Response playground(@Valid PlaygroundRequest request) {
        try {
            return Response.ok(playground.run(request.rule(), request.code(), request.extension())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(java.util.Map.of("error", e.getMessage())).build();
        } catch (RulePlaygroundService.BusyException e) {
            return Response.status(HTTP_TOO_MANY_REQUESTS).entity(java.util.Map.of("error", e.getMessage())).build();
        } catch (RulePlaygroundService.UnavailableException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(java.util.Map.of("error", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(HTTP_UNPROCESSABLE).entity(java.util.Map.of("error", e.getMessage())).build();
        }
    }
}
