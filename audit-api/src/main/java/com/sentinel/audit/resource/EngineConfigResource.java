package com.sentinel.audit.resource;

import com.sentinel.audit.tuning.TuningJson;
import com.sentinel.audit.tuning.SystemTuningService;
import jakarta.annotation.security.PermitAll;
import com.fasterxml.jackson.databind.JsonNode;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.pack.SystemTuning;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;


/**
 * Ajustes do motor de regras de cada sistema, usados pelas abas "Motor de regras" e "Analisadores".
 * Tudo vale por cima do perfil do sistema e a partir da próxima auditoria; sem ajustes o sistema audita como o perfil.
 */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/engine-config/{id}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Motor", description = "Ajustes do motor de regras por sistema")
public class EngineConfigResource {

    @Inject
    SystemCatalog systemCatalog;
    @Inject
    SystemTuningService tuningService;
    @Inject
    EngineConfigViews views;

    @GET
    @Operation(summary = "Ajustes do sistema e o que vem do perfil")
    public EngineConfigViews.EngineConfigResponse get(@PathParam("id") Long id) {
        return views.view(systemCatalog.require(id), tuningService.load(id));
    }

    @PUT
    @Operation(summary = "Substituir os ajustes do sistema", description = "Vazio = voltar a ser igual ao perfil")
    public EngineConfigViews.EngineConfigResponse put(@PathParam("id") Long id, JsonNode body) {
        AuditedSystem system = systemCatalog.require(id);
        SystemTuning parsed;
        try {
            parsed = TuningJson.parse(body);
            views.validateAgainstRegistry(parsed);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
        return views.view(system, tuningService.save(id, parsed));
    }
}
