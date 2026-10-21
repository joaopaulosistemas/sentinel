package com.sentinel.audit.resource;

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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/rules")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Rules", description = "Qualidade das regras de análise")
public class RuleResource {

    public record PlaygroundRequest(@NotNull String rule, @NotNull String code, @NotNull String extension) {}

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
    public Response playground(@Valid PlaygroundRequest request) {
        try {
            return Response.ok(playground.run(request.rule(), request.code(), request.extension())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(java.util.Map.of("error", e.getMessage())).build();
        } catch (RulePlaygroundService.BusyException e) {
            return Response.status(429).entity(java.util.Map.of("error", e.getMessage())).build();
        } catch (RulePlaygroundService.UnavailableException e) {
            return Response.status(503).entity(java.util.Map.of("error", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(422).entity(java.util.Map.of("error", e.getMessage())).build();
        }
    }
}
