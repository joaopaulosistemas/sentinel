package com.sentinel.audit.resource;

import jakarta.annotation.security.PermitAll;
import com.sentinel.audit.dto.CreateSystemRequest;
import com.sentinel.audit.dto.ProfileRequest;
import com.sentinel.audit.dto.ScheduleRequest;
import com.sentinel.audit.dto.SystemSummaryResponse;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Endpoint REST para gerenciamento de sistemas auditados.
 */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/systems")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Systems", description = "Gerenciamento de sistemas auditados")
public class SystemResource {


    @Inject
    SystemCatalog systemCatalog;

    @Inject
    SystemViews views;

    @Inject
    SystemCleanup cleanup;

    @Inject
    SystemWriter writer;

    /**
     * Lista todos os sistemas cadastrados com um resumo da última auditoria.
     */
    @GET
    @Operation(summary = "Listar sistemas", description = "Retorna todos os sistemas cadastrados com resumo da última auditoria")
    public List<SystemSummaryResponse> listSystems() {
        return systemCatalog.all().stream()
                .map(views::summary)
                .collect(Collectors.toList());
    }

    /**
     * Cadastra um novo sistema para auditoria.
     */
    @POST
    @Transactional
    @Operation(summary = "Criar sistema", description = "Cadastra um novo sistema para auditoria")
    public Response createSystem(@Valid CreateSystemRequest request) {
        AuditedSystem system = new AuditedSystem();
        writer.apply(system, request, true);
        systemCatalog.add(system);
        return Response.status(Response.Status.CREATED).entity(views.summary(system)).build();
    }

    /**
     * Atualiza os dados cadastrais do sistema. O agendamento e o perfil têm endpoints próprios.
     */
    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Atualizar sistema", description = "Altera nome, descrição, repositório, stack, equipe e (se informado) o perfil de regras")
    public SystemSummaryResponse updateSystem(@PathParam("id") Long id, @Valid CreateSystemRequest request) {
        AuditedSystem system = systemCatalog.require(id);
        writer.apply(system, request, false);
        return views.summary(system);
    }

    /**
     * Busca um sistema específico pelo ID.
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar sistema", description = "Retorna os dados de um sistema específico")
    public SystemSummaryResponse getSystem(@PathParam("id") Long id) {
        AuditedSystem system = systemCatalog.require(id);
        return views.summary(system);
    }

    /**
     * Remove um sistema e todas as suas auditorias.
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Remover sistema", description = "Remove um sistema e todas as suas auditorias")
    public Response deleteSystem(@PathParam("id") Long id) {
        AuditedSystem system = systemCatalog.require(id);
        cleanup.purgeRelated(id);
        systemCatalog.remove(system);
        return Response.noContent().build();
    }

    /**
     * Configura a auditoria automática do sistema (periódica e/ou a cada push).
     */
    @PUT
    @Path("/{id}/schedule")
    @Transactional
    @Operation(summary = "Configurar auditoria automática",
            description = "Intervalo em horas (0 desliga), branch auditada e auditoria a cada push")
    public SystemSummaryResponse updateSchedule(@PathParam("id") Long id, @Valid ScheduleRequest request) {
        AuditedSystem system = systemCatalog.require(id);
        Integer hours = request.auditIntervalHours();
        system.setAuditIntervalHours(hours == null || hours <= 0 ? null : hours);
        system.setScheduledBranch(request.branch() == null || request.branch().isBlank() ? null : request.branch().trim());
        system.setAuditOnPush(Boolean.TRUE.equals(request.auditOnPush()));
        return views.summary(system);
    }

    /**
     * Troca o perfil de regras do sistema.
     */
    @PUT
    @Path("/{id}/profile")
    @Transactional
    @Operation(summary = "Definir perfil de regras",
            description = "Escolhe o perfil (pacotes ativos, sobrescritas e limites) usado nas próximas auditorias; vazio = default")
    public SystemSummaryResponse updateProfile(@PathParam("id") Long id, @Valid ProfileRequest request) {
        AuditedSystem system = systemCatalog.require(id);
        system.setProfile(writer.validProfile(request == null ? null : request.profile()));
        return views.summary(system);
    }
}
