package com.sentinel.audit.resource;

import com.sentinel.audit.service.AuditRunMapper;
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
import com.sentinel.audit.service.IngestionService;
import com.sentinel.audit.dto.RepoCheckRequest;
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
    AuditRunMapper runMapper;


    @Inject
    AuditProgressTracker progressTracker;

    @Inject
    PackRegistry packRegistry;

    @Inject
    IngestionService ingestionService;

    @Inject
    com.sentinel.audit.service.SecretBox secretBox;

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

    @Inject
    com.sentinel.audit.mcp.McpGateService mcpGate;

    @Inject
    com.sentinel.audit.tuning.SystemTuningService tuningService;

    @Inject
    com.sentinel.audit.review.ReviewService reviewService;

    @Inject
    com.sentinel.audit.actions.PendingActionService pendingActions;

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
        applyGitAccess(system, request);
        systemRepository.persist(system);
        return Response.status(Response.Status.CREATED).entity(toSummary(system)).build();
    }

    /**
     * Confere se o repositório responde e qual é a branch padrão, sem clonar.
     */
    @POST
    @Path("/check-repo")
    @Operation(summary = "Verificar repositório", description = "Testa o acesso ao repositório e descobre a branch padrão (git ls-remote), sem clonar")
    public IngestionService.RepoCheck checkRepo(@Valid RepoCheckRequest request) {
        String token = request.gitToken();
        String hosts = request.gitTokenHosts();
        if ((token == null || token.isBlank()) && request.systemId() != null) {
            AuditedSystem system = systemRepository.findById(request.systemId());
            if (system != null && system.hasGitToken()) {
                token = secretBox.decrypt(system.getGitTokenEnc());
                if (hosts == null || hosts.isBlank()) {
                    hosts = system.getGitTokenHosts();
                }
            }
        }
        return ingestionService.checkRepository(request.repoUrl(), request.branch(), token, hosts);
    }

    /**
     * Atualiza os dados cadastrais do sistema. O agendamento e o perfil têm endpoints próprios.
     */
    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Atualizar sistema", description = "Altera nome, descrição, repositório, stack, equipe e (se informado) o perfil de regras")
    public SystemSummaryResponse updateSystem(@PathParam("id") Long id, @Valid CreateSystemRequest request) {
        AuditedSystem system = systemRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Sistema não encontrado: " + id));
        system.setName(request.getName());
        system.setDescription(request.getDescription());
        system.setRepoUrl(request.getRepoUrl());
        system.setStack(request.getStack());
        system.setTeam(request.getTeam());
        if (request.getProfile() != null) {
            system.setProfile(validProfile(request.getProfile()));
        }
        applyGitAccess(system, request);
        return toSummary(system);
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
        mcpGate.purge(id);
        tuningService.purge(id);
        reviewService.purge(id);
        pendingActions.purge(id);
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
                .map(r -> runMapper.toResponse(r, true))
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

    /** Token (cifrado) e hosts do acesso ao repositório: token vazio mantém o salvo; "clearGitToken" apaga. */
    private void applyGitAccess(AuditedSystem system, CreateSystemRequest request) {
        if (request.getGitTokenHosts() != null) {
            String hosts = request.getGitTokenHosts().trim();
            system.setGitTokenHosts(hosts.isEmpty() ? null : hosts);
        }
        if (Boolean.TRUE.equals(request.getClearGitToken())) {
            system.setGitTokenEnc(null);
        }
        if (request.getGitHistoryDepth() != null) {
            system.setGitHistoryDepth(request.getGitHistoryDepth());
        }
        if (request.getNotifyFormat() != null) {
            String f = request.getNotifyFormat().trim().toLowerCase(java.util.Locale.ROOT);
            system.setNotifyFormat(f.isEmpty() ? null : f);
        }
        if (request.getNotifyMinSeverity() != null) {
            String sev = request.getNotifyMinSeverity().trim().toUpperCase(java.util.Locale.ROOT);
            system.setNotifyMinSeverity(sev.isEmpty() ? null : sev);
        }
        if (request.getNotifyMinScoreDrop() != null) {
            system.setNotifyMinScoreDrop(request.getNotifyMinScoreDrop());
        }
        if (request.getNotifyPublicUrl() != null) {
            String u = request.getNotifyPublicUrl().trim();
            system.setNotifyPublicUrl(u.isEmpty() ? null : u);
        }
        if (Boolean.TRUE.equals(request.getClearNotifyUrl())) {
            system.setNotifyUrlEnc(null);
        }
        String notifyUrl = request.getNotifyUrl();
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            try {
                com.sentinel.audit.service.NotificationService.validateTarget(notifyUrl.trim());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
            if (!secretBox.available()) {
                throw new BadRequestException("Não foi possível guardar a URL: a API não tem chave de cifragem (defina SECRETS_KEY ou deixe-a gravar o arquivo de chave em um volume).");
            }
            system.setNotifyUrlEnc(secretBox.encrypt(notifyUrl.trim()));
        }
        if (Boolean.TRUE.equals(request.getClearWebhookSecret())) {
            system.setWebhookSecretEnc(null);
        }
        String webhook = request.getWebhookSecret();
        if (webhook != null && !webhook.isBlank()) {
            if (!secretBox.available()) {
                throw new BadRequestException("Não foi possível guardar o segredo: a API não tem chave de cifragem (defina SECRETS_KEY ou deixe-a gravar o arquivo de chave em um volume).");
            }
            system.setWebhookSecretEnc(secretBox.encrypt(webhook.trim()));
        }
        String token = request.getGitToken();
        if (token != null && !token.isBlank()) {
            if (!secretBox.available()) {
                throw new BadRequestException("Não foi possível guardar o token: a API não tem chave de cifragem (defina SECRETS_KEY ou deixe-a gravar o arquivo de chave em um volume).");
            }
            system.setGitTokenEnc(secretBox.encrypt(token.trim()));
        }
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
        dto.setRepoUrl(system.getRepoUrl());
        dto.setDescription(system.getDescription());
        dto.setGitTokenSet(system.hasGitToken());
        dto.setWebhookSecretSet(system.hasWebhookSecret());
        dto.setGitHistoryDepth(system.getGitHistoryDepth());
        dto.setNotifyUrlSet(system.hasNotifyUrl());
        dto.setNotifyFormat(system.getNotifyFormat());
        dto.setNotifyMinScoreDrop(system.getNotifyMinScoreDrop());
        dto.setNotifyMinSeverity(system.getNotifyMinSeverity());
        dto.setNotifyPublicUrl(system.getNotifyPublicUrl());
        dto.setGitTokenHosts(system.getGitTokenHosts());

        List<AuditRun> runs = auditRunRepository.findBySystemId(system.getId());
        dto.setScoreDelta(com.sentinel.audit.service.ScoreTrend.delta(runs));
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
}
