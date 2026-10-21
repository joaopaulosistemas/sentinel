package com.sentinel.audit.pack;

import com.sentinel.audit.model.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Lê {@code profiles/*.yml} na raiz de pacotes. Um perfil por arquivo. */
public final class ProfileLoader {

    /** Limites que o CleanCodeAnalyzer entende; qualquer outro nome é erro de digitação. */
    public static final Set<String> KNOWN_THRESHOLDS = Set.of("max-function-lines", "max-component-lines",
            "max-parameters", "max-complexity", "max-nesting", "max-findings");

    private ProfileLoader() {}

    public static Map<String, Profile> loadAll(Path root, Map<String, List<String>> rejected) {
        Map<String, Profile> out = new LinkedHashMap<>();
        Path dir = root == null ? null : root.resolve("profiles");
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> s = Files.list(dir)) {
            for (Path f : s.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p)
                    && (p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))).sorted().toList()) {
                List<String> errors = new ArrayList<>();
                Profile p = parse(f, errors);
                if (p != null && errors.isEmpty()) {
                    if (out.containsKey(p.id())) {
                        errors.add("perfil '" + p.id() + "' repetido");
                    } else {
                        out.put(p.id(), p);
                    }
                }
                if (!errors.isEmpty()) {
                    rejected.put("profiles/" + f.getFileName(), errors);
                }
            }
        } catch (IOException e) {
            rejected.put("profiles", List.of("não foi possível listar: " + e.getMessage()));
        }
        return out;
    }

    static Profile parse(Path file, List<String> errors) {
        String where = file.getFileName().toString();
        Map<String, Object> m;
        try {
            if (Files.size(file) > PackLoader.MAX_FILE_BYTES) {
                errors.add(where + ": arquivo maior que 1 MB");
                return null;
            }
            m = Yamls.map(Yamls.parse(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            errors.add(where + ": YAML inválido: " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return null;
        }
        if (m == null) {
            errors.add(where + ": esperava um mapa de chaves");
            return null;
        }
        for (String k : m.keySet()) {
            if (!Set.of("id", "name", "description", "extends", "packs", "disable-packs", "rules", "thresholds").contains(k)) {
                errors.add(where + ": chave desconhecida '" + k + "'");
            }
        }
        String id = Yamls.str(m, "id");
        if (id == null || !PackManifest.ID.matcher(id).matches()) {
            errors.add(where + ": 'id' ausente ou inválido (minúsculas, números e hífen)");
            return null;
        }
        List<String> packs = Yamls.strings(m, "packs", errors, where);
        List<String> disable = Yamls.strings(m, "disable-packs", errors, where);

        Map<String, RuleOverride> rules = new LinkedHashMap<>();
        Map<String, Object> rawRules = Yamls.map(m.get("rules"));
        if (m.get("rules") != null && rawRules == null) {
            errors.add(where + ": 'rules' deve ser um mapa id-da-regra -> ajustes");
        }
        if (rawRules != null) {
            for (Map.Entry<String, Object> e : rawRules.entrySet()) {
                String w = where + " rules." + e.getKey();
                Map<String, Object> o = Yamls.map(e.getValue());
                if (o == null) {
                    errors.add(w + ": esperava {severity, disabled, shadow}");
                    continue;
                }
                for (String k : o.keySet()) {
                    if (!Set.of("severity", "disabled", "shadow").contains(k)) {
                        errors.add(w + ": chave desconhecida '" + k + "'");
                    }
                }
                Severity sev = null;
                String sv = Yamls.str(o, "severity");
                if (sv != null) {
                    try {
                        sev = Severity.valueOf(sv.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        errors.add(w + ".severity: inválida '" + sv + "'");
                    }
                }
                Boolean shadow = o.get("shadow") instanceof Boolean b ? b : null;
                if (o.get("shadow") != null && shadow == null) {
                    errors.add(w + ".shadow: use true ou false");
                }
                rules.put(e.getKey().toLowerCase(Locale.ROOT), new RuleOverride(sev, Yamls.bool(o, "disabled", false), shadow));
            }
        }

        Map<String, Integer> thresholds = new LinkedHashMap<>();
        Map<String, Object> rawT = Yamls.map(m.get("thresholds"));
        if (m.get("thresholds") != null && rawT == null) {
            errors.add(where + ": 'thresholds' deve ser um mapa");
        }
        if (rawT != null) {
            for (Map.Entry<String, Object> e : rawT.entrySet()) {
                if (!KNOWN_THRESHOLDS.contains(e.getKey())) {
                    errors.add(where + " thresholds: limite desconhecido '" + e.getKey() + "' (conhecidos: "
                            + String.join(", ", new java.util.TreeSet<>(KNOWN_THRESHOLDS)) + ")");
                } else if (!(e.getValue() instanceof Number n) || n.intValue() < 1) {
                    errors.add(where + " thresholds." + e.getKey() + ": use um inteiro maior que zero");
                } else {
                    thresholds.put(e.getKey(), n.intValue());
                }
            }
        }
        return new Profile(id, Yamls.str(m, "name") == null ? id : Yamls.str(m, "name"), Yamls.str(m, "description"),
                Yamls.str(m, "extends"), packs, disable == null ? List.of() : disable, rules, thresholds);
    }
}
