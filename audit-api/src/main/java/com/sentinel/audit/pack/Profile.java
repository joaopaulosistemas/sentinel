package com.sentinel.audit.pack;

import java.util.List;
import java.util.Map;

/**
 * Perfil: o que vale para um grupo de sistemas (pacotes ativos, sobrescritas de regra e limites de clean code).
 *
 * @param packs        pacotes ativos; {@code null} = herda do {@code extendsId} ou, sem pai, todos os habilitados por padrão
 * @param disablePacks pacotes retirados depois de resolver {@code packs}
 * @param rules        sobrescritas por id de regra (aceita {@code *}); chave exata vale mais que glob
 * @param thresholds   limites do CleanCode (ver {@link ProfileLoader#KNOWN_THRESHOLDS})
 */
public record Profile(String id, String name, String description, String extendsId, List<String> packs,
                      List<String> disablePacks, Map<String, RuleOverride> rules, Map<String, Integer> thresholds) {}
