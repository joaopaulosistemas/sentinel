package com.sentinel.audit.resource;

import com.sentinel.audit.dto.PathCheckRequest;
import com.sentinel.audit.dto.RepoCheckRequest;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.service.IngestionService;
import com.sentinel.audit.service.LocalSource;
import com.sentinel.audit.service.SecretBox;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.validation.Valid;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/** Verificação da origem do código (repositório Git ou pasta local) antes de cadastrar ou salvar um sistema. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/systems")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Systems", description = "Gerenciamento de sistemas auditados")
public class SystemSourceResource {

    @Inject
    IngestionService ingestionService;

    @Inject
    SecretBox secretBox;

    @Inject
    SystemCatalog systemCatalog;


    /**
     * Confere se o repositório responde e qual é a branch padrão, sem clonar.
     */
    @POST
    @Path("/check-repo")
    @Operation(summary = "Verificar repositório", description = "Testa o acesso ao repositório e descobre a branch padrão (git ls-remote), sem clonar")
    public IngestionService.RepoCheck checkRepo(@Valid RepoCheckRequest request) {
        String token = request.gitToken();
        String hosts = request.gitTokenHosts();
        AuditedSystem saved = (token == null || token.isBlank()) && request.systemId() != null
                ? systemCatalog.findOrNull(request.systemId()) : null;
        if (saved != null && saved.hasGitToken()) {
            token = secretBox.decrypt(saved.getGitTokenEnc());
            hosts = hosts == null || hosts.isBlank() ? saved.getGitTokenHosts() : hosts;
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
}
