package com.sentinel.audit.pack;

import com.sentinel.audit.model.Finding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * O que vale numa auditoria: os pacotes ativos, as sobrescritas de regra do perfil e os limites de clean code.
 * É montada por {@link PackRegistry#policyFor(String)} e entregue aos analisadores.
 */
public final class AuditPolicy {

    private final String profileId;
    private final List<RulePack> packs;
    private final Map<String, RuleOverride> exact = new LinkedHashMap<>();
    private final Map<Pattern, RuleOverride> globs = new LinkedHashMap<>();
    private final Map<String, Integer> thresholds;
    private final Map<String, RuleOverride> rawOverrides;
    private SystemTuning tuning = SystemTuning.EMPTY;
    private java.nio.file.Path localRulesDir;

    public AuditPolicy(String profileId, List<RulePack> packs, Map<String, RuleOverride> overrides,
                       Map<String, Integer> thresholds) {
        this.profileId = profileId;
        this.packs = List.copyOf(packs);
        this.thresholds = Map.copyOf(thresholds);
        this.rawOverrides = Map.copyOf(overrides);
        for (Map.Entry<String, RuleOverride> e : overrides.entrySet()) {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            if (key.contains("*")) {
                String[] parts = key.split("\\*", -1);
                StringBuilder regex = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    regex.append(Pattern.quote(parts[i]));
                    if (i < parts.length - 1) {
                        regex.append(".*");
                    }
                }
                globs.put(Pattern.compile(regex.toString()), e.getValue());
            } else {
                exact.put(key, e.getValue());
            }
        }
    }

    /** Todos os pacotes habilitados, sem sobrescritas nem limites (comportamento sem perfil). */
    public static AuditPolicy defaults(List<RulePack> packs) {
        return new AuditPolicy("default", packs, Map.of(), Map.of());
    }

    private java.util.Set<String> trustedToolPacks = java.util.Set.of();

    /** Pacotes (já conferidos como embutidos) cujas ferramentas de linha de comando podem rodar sem {@code allow-tools}. */
    public AuditPolicy withTrustedToolPacks(java.util.Set<String> ids) {
        this.trustedToolPacks = java.util.Set.copyOf(ids);
        return this;
    }

    public boolean isToolPackTrusted(String packId) {
        return trustedToolPacks.contains(packId);
    }

    /**
     * Política do sistema: a do perfil mais os ajustes dele. Sem ajustes devolve esta mesma política, sem copiar nada,
     * o que garante que um sistema que nunca foi ajustado audita exatamente como antes.
     *
     * @param allPacks todos os pacotes carregados, para ligar um que o perfil não inclui
     */
    public AuditPolicy withTuning(SystemTuning t, List<RulePack> allPacks) {
        if (t == null || t.isEmpty()) {
            return this;
        }
        List<RulePack> merged = new ArrayList<>();
        for (RulePack p : allPacks) {
            boolean active = packs.stream().anyMatch(x -> x.id().equals(p.id()));
            if (t.enablePacks().contains(p.id())) {
                active = true;
            }
            if (t.disablePacks().contains(p.id())) {
                active = false;
            }
            if (active) {
                merged.add(p);
            }
        }
        Map<String, RuleOverride> rules = new LinkedHashMap<>(rawOverrides);
        rules.putAll(t.rules());
        Map<String, Integer> limits = new LinkedHashMap<>(thresholds);
        limits.putAll(t.thresholds());
        AuditPolicy out = new AuditPolicy(profileId, merged, rules, limits).withTrustedToolPacks(trustedToolPacks);
        out.tuning = t;
        return out;
    }

    /** Sobrescritas do perfil, sem os ajustes do sistema (para a tela mostrar o que vem do perfil). */
    public Map<String, RuleOverride> baseOverrides() {
        return rawOverrides;
    }

    public Map<String, Integer> thresholds() {
        return thresholds;
    }

    /** Pasta com as regras Semgrep locais do sistema, ou {@code null}. */
    public java.nio.file.Path localRulesDir() {
        return localRulesDir;
    }

    /**
     * Acrescenta as regras locais do sistema. As que estão em observação ({@code shadowIds}) entram em modo sombra,
     * a menos que o próprio sistema já tenha um ajuste explícito para elas.
     */
    public AuditPolicy withLocalRules(java.nio.file.Path dir, java.util.Set<String> shadowIds) {
        Map<String, RuleOverride> rules = new LinkedHashMap<>(rawOverrides);
        for (String id : shadowIds) {
            rules.putIfAbsent(id.toLowerCase(Locale.ROOT), new RuleOverride(null, false, true));
        }
        AuditPolicy out = new AuditPolicy(profileId, packs, rules, thresholds).withTrustedToolPacks(trustedToolPacks);
        out.tuning = tuning;
        out.localRulesDir = dir;
        return out;
    }

    /** Este analisador foi desligado para o sistema? */
    public boolean analyzerDisabled(String name) {
        return tuning.analyzerDisabled(name);
    }

    public String profileId() {
        return profileId;
    }

    public List<RulePack> packs() {
        return packs;
    }

    public int threshold(String key, int fallback) {
        return thresholds.getOrDefault(key, fallback);
    }

    public ArchitecturePolicy architecture() {
        return ArchitecturePolicy.merge(packs.stream().map(RulePack::architecture).toList());
    }

    public List<ToolSpec> tools() {
        return packs.stream().flatMap(p -> p.tools().stream()).toList();
    }

    /** Sobrescrita aplicável ao id de regra: id completo, ou o último trecho depois de ponto (ids do Semgrep trazem o caminho). */
    public RuleOverride overrideFor(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return null;
        }
        String full = ruleId.toLowerCase(Locale.ROOT);
        String shortId = full.substring(full.lastIndexOf('.') + 1);
        RuleOverride o = exact.get(shortId);
        if (o == null) {
            o = exact.get(full);
        }
        if (o != null) {
            return o;
        }
        for (Map.Entry<Pattern, RuleOverride> e : globs.entrySet()) {
            if (e.getKey().matcher(shortId).matches() || e.getKey().matcher(full).matches()) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Aplica as sobrescritas: tira o que está desligado, troca severidade e marca/desmarca o modo sombra. */
    public List<Finding> apply(List<Finding> findings) {
        List<Finding> out = new ArrayList<>(findings.size());
        for (Finding f : findings) {
            RuleOverride o = overrideFor(f.getRuleId());
            if (o == null) {
                out.add(f);
                continue;
            }
            if (o.disabled()) {
                continue;
            }
            if (o.severity() != null) {
                f.setSeverity(o.severity());
            }
            if (o.shadow() != null) {
                f.setShadow(o.shadow());
            }
            out.add(f);
        }
        return out;
    }
}
