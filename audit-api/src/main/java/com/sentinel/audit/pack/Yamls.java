package com.sentinel.audit.pack;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitura de YAML <b>segura</b> (só tipos básicos: nada de instanciar classes, que é o risco clássico do SnakeYAML)
 * com limites contra arquivos malucos, e acessores tolerantes que registram o problema em vez de lançar exceção.
 */
final class Yamls {

    private static final int MAX_ALIASES = 20;
    private static final int CODE_POINT_LIMIT = 2_000_000;


    private Yamls() {}

    static Object parse(String text) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(MAX_ALIASES);
        options.setCodePointLimit(CODE_POINT_LIMIT);
        return new Yaml(new SafeConstructor(options)).load(text);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        if (!(o instanceof Map<?, ?> m)) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    static List<Object> list(Object o) {
        return o instanceof List<?> l ? new ArrayList<>(l) : null;
    }

    /** Texto do campo, ou {@code null} se ausente/vazio. Números e booleanos viram texto. */
    static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).strip();
        return s.isEmpty() ? null : s;
    }

    /** Lista de textos; aceita um texto solto como lista de um item. {@code null} se o campo não existe. */
    static List<String> strings(Map<String, Object> m, String key, List<String> problems, String where) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object item : l) {
                if (item == null || item instanceof Map || item instanceof List) {
                    problems.add(where + "." + key + ": esperava uma lista de textos");
                    return out;
                }
                out.add(String.valueOf(item).strip());
            }
        } else if (v instanceof Map) {
            problems.add(where + "." + key + ": esperava texto ou lista de textos");
        } else {
            out.add(String.valueOf(v).strip());
        }
        return out;
    }

    static int integer(Map<String, Object> m, String key, int fallback, List<String> problems, String where) {
        Object v = m.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        problems.add(where + "." + key + ": esperava um número inteiro, veio '" + v + "'");
        return fallback;
    }

    static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : fallback;
    }
}
