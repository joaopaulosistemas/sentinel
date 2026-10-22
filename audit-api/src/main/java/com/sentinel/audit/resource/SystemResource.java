package com.sentinel.audit.resource;

import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.CreateSystemRequest;
import com.sentinel.audit.dto.ProfileRequest;
import com.sentinel.audit.dto.ScheduleRequest;
import com.sentinel.audit.dto.RiskScoreDto;
import com.sentinel.audit.dto.SystemSummaryResponse;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.pack.PackRegistry;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import com.sentinel.audit.repository.FindingRepository;
import com.sentinel.audit.repository.FindingSuppressionRepository;
import com.sentinel.audit.service.FindingLifecycleService;
import com.sentinel.audit.dto.SuppressionResponse;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.service.AuditProgressTracker;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Endpoint REST para gerenciamento de sistemas auditados.
 */
@Path("/api/v1/systems")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Systems", description = "Gerenciamento de sistemas auditados")
public class SystemResource {

    @Inject
    AuditProgressTracker progressTracker;

    @Inject
    PackRegistry packRegistry;

    @Inject
    AuditedSystemRepository systemRepository;

    @Inject
    AuditRunRepository auditRunRepository;

    @Inject
    FindingRepository findingRepository;

    @Inject
    FindingLifecycleService lifecycleService;

    @Inject
    FindingSuppressionRepository suppressionRepository;

    /**
     * Lista todos os sistemas cadastrados com um resumo da última auditoria.
     */
    @GET
    @Operation(summary = "Listar sistemas", description = "Retorna todos os sistemas cadastrados com resumo da última auditoria")
    public List<SystemSummaryResponse> listSystems() {
        return systemRepository.listAll().stream()
                .map(this::toSummary)
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
        system.setName(request.getName());
        system.setDescription(request.getDescription());
        system.setRepoUrl(request.getRepoUrl());
        system.setStack(request.getStack());
        system.setTeam(request.getTeam());
        system.setProfile(validProfile(request.getProfile()));
        systemRepository.persist(system);
        return Response.status(Response.Status.CREATED).entity(toSummary(system)).build();
    }

    /**
     * Busca um sistema específico pelo ID.
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar sistema", description = "Retorna os dados de um sistema específico")
    public SystemSummaryResponse getSystem(@PathParam("id") Long id) {
        AuditedSystem system = systemRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + id));
        return toSummary(system);
    }

    /**
     * Remove um sistema e todas as suas auditorias.
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Remover sistema", description = "Remove um sistema e todas as suas auditorias")
    public Response deleteSystem(@PathParam("id") Long id) {
        AuditedSystem system = systemRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + id));
        // As supressões referenciam o sistema (FK): precisam sair antes.
        suppressionRepository.findBySystemId(id).forEach(suppressionRepository::delete);
        systemRepository.delete(system);
        return Response.noContent().build();
    }

    /**
     * Evolução do score do sistema ao longo das auditorias concluídas (mais antiga primeiro).
     */
    @GET
    @Path("/{id}/trend")
    @Operation(summary = "Tendência do score", description = "Score e contagem por severidade das últimas auditorias concluídas")
    public List<TrendPoint> trend(@PathParam("id") Long id, @QueryParam("limit") @DefaultValue("30") int limit) {
        systemRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + id));
        return lifecycleService.trend(id, limit);
    }

    /**
     * Supressões (falsos positivos / riscos aceitos) registradas para o sistema.
     */
    @GET
    @Path("/{id}/suppressions")
    @Operation(summary = "Listar supressões")
    public List<SuppressionResponse> suppressions(@PathParam("id") Long id) {
        systemRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + id));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return lifecycleService.listSuppressions(id).stream()
                .map(s -> SuppressionResponse.from(s, now))
                .collect(Collectors.toList());
    }

    /**
     * Remove uma supressão: o finding volta a contar no score.
     */
    @DELETE
    @Path("/{id}/suppressions/{suppressionId}")
    @Operation(summary = "Remover supressão")
    public Response removeSuppression(@PathParam("id") Long id, @PathParam("suppressionId") Long suppressionId) {
        lifecycleService.removeSuppression(id, suppressionId);
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
        AuditedSystem system = systemRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + id));
        Integer hours = request.auditIntervalHours();
        system.setAuditIntervalHours(hours == null || hours <= 0 ? null : hours);
        system.setScheduledBranch(request.branch() == null || request.branch().isBlank() ? null : request.branch().trim());
        system.setAuditOnPush(Boolean.TRUE.equals(request.auditOnPush()));
        return toSummary(system);
    }

    /**
     * Lista todas as execuções de auditoria de um sistema.
     */
    @GET
    @Path("/{id}/audits")
    @Operation(summary = "Listar auditorias do sistema", description = "Retorna todas as execuções de auditoria de um sistema")
    public List<AuditRunResponse> listAudits(@PathParam("id") Long id) {
        systemRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + id));

        return auditRunRepository.findBySystemId(id).stream()
                .map(this::toAuditRunResponse)
                .collect(Collectors.toList());
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
        AuditedSystem system = systemRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + id));
        system.setProfile(validProfile(request == null ? null : request.profile()));
        return toSummary(system);
    }

    /** Perfil conhecido (ou vazio = default); desconhecido é erro 400 para o erro de digitação aparecer na hora. */
    private String validProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return null;
        }
        if (!packRegistry.profileExists(profile)) {
            throw new BadRequestException("Perfil não encontrado: " + profile
                    + ". Perfis disponíveis: " + String.join(", ", packRegistry.snapshot().profiles().keySet()));
        }
        return profile.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private SystemSummaryResponse toSummary(AuditedSystem system) {
        SystemSummaryResponse dto = new SystemSummaryResponse();
        dto.setId(system.getId());
        dto.setName(system.getName());
        dto.setStack(system.getStack());
        dto.setTeam(system.getTeam());
        dto.setAuditIntervalHours(system.getAuditIntervalHours());
        dto.setScheduledBranch(system.effectiveScheduledBranch());
        dto.setAuditOnPush(Boolean.TRUE.equals(system.getAuditOnPush()));
        dto.setProfile(system.effectiveProfile());

        List<AuditRun> runs = auditRunRepository.findBySystemId(system.getId());
        runs.stream()
            .filter(r -> r.getStatus() == AuditStatus.COMPLETED)
            .findFirst()
            .ifPresent(lastRun -> {
                if (lastRun.getRiskScore() != null) {
                    dto.setLastAuditScore(lastRun.getRiskScore().getOverall());
                }
                dto.setLastAuditDate(lastRun.getCompletedAt());

                Map<Severity, Long> counts = findingRepository.countBySeverityForRun(lastRun.getId());
                dto.setOpenCritical(counts.getOrDefault(Severity.CRITICAL, 0L));
                dto.setOpenHigh(counts.getOrDefault(Severity.HIGH, 0L));
            });

        return dto;
    }

    private AuditRunResponse toAuditRunResponse(AuditRun run) {
        AuditRunResponse dto = new AuditRunResponse();
        dto.setId(run.getId());
        dto.setSystemName(run.getSystem().getName());
        dto.setBranch(run.getBranch());
        dto.setStatus(run.getStatus());
        dto.setStartedAt(run.getStartedAt());
        dto.setCompletedAt(run.getCompletedAt());
        dto.setErrorMessage(run.getErrorMessage());

        if (run.getRiskScore() != null) {
            dto.setRiskScore(new RiskScoreDto(
                    run.getRiskScore().getOverall(),
                    run.getRiskScore().getSecurity(),
                    run.getRiskScore().getQuality(),
                    run.getRiskScore().getArchitecture(),
                    run.getRiskScore().getDependencies(),
                    run.getRiskScore().getObservability(),
                    run.getRiskScore().getPerformance()
            ));
        }

        dto.setFindingsSummary(findingRepository.countBySeverityForRun(run.getId()));
        if (run.getStatus() == AuditStatus.RUNNING || run.getStatus() == AuditStatus.PENDING) {
            progressTracker.snapshot(run.getId()).ifPresent(dto::setProgress);
        }
        return dto;
    }
}
