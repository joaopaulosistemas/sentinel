package com.sentinel.audit.settings;

import io.quarkus.runtime.Quarkus;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Tela de Configurações: mostra as configurações da instalação, grava as que podem ser mudadas por aqui e reinicia
 * a API para valerem. As protegidas (banco, chaves, caminhos, execução de programas) aparecem só para leitura.
 */
@Path("/api/v1/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Configurações", description = "Configurações da instalação")
public class SettingsResource {

    private static final Logger LOG = Logger.getLogger(SettingsResource.class);
    private static final long RESTART_DELAY_MS = 800;

    public record SaveRequest(Map<String, String> values) {}

    @Inject
    SettingsService service;

    @GET
    @Operation(summary = "Configurações em uso, valores salvos e o que aguarda reinício")
    public SettingsService.SettingsView get() {
        return service.view();
    }

    @PUT
    @Operation(summary = "Salvar configurações", description = "Valor nulo volta a configuração para o ambiente. Vale após reiniciar a API.")
    public SettingsService.SettingsView put(SaveRequest body) {
        try {
            return service.save(body == null ? null : body.values());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @DELETE
    @Path("/{key}")
    @Operation(summary = "Voltar uma configuração para o valor do ambiente")
    public SettingsService.SettingsView delete(@PathParam("key") String key) {
        try {
            return service.resetOne(key);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/restart")
    @Operation(summary = "Reiniciar a API", description = "Encerra o processo; o Docker Compose e o Kubernetes sobem de novo.")
    public Response restart() {
        LOG.warn("Reinício da API pedido pela tela de Configurações");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(RESTART_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Quarkus.asyncExit();
        }, "settings-restart");
        t.setDaemon(true);
        t.start();
        return Response.accepted().build();
    }
}
