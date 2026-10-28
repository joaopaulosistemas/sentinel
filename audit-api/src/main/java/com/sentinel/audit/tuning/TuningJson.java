package com.sentinel.audit.tuning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.pack.ProfileLoader;
import com.sentinel.audit.pack.RuleOverride;
import com.sentinel.audit.pack.SystemTuning;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Lê e escreve {@link SystemTuning} como JSON, validando tudo que vem de fora. */
public final class TuningJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final int MAX_RULES = 200;

    private TuningJson() {}

    public static String write(SystemTuning t) {
        ObjectNode n = MAPPER.createObjectNode();
        ArrayNode rules = n.putArray("rules");
        t.rules().forEach((id, o) -> {
            ObjectNode r = rules.addObject();
            r.put("id", id);
            if (o.severity() != null) {
                r.put("severity", o.severity().name());
            }
            r.put("disabled", o.disabled());
            if (o.shadow() != null) {
                r.put("shadow", o.shadow());
            }
        });
        ObjectNode th = n.putObject("thresholds");
        t.thresholds().forEach(th::put);
        strings(n.putArray("enablePacks"), t.enablePacks());
        strings(n.putArray("disablePacks"), t.disablePacks());
        strings(n.putArray("disableAnalyzers"), t.disableAnalyzers());
        if (!t.ignorePaths().isEmpty()) {
            t.ignorePaths().forEach(n.putArray("ignorePaths")::add);
        }
        return n.toString();
    }

    /** @throws IllegalArgumentException com mensagem em português quando algo é inválido */
    public static SystemTuning read(String json) {
        if (json == null || json.isBlank()) {
            return SystemTuning.EMPTY;
        }
        try {
            return parse(MAPPER.readTree(json));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Ajustes ilegíveis");
        }
    }

    public static SystemTuning parse(JsonNode n) {
        if (n == null || !n.isObject()) {
            throw new IllegalArgumentException("Corpo inválido");
        }
        Map<String, RuleOverride> rules = parseRules(n.path("rules"));
        Map<String, Integer> limits = parseLimits(n.path("thresholds"));
        Set<String> enable = strings(n.path("enablePacks"));
        Set<String> disable = strings(n.path("disablePacks"));
        if (enable.stream().anyMatch(disable::contains)) {
            throw new IllegalArgumentException("Um pacote não pode ser ligado e desligado ao mesmo tempo");
        }
        return new SystemTuning(rules, limits, enable, disable, strings(n.path("disableAnalyzers")), paths(n.path("ignorePaths")));
    }

    private static Map<String, RuleOverride> parseRules(JsonNode rs) {
        if (rs.size() > MAX_RULES) {
            throw new IllegalArgumentException("No máximo " + MAX_RULES + " ajustes de regra por sistema");
        }
        Map<String, RuleOverride> rules = new LinkedHashMap<>();
        for (JsonNode r : rs) {
            String id = r.path("id").asText("").trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || id.length() > 200 || !id.matches("[a-z0-9_.*:/\\-]+")) {
                throw new IllegalArgumentException("Id de regra inválido: " + id);
            }
            Severity sev = ruleSeverity(r.path("severity"), id);
            boolean disabled = r.path("disabled").asBoolean(false);
            Boolean shadow = r.path("shadow").isBoolean() ? r.path("shadow").asBoolean() : null;
            if (sev == null && !disabled && shadow == null) {
                continue; // ajuste sem efeito não conta
            }
            rules.put(id, new RuleOverride(sev, disabled, shadow));
        }
        return rules;
    }

    /** Severidade do ajuste; ausente, nula ou em branco vale "a do perfil" ({@code null}). */
    private static Severity ruleSeverity(JsonNode node, String ruleId) {
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return Severity.valueOf(node.asText().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Severidade inválida em " + ruleId + ": " + node.asText());
        }
    }

    private static Map<String, Integer> parseLimits(JsonNode thresholds) {
        Map<String, Integer> limits = new LinkedHashMap<>();
        thresholds.fields().forEachRemaining(e -> {
            if (!ProfileLoader.KNOWN_THRESHOLDS.contains(e.getKey())) {
                throw new IllegalArgumentException("Limite desconhecido: " + e.getKey());
            }
            if (!e.getValue().isInt() || e.getValue().asInt() < 1 || e.getValue().asInt() > 100000) {
                throw new IllegalArgumentException("Limite " + e.getKey() + ": use um inteiro entre 1 e 100000");
            }
            limits.put(e.getKey(), e.getValue().asInt());
        });
        return limits;
    }

    public static final int MAX_IGNORE_PATHS = 50;

    /** Padrões de caminho: texto livre curto, sem quebra de linha nem {@code ..}; mantém a ordem digitada. */
    private static Set<String> paths(JsonNode n) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode x : n) {
            String s = x.asText("").trim();
            if (s.isEmpty()) {
                continue;
            }
            if (s.length() > 200 || s.chars().anyMatch(Character::isISOControl) || s.contains("..")) {
                throw new IllegalArgumentException("Caminho ignorado inválido: " + (s.length() > 60 ? s.substring(0, 60) + "…" : s));
            }
            out.add(s);
        }
        if (out.size() > MAX_IGNORE_PATHS) {
            throw new IllegalArgumentException("No máximo " + MAX_IGNORE_PATHS + " caminhos ignorados por sistema");
        }
        return out;
    }

    private static void strings(ArrayNode out, Set<String> in) {
        in.stream().sorted().forEach(out::add);
    }

    private static Set<String> strings(JsonNode n) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode x : n) {
            String s = x.asText("").trim();
            if (!s.isEmpty() && s.length() <= 100) {
                out.add(s);
            }
        }
        if (out.size() > 100) {
            throw new IllegalArgumentException("Lista grande demais");
        }
        return out;
    }
}
