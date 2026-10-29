package com.sentinel.audit.resource;

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

    public record PackInfo(String id, String name, String version, String description, String author,
                           List<String> languages, boolean enabledByDefault, int semgrepRules, int remediations,
                           int dedupTopics, int tools, boolean architecture, String integrity) {}

    public record ProfileInfo(String id, String name, String description, String extendsId, List<String> packs,
                              int ruleOverrides, Map<String, Integer> thresholds) {}

    public record PacksResponse(List<PackInfo> packs, List<ProfileInfo> profiles, Map<String, List<String>> rejected,
                                List<String> warnings, Instant loadedAt) {}

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
        List<ProfileInfo> profiles = s.profiles().values().stream().map(PackResource::profileInfo)
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
