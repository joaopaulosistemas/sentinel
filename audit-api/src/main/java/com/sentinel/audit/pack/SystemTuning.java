package com.sentinel.audit.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ajustes de um sistema por cima do perfil dele. Só guarda o que difere do perfil: vazio significa "igual ao perfil",
 * e então a política da auditoria é exatamente a do perfil.
 *
 * @param rules             sobrescritas por id de regra (aceita {@code *}); valem sobre as do perfil, chave a chave
 * @param thresholds        limites de clean code que diferem do perfil
 * @param enablePacks       pacotes ligados além dos do perfil
 * @param disablePacks      pacotes retirados dos do perfil
 * @param disableAnalyzers  analisadores que não rodam neste sistema (nome como aparece na tela de acompanhamento)
 * @param ignorePaths       caminhos cujos achados não entram na auditoria (curinga estilo .gitignore: {@code *}, {@code **}, {@code ?};
 *                          pasta termina em {@code /}; sem {@code /} vale para qualquer pasta)
 */
public record SystemTuning(Map<String, RuleOverride> rules, Map<String, Integer> thresholds,
                           Set<String> enablePacks, Set<String> disablePacks, Set<String> disableAnalyzers,
                           Set<String> ignorePaths) {

    public static final SystemTuning EMPTY = new SystemTuning(Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Set.of());

    /** Sem caminhos ignorados (forma que existia antes do campo {@code ignorePaths}). */
    public SystemTuning(Map<String, RuleOverride> rules, Map<String, Integer> thresholds, Set<String> enablePacks,
                        Set<String> disablePacks, Set<String> disableAnalyzers) {
        this(rules, thresholds, enablePacks, disablePacks, disableAnalyzers, Set.of());
    }

    public SystemTuning {
        rules = rules == null ? Map.of() : copyRules(rules);
        thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
        enablePacks = lower(enablePacks);
        disablePacks = lower(disablePacks);
        disableAnalyzers = disableAnalyzers == null ? Set.of() : Set.copyOf(disableAnalyzers);
        ignorePaths = ignorePaths == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(ignorePaths));
    }

    public boolean isEmpty() {
        return rules.isEmpty() && thresholds.isEmpty() && enablePacks.isEmpty() && disablePacks.isEmpty()
                && disableAnalyzers.isEmpty() && ignorePaths.isEmpty();
    }

    /** Quantos ajustes existem em relação ao perfil (o número que a tela mostra). */
    public int adjustments() {
        return rules.size() + thresholds.size() + enablePacks.size() + disablePacks.size() + disableAnalyzers.size() + ignorePaths.size();
    }

    public boolean analyzerDisabled(String name) {
        if (name == null) {
            return false;
        }
        return disableAnalyzers.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    private static Map<String, RuleOverride> copyRules(Map<String, RuleOverride> in) {
        Map<String, RuleOverride> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k.toLowerCase(Locale.ROOT).trim(), v));
        return Map.copyOf(out);
    }

    private static Set<String> lower(Set<String> in) {
        if (in == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        in.forEach(s -> out.add(s.toLowerCase(Locale.ROOT).trim()));
        return Set.copyOf(out);
    }
}
