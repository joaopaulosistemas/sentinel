package com.sentinel.audit.resource;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.annotation.security.PermitAll;
import com.sentinel.audit.pack.PackManifest;
import com.sentinel.audit.pack.PackRegistry;
import com.sentinel.audit.pack.Profile;
import com.sentinel.audit.pack.RulePack;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Pacotes de regras e perfis carregados. Caminhos do servidor não são expostos. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/packs")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Packs", description = "Pacotes de regras e perfis")
public class PackResource {

    public record PackInfo(@Schema(description = "Identificador do pacote.") String id, @Schema(description = "Nome.") String name, @Schema(description = "Versão.") String version, @Schema(description = "O que o pacote cobre.") String description, @Schema(description = "Autor.") String author,
                           @Schema(description = "Linguagens atendidas.") List<String> languages, @Schema(description = "Ligado quando o perfil não diz nada.") boolean enabledByDefault, @Schema(description = "Quantidade de regras Semgrep.") int semgrepRules, @Schema(description = "Quantidade de orientações de correção.") int remediations,
                           @Schema(description = "Quantidade de tópicos de deduplicação.") int dedupTopics, @Schema(description = "Quantidade de analisadores declarados.") int tools, @Schema(description = "Verdadeiro quando traz regras de arquitetura.") boolean architecture, @Schema(description = "Situação da integridade do pacote (unsigned quando não há assinatura).") String integrity) {}

    public record ProfileInfo(@Schema(description = "Identificador do perfil.") String id, @Schema(description = "Nome.") String name, @Schema(description = "Para que serve.") String description, @Schema(description = "Perfil que este estende.") String extendsId, @Schema(description = "Pacotes do perfil.") List<String> packs,
                              @Schema(description = "Quantas regras o perfil ajusta.") int ruleOverrides, @Schema(description = "Limites do perfil, por nome.") Map<String, Integer> thresholds) {}

    public record PacksResponse(@Schema(description = "Pacotes carregados.") List<PackInfo> packs, @Schema(description = "Perfis carregados.") List<ProfileInfo> profiles, @Schema(description = "Pacotes recusados e os motivos.") Map<String, List<String>> rejected,
                                @Schema(description = "Avisos do carregamento.") List<String> warnings, @Schema(description = "Quando os pacotes foram carregados.") Instant loadedAt) {}

    @Inject
    PackRegistry registry;

    @GET
    @Operation(summary = "Pacotes e perfis carregados",
            description = "Inclui o que foi rejeitado na validação (com o motivo) e os avisos do carregamento")
    public PacksResponse list() {
        return toResponse(registry.snapshot());
    }

    @POST
    @Path("/reload")
    @Operation(summary = "Recarrega os pacotes", description = "Relê as pastas e atualiza o repositório Git de pacotes, se configurado")
    public PacksResponse reload() {
        return toResponse(registry.reload());
    }

    static PacksResponse toResponse(PackRegistry.Snapshot s) {
        List<PackInfo> packs = s.packs().stream().map(p -> info(p, s.integrity().getOrDefault(p.id(), "unsigned"))).sorted(Comparator.comparing(PackInfo::id)).toList();
        List<ProfileInfo> profiles = s.profiles().values().stream().map(p -> profileInfo(p))
                .sorted(Comparator.comparing(ProfileInfo::id)).toList();
        return new PacksResponse(packs, profiles, s.rejected(), s.warnings(), s.loadedAt());
    }

    private static PackInfo info(RulePack p, String integrity) {
        PackManifest m = p.manifest();
        return new PackInfo(m.id(), m.name(), m.version(), m.description(), m.author(), m.languages(), m.enabledByDefault(),
                p.semgrepRuleCount(), p.remediation().size(), p.dedup().size(), p.tools().size(), !p.architecture().isEmpty(), integrity);
    }

    private static ProfileInfo profileInfo(Profile p) {
        return new ProfileInfo(p.id(), p.name(), p.description(), p.extendsId(), p.packs(), p.rules().size(), p.thresholds());
    }
}
