package com.sentinel.audit.resource;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.PackRegistry;
import com.sentinel.audit.pack.ProfileLoader;
import com.sentinel.audit.pack.RuleCatalog;
import com.sentinel.audit.pack.RuleOverride;
import com.sentinel.audit.pack.SystemTuning;
import com.sentinel.audit.service.AnalyzerCatalog;
import com.sentinel.audit.service.FindingCatalog;
import com.sentinel.audit.service.RunCatalog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Monta a visão dos ajustes do motor de um sistema (o que vem do perfil, o que foi ajustado, o catálogo) e valida ajustes novos. */
@ApplicationScoped
public class EngineConfigViews {

    public record RuleAdjustment(@Schema(description = "Regra ajustada.") String id, @Schema(description = "Severidade forçada; vazio = a da regra.") String severity, @Schema(description = "Regra desligada.") boolean disabled, @Schema(description = "Regra em observação: aparece, mas não pesa na nota.") Boolean shadow) {}

    public record PackState(@Schema(description = "Pacote.") String id, @Schema(description = "Nome.") String name, @Schema(description = "O que cobre.") String description, @Schema(description = "Está no perfil do sistema.") boolean inProfile, @Schema(description = "Está ativo para este sistema.") boolean active) {}

    public record AnalyzerState(@Schema(description = "Analisador.") String name, @Schema(description = "O que faz.") String description, @Schema(description = "Desligado para este sistema.") boolean disabled, @Schema(description = "Lado: back, front ou common.") String side) {}

    public record ThresholdState(@Schema(description = "Nome do limite.") String key, @Schema(description = "Valor do perfil.") Integer profileValue, @Schema(description = "Valor ajustado no sistema; nulo = o do perfil.") Integer override) {}

    public record KnownRule(@Schema(description = "Regra.") String id, @Schema(description = "Achados da regra na última auditoria.") long count) {}

    /** Regra declarada num pacote: o que ela faz, de que pacote vem, a severidade que o Semgrep declara e o lado (back, front ou common). */
    public record CatalogRule(@Schema(description = "Regra.") String id, @Schema(description = "Pacote de origem.") String pack, @Schema(description = "O que a regra diz.") String message, @Schema(description = "Severidade declarada no Semgrep.") String severity, @Schema(description = "Categoria.") String category, @Schema(description = "Lado: back, front ou common.") String side) {}

    public record Tuning(@Schema(description = "Ajustes por regra.") List<RuleAdjustment> rules, @Schema(description = "Limites ajustados.") Map<String, Integer> thresholds, @Schema(description = "Pacotes ligados além do perfil.") List<String> enablePacks,
                         @Schema(description = "Pacotes desligados.") List<String> disablePacks, @Schema(description = "Analisadores desligados.") List<String> disableAnalyzers, @Schema(description = "Caminhos ignorados.") List<String> ignorePaths,
                         @Schema(description = "Lados desligados (back, front).") List<String> disableSides) {}

    public record EngineConfigResponse(@Schema(description = "Perfil do sistema.") String profile, @Schema(description = "Descrição do perfil.") String profileDescription, @Schema(description = "Quantos ajustes o sistema tem sobre o perfil.") int adjustments, @Schema(description = "Ajustes salvos do sistema.") Tuning tuning,
                                       @Schema(description = "Ajustes que vêm do perfil.") List<RuleAdjustment> profileRules, @Schema(description = "Estado de cada pacote.") List<PackState> packs,
                                       @Schema(description = "Estado de cada analisador.") List<AnalyzerState> analyzers, @Schema(description = "Estado de cada limite.") List<ThresholdState> thresholds,
                                       @Schema(description = "Regras vistas nas auditorias, com contagem.") List<KnownRule> knownRules, @Schema(description = "Regras dos pacotes.") List<CatalogRule> catalog) {}

    @Inject
    PackRegistry packRegistry;
    @Inject
    RunCatalog runCatalog;
    @Inject
    FindingCatalog findingCatalog;

    void validateAgainstRegistry(SystemTuning t) {
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

    EngineConfigResponse view(AuditedSystem system, SystemTuning tuning) {
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
                .map(n -> new AnalyzerState(n, AnalyzerCatalog.describe(n), tuning.analyzerDisabled(n), AnalyzerCatalog.sideOf(n)))
                .toList();
        List<ThresholdState> thresholds = ProfileLoader.KNOWN_THRESHOLDS.stream().sorted()
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
                List.copyOf(tuning.ignorePaths()), tuning.disableSides().stream().sorted().toList());
        String description = packRegistry.snapshot().profiles().containsKey(profileId == null ? "" : profileId.toLowerCase(Locale.ROOT))
                ? packRegistry.snapshot().profiles().get(profileId.toLowerCase(Locale.ROOT)).description() : null;
        List<CatalogRule> catalog = RuleCatalog.of(packRegistry.snapshot().packs()).stream()
                .map(e -> new CatalogRule(e.id(), e.pack(), e.message(), e.severity(), e.category(), e.side()))
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
}
