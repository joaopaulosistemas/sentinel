package com.sentinel.audit.resource;

import jakarta.annotation.security.PermitAll;
import com.sentinel.audit.dto.AuditRunResponse;
import com.sentinel.audit.dto.CreateSystemRequest;
import com.sentinel.audit.dto.ProfileRequest;
import com.sentinel.audit.dto.ScheduleRequest;
import com.sentinel.audit.dto.SystemSummaryResponse;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.pack.PackRegistry;
import com.sentinel.audit.dto.SuppressionResponse;
import com.sentinel.audit.dto.TrendPoint;
import com.sentinel.audit.service.IngestionService;
import com.sentinel.audit.dto.PathCheckRequest;
import com.sentinel.audit.dto.RepoCheckRequest;
import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.model.SourceType;
import com.sentinel.audit.service.LocalSource;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/systems")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Systems", description = "Gerenciamento de sistemas auditados")
public class SystemResource {


    @Inject
    PackRegistry packRegistry;

    @Inject
    IngestionService ingestionService;

    @Inject
    com.sentinel.audit.service.SecretBox secretBox;

    @Inject
    SystemCatalog systemCatalog;

    @Inject
    SystemViews views;

    @Inject
    SystemCleanup cleanup;

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
        system.setName(request.name());
        system.setDescription(request.description());
        applySource(system, request);
        system.setStack(request.stack());
        system.setTeam(request.team());
        system.setProfile(validProfile(request.profile()));
        applyGitAccess(system, request);
        systemCatalog.add(system);
        return Response.status(Response.Status.CREATED).entity(views.summary(system)).build();
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
        system.setName(request.name());
        system.setDescription(request.description());
        applySource(system, request);
        system.setStack(request.stack());
        system.setTeam(request.team());
        if (request.profile() != null) {
            system.setProfile(validProfile(request.profile()));
        }
        applyGitAccess(system, request);
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
     * Evolução do score do sistema ao longo das auditorias concluídas (mais antiga primeiro).
     */
    @GET
    @Path("/{id}/trend")
    @Operation(summary = "Tendência do score", description = "Score e contagem por severidade das últimas auditorias concluídas")
    public List<TrendPoint> trend(@PathParam("id") Long id, @QueryParam("limit") @DefaultValue("30") int limit) {
        systemCatalog.require(id);
        return views.trend(id, limit);
    }

    /**
     * Supressões (falsos positivos / riscos aceitos) registradas para o sistema.
     */
    @GET
    @Path("/{id}/suppressions")
    @Operation(summary = "Listar supressões")
    public List<SuppressionResponse> suppressions(@PathParam("id") Long id) {
        systemCatalog.require(id);
        return views.suppressions(id);
    }

    /**
     * Remove uma supressão: o finding volta a contar no score.
     */
    @DELETE
    @Path("/{id}/suppressions/{suppressionId}")
    @Operation(summary = "Remover supressão")
    public Response removeSuppression(@PathParam("id") Long id, @PathParam("suppressionId") Long suppressionId) {
        views.removeSuppression(id, suppressionId);
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
     * Lista todas as execuções de auditoria de um sistema.
     */
    @GET
    @Path("/{id}/audits")
    @Operation(summary = "Listar auditorias do sistema", description = "Retorna todas as execuções de auditoria de um sistema")
    public List<AuditRunResponse> listAudits(@PathParam("id") Long id) {
        systemCatalog.require(id);

        return views.audits(id);
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
        return views.summary(system);
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
        SourceType type = request.sourceType() == null ? SourceType.GIT_URL : request.sourceType();
        boolean sameFolder = system.isLocalSource() && LocalSource.normalizeInput(request.localPath()).equals(system.getLocalPath());
        system.setSourceType(type);
        if (type == SourceType.LOCAL_PATH) {
            if (!sameFolder) {
                // Pasta nova: precisa existir, estar dentro da raiz e ser um repositório Git. Editar outros campos de
                // um sistema que já usa a pasta não depende de ela estar acessível agora.
                try {
                    system.setLocalPath(ingestionService.validateLocalPath(request.localPath(), null));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(e.getMessage(), e);
                }
            }
            system.setLocalSnapshot(request.localSnapshot() == null ? LocalSnapshot.COMMIT : request.localSnapshot());
            system.setRepoUrl(null);
            return;
        }
        system.setRepoUrl(request.repoUrl());
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
        if (request.gitTokenHosts() != null) {
            system.setGitTokenHosts(blankToNull(request.gitTokenHosts().trim()));
        }
        if (Boolean.TRUE.equals(request.clearGitToken())) {
            system.setGitTokenEnc(null);
        }
        if (request.gitHistoryDepth() != null) {
            system.setGitHistoryDepth(request.gitHistoryDepth());
        }
    }

    private void applyNotifySettings(AuditedSystem system, CreateSystemRequest request) {
        if (request.notifyFormat() != null) {
            system.setNotifyFormat(blankToNull(request.notifyFormat().trim().toLowerCase(java.util.Locale.ROOT)));
        }
        if (request.notifyMinSeverity() != null) {
            system.setNotifyMinSeverity(blankToNull(request.notifyMinSeverity().trim().toUpperCase(java.util.Locale.ROOT)));
        }
        if (request.notifyMinScoreDrop() != null) {
            system.setNotifyMinScoreDrop(request.notifyMinScoreDrop());
        }
        if (request.notifyPublicUrl() != null) {
            system.setNotifyPublicUrl(blankToNull(request.notifyPublicUrl().trim()));
        }
        if (Boolean.TRUE.equals(request.clearNotifyUrl())) {
            system.setNotifyUrlEnc(null);
        }
        String notifyUrl = request.notifyUrl();
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            try {
                com.sentinel.audit.service.NotificationService.validateTarget(notifyUrl.trim());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage(), e);
            }
            system.setNotifyUrlEnc(encrypted(notifyUrl, "a URL"));
        }
    }

    /** Segredos novos (webhook e token Git): cifrados antes de gravar; "remover" apaga o que já estava salvo. */
    private void applySecrets(AuditedSystem system, CreateSystemRequest request) {
        if (Boolean.TRUE.equals(request.clearWebhookSecret())) {
            system.setWebhookSecretEnc(null);
        }
        String webhook = request.webhookSecret();
        if (webhook != null && !webhook.isBlank()) {
            system.setWebhookSecretEnc(encrypted(webhook, "o segredo"));
        }
        String token = request.gitToken();
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
}
