package com.sentinel.audit.resource;

import com.sentinel.audit.service.AuditRunMapper;
import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.CreateSystemRequest;
import com.sentinel.audit.dto.ProfileRequest;
import com.sentinel.audit.dto.ScheduleRequest;
import com.sentinel.audit.dto.SystemSummaryResponse;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.pack.PackRegistry;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.FindingLifecycleService;
import com.sentinel.audit.dto.SuppressionResponse;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.service.AuditProgressTracker;
import com.sentinel.audit.service.IngestionService;
import com.sentinel.audit.dto.PathCheckRequest;
import com.sentinel.audit.dto.RepoCheckRequest;
import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.model.SourceType;
import com.sentinel.audit.service.LocalSource;
import com.sentinel.audit.service.RunCatalog;
import com.sentinel.audit.service.SystemCatalog;
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
    SystemCatalog systemCatalog;

    @Inject
    RunCatalog runCatalog;

    @Inject
    FindingCatalog findingCatalog;

    @Inject
    FindingLifecycleService lifecycleService;

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
        return systemCatalog.all().stream()
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
        applySource(system, request);
        system.setStack(request.getStack());
        system.setTeam(request.getTeam());
        system.setProfile(validProfile(request.getProfile()));
        applyGitAccess(system, request);
        systemCatalog.add(system);
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
            AuditedSystem system = systemCatalog.findOrNull(request.systemId());
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
     * Confere uma pasta local (dentro da raiz do servidor) sem copiá-la: é um repositório Git, qual a branch,
     * quantos arquivos e quantos ainda não foram commitados.
     */
    @POST
    @Path("/check-path")
    @Operation(summary = "Verificar pasta local", description = "Testa se a pasta (relativa à raiz de projetos do servidor) é um repositório Git acessível")
    public LocalSource.PathCheck checkPath(@Valid PathCheckRequest request) {
        return ingestionService.checkLocalPath(request.path(), request.branch());
    }

    /** Se a origem "pasta local" está ligada neste servidor (há uma raiz configurada). */
    @GET
    @Path("/local-source")
    @Operation(summary = "Origem pasta local ligada?")
    public Map<String, Boolean> localSource() {
        return Map.of("enabled", ingestionService.localSourceEnabled());
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
        system.setName(request.getName());
        system.setDescription(request.getDescription());
        applySource(system, request);
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
        AuditedSystem system = systemCatalog.require(id);
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
        AuditedSystem system = systemCatalog.require(id);
        mcpGate.purge(id);
        tuningService.purge(id);
        reviewService.purge(id);
        pendingActions.purge(id);
        systemCatalog.remove(system);
        return Response.noContent().build();
    }

    /**
     * Evolução do score do sistema ao longo das auditorias concluídas (mais antiga primeiro).
     */
    @GET
    @Path("/{id}/trend")
    @Operation(summary = "Tendência do score", description = "Score e contagem por severidade das últimas auditorias concluídas")
    public List<TrendPoint> trend(@PathParam("id") Long id, @QueryParam("limit") @DefaultValue("30") int limit) {
        systemCatalog.require(id);
        return lifecycleService.trend(id, limit);
    }

    /**
     * Supressões (falsos positivos / riscos aceitos) registradas para o sistema.
     */
    @GET
    @Path("/{id}/suppressions")
    @Operation(summary = "Listar supressões")
    public List<SuppressionResponse> suppressions(@PathParam("id") Long id) {
        systemCatalog.require(id);
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
        AuditedSystem system = systemCatalog.require(id);
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
        systemCatalog.require(id);

        return runCatalog.bySystem(id).stream()
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
        AuditedSystem system = systemCatalog.require(id);
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
    /**
     * Grava de onde vem o código: a URL https (padrão) ou uma pasta local validada contra a raiz do servidor.
     * Uma origem não carrega dados da outra, para o cadastro não ficar com lixo da escolha anterior.
     */
    private void applySource(AuditedSystem system, CreateSystemRequest request) {
        SourceType type = request.getSourceType() == null ? SourceType.GIT_URL : request.getSourceType();
        boolean sameFolder = system.isLocalSource() && LocalSource.normalizeInput(request.getLocalPath()).equals(system.getLocalPath());
        system.setSourceType(type);
        if (type == SourceType.LOCAL_PATH) {
            if (!sameFolder) {
                // Pasta nova: precisa existir, estar dentro da raiz e ser um repositório Git. Editar outros campos de
                // um sistema que já usa a pasta não depende de ela estar acessível agora.
                try {
                    system.setLocalPath(ingestionService.validateLocalPath(request.getLocalPath(), null));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(e.getMessage());
                }
            }
            system.setLocalSnapshot(request.getLocalSnapshot() == null ? LocalSnapshot.COMMIT : request.getLocalSnapshot());
            system.setRepoUrl(null);
            return;
        }
        system.setRepoUrl(request.getRepoUrl());
        system.setLocalPath(null);
        system.setLocalSnapshot(null);
    }

    private void applyGitAccess(AuditedSystem system, CreateSystemRequest request) {
        applyGitSettings(system, request);
        applyNotifySettings(system, request);
        applySecrets(system, request);
    }

    private static String blankToNull(String trimmed) {
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void applyGitSettings(AuditedSystem system, CreateSystemRequest request) {
        if (request.getGitTokenHosts() != null) {
            system.setGitTokenHosts(blankToNull(request.getGitTokenHosts().trim()));
        }
        if (Boolean.TRUE.equals(request.getClearGitToken())) {
            system.setGitTokenEnc(null);
        }
        if (request.getGitHistoryDepth() != null) {
            system.setGitHistoryDepth(request.getGitHistoryDepth());
        }
    }

    private void applyNotifySettings(AuditedSystem system, CreateSystemRequest request) {
        if (request.getNotifyFormat() != null) {
            system.setNotifyFormat(blankToNull(request.getNotifyFormat().trim().toLowerCase(java.util.Locale.ROOT)));
        }
        if (request.getNotifyMinSeverity() != null) {
            system.setNotifyMinSeverity(blankToNull(request.getNotifyMinSeverity().trim().toUpperCase(java.util.Locale.ROOT)));
        }
        if (request.getNotifyMinScoreDrop() != null) {
            system.setNotifyMinScoreDrop(request.getNotifyMinScoreDrop());
        }
        if (request.getNotifyPublicUrl() != null) {
            system.setNotifyPublicUrl(blankToNull(request.getNotifyPublicUrl().trim()));
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
            system.setNotifyUrlEnc(encrypted(notifyUrl, "a URL"));
        }
    }

    /** Segredos novos (webhook e token Git): cifrados antes de gravar; "remover" apaga o que já estava salvo. */
    private void applySecrets(AuditedSystem system, CreateSystemRequest request) {
        if (Boolean.TRUE.equals(request.getClearWebhookSecret())) {
            system.setWebhookSecretEnc(null);
        }
        String webhook = request.getWebhookSecret();
        if (webhook != null && !webhook.isBlank()) {
            system.setWebhookSecretEnc(encrypted(webhook, "o segredo"));
        }
        String token = request.getGitToken();
        if (token != null && !token.isBlank()) {
            system.setGitTokenEnc(encrypted(token, "o token"));
        }
    }

    private String encrypted(String value, String what) {
        if (!secretBox.available()) {
            throw new BadRequestException("Não foi possível guardar " + what + ": a API não tem chave de cifragem (defina SECRETS_KEY ou deixe-a gravar o arquivo de chave em um volume).");
        }
        return secretBox.encrypt(value.trim());
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
        dto.setSourceType(system.effectiveSourceType());
        dto.setLocalPath(system.getLocalPath());
        dto.setLocalSnapshot(system.isLocalSource() ? system.effectiveLocalSnapshot() : null);
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

        List<AuditRun> runs = runCatalog.bySystem(system.getId());
        dto.setScoreDelta(com.sentinel.audit.service.ScoreTrend.delta(runs));
        dto.setAuditCount(runs.size());
        runs.stream()
            .filter(r -> r.getStatus() == AuditStatus.COMPLETED)
            .findFirst()
            .ifPresent(lastRun -> {
                if (lastRun.getRiskScore() != null) {
                    dto.setLastAuditScore(lastRun.getRiskScore().getOverall());
                }
                dto.setLastAuditDate(lastRun.getCompletedAt());

                Map<Severity, Long> counts = findingCatalog.countBySeverity(lastRun.getId());
                dto.setOpenCritical(counts.getOrDefault(Severity.CRITICAL, 0L));
                dto.setOpenHigh(counts.getOrDefault(Severity.HIGH, 0L));
            });

        return dto;
    }
}
