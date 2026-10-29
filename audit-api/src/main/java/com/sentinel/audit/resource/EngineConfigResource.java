package com.sentinel.audit.resource;

import com.sentinel.audit.tuning.TuningJson;
import com.sentinel.audit.tuning.SystemTuningService;
import jakarta.annotation.security.PermitAll;
import com.fasterxml.jackson.databind.JsonNode;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.PackRegistry;
import com.sentinel.audit.pack.RuleOverride;
import com.sentinel.audit.pack.SystemTuning;
import com.sentinel.audit.service.AnalyzerCatalog;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.RunCatalog;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public record RuleAdjustment(String id, String severity, boolean disabled, Boolean shadow) {}

    public record PackState(String id, String name, String description, boolean inProfile, boolean active) {}

    public record AnalyzerState(String name, String description, boolean disabled) {}

    public record ThresholdState(String key, Integer profileValue, Integer override) {}

    public record KnownRule(String id, long count) {}

    /** Regra declarada num pacote: o que ela faz, de que pacote vem e a severidade que o Semgrep declara. */
    public record CatalogRule(String id, String pack, String message, String severity, String category) {}

    public record Tuning(List<RuleAdjustment> rules, Map<String, Integer> thresholds, List<String> enablePacks,
                         List<String> disablePacks, List<String> disableAnalyzers, List<String> ignorePaths) {}

    public record EngineConfigResponse(String profile, String profileDescription, int adjustments, Tuning tuning,
                                       List<RuleAdjustment> profileRules, List<PackState> packs,
                                       List<AnalyzerState> analyzers, List<ThresholdState> thresholds,
                                       List<KnownRule> knownRules, List<CatalogRule> catalog) {}

    @Inject
    SystemCatalog systemCatalog;
    @Inject
    SystemTuningService tuningService;
    @Inject
    PackRegistry packRegistry;
    @Inject
    RunCatalog runCatalog;
    @Inject
    FindingCatalog findingCatalog;

    @GET
    @Operation(summary = "Ajustes do sistema e o que vem do perfil")
    public EngineConfigResponse get(@PathParam("id") Long id) {
        return view(requireSystem(id), tuningService.load(id));
    }

    @PUT
    @Operation(summary = "Substituir os ajustes do sistema", description = "Vazio = voltar a ser igual ao perfil")
    public EngineConfigResponse put(@PathParam("id") Long id, JsonNode body) {
        AuditedSystem system = requireSystem(id);
        SystemTuning parsed;
        try {
            parsed = TuningJson.parse(body);
            validateAgainstRegistry(parsed);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
        return view(system, tuningService.save(id, parsed));
    }

    private void validateAgainstRegistry(SystemTuning t) {
        List<String> known = packRegistry.snapshot().packs().stream().map(p -> p.id()).toList();
        for (String p : t.enablePacks()) {
            if (!known.contains(p)) {
                throw new IllegalArgumentException("Pacote desconhecido: " + p);
            }
        }
        for (String p : t.disablePacks()) {
            if (!known.contains(p)) {
                throw new IllegalArgumentException("Pacote desconhecido: " + p);
            }
        }
        for (String a : t.disableAnalyzers()) {
            if (!AnalyzerCatalog.names().contains(a)) {
                throw new IllegalArgumentException("Analisador desconhecido: " + a);
            }
        }
    }

    private EngineConfigResponse view(AuditedSystem system, SystemTuning tuning) {
        String profileId = system.effectiveProfile();
        AuditPolicy resolved;
        try {
            resolved = packRegistry.policyFor(profileId);
        } catch (IllegalArgumentException e) {
            resolved = packRegistry.policyFor(null);
        }
        final AuditPolicy base = resolved;
        AuditPolicy effective = base.withTuning(tuning, packRegistry.snapshot().packs());
        List<String> inProfile = base.packs().stream().map(p -> p.id()).toList();
        List<String> active = effective.packs().stream().map(p -> p.id()).toList();
        List<PackState> packs = packRegistry.snapshot().packs().stream()
                .map(p -> new PackState(p.id(), p.manifest().name(), p.manifest().description(),
                        inProfile.contains(p.id()), active.contains(p.id())))
                .toList();
        List<AnalyzerState> analyzers = AnalyzerCatalog.names().stream()
                .map(n -> new AnalyzerState(n, AnalyzerCatalog.describe(n), tuning.analyzerDisabled(n)))
                .toList();
        List<ThresholdState> thresholds = com.sentinel.audit.pack.ProfileLoader.KNOWN_THRESHOLDS.stream().sorted()
                .map(k -> new ThresholdState(k, base.thresholds().get(k), tuning.thresholds().get(k)))
                .toList();
        List<KnownRule> known = new ArrayList<>();
        List<AuditRun> last = runCatalog.latestCompleted(system.getId(), 1);
        if (!last.isEmpty()) {
            for (Object[] row : findingCatalog.ruleCounts(last.get(0).getId())) {
                known.add(new KnownRule(String.valueOf(row[0]), (Long) row[1]));
            }
        }
        Tuning dto = new Tuning(rules(tuning.rules()), tuning.thresholds(), tuning.enablePacks().stream().sorted().toList(),
                tuning.disablePacks().stream().sorted().toList(), tuning.disableAnalyzers().stream().sorted().toList(),
                List.copyOf(tuning.ignorePaths()));
        String description = packRegistry.snapshot().profiles().containsKey(profileId == null ? "" : profileId.toLowerCase(java.util.Locale.ROOT))
                ? packRegistry.snapshot().profiles().get(profileId.toLowerCase(java.util.Locale.ROOT)).description() : null;
        List<CatalogRule> catalog = com.sentinel.audit.pack.RuleCatalog.of(packRegistry.snapshot().packs()).stream()
                .map(e -> new CatalogRule(e.id(), e.pack(), e.message(), e.severity(), e.category()))
                .toList();
        return new EngineConfigResponse(profileId, description, tuning.adjustments(), dto, rules(base.baseOverrides()),
                packs, analyzers, thresholds, known, catalog);
    }

    private static List<RuleAdjustment> rules(Map<String, RuleOverride> in) {
        return in.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new RuleAdjustment(e.getKey(), e.getValue().severity() == null ? null : e.getValue().severity().name(),
                        e.getValue().disabled(), e.getValue().shadow()))
                .toList();
    }

    private AuditedSystem requireSystem(Long id) {
        return systemCatalog.require(id);
    }
}
