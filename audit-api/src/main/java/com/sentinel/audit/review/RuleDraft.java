package com.sentinel.audit.review;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Confere uma regra Semgrep proposta antes de guardá-la. A regra só roda depois de revisada por uma pessoa e
 * aprovada no teste de amostra; aqui o objetivo é recusar o que é perigoso ou fora do formato.
 */
public final class RuleDraft {

    public static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{2,59}");
    public static final String PREFIX = "local-";
    static final int MAX_YAML = 8000;
    static final int MAX_REGEX = 300;
    private static final Set<String> MATCHERS = Set.of("pattern", "patterns", "pattern-either", "pattern-regex");
    private static final Set<String> FORBIDDEN = Set.of("fix", "fix-regex", "options", "metavariable-analysis");
    private static final Set<String> SEVERITIES = Set.of("ERROR", "WARNING", "INFO");
    /** Repetição dentro de repetição, como {@code (a+)+}: base do ReDoS. */
    private static final Pattern NESTED_QUANTIFIER = Pattern.compile("\\([^)]*[+*][^)]*\\)[+*{]");

    private RuleDraft() {}

    /** Id que a regra tem dentro do Semgrep. */
    public static String semgrepId(String slug) {
        return PREFIX + slug;
    }

    /** @return lista de problemas; vazia quando a regra pode ser guardada */
    public static List<String> validate(String slug, String yaml) {
        List<String> problems = new ArrayList<>();
        if (slug == null || !SLUG.matcher(slug).matches()) {
            problems.add("Id inválido: use letras minúsculas, números e hífen (3 a 60 caracteres).");
            return problems;
        }
        if (yaml == null || yaml.isBlank()) {
            problems.add("A regra Semgrep está vazia.");
            return problems;
        }
        if (yaml.length() > MAX_YAML) {
            problems.add("A regra passa de " + MAX_YAML + " caracteres.");
            return problems;
        }
        Object root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setCodePointLimit(MAX_YAML * 2);
            root = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (RuntimeException e) {
            problems.add("YAML ilegível.");
            return problems;
        }
        if (!(root instanceof Map<?, ?> top) || !(top.get("rules") instanceof List<?> rules) || rules.size() != 1
                || !(rules.get(0) instanceof Map<?, ?> rule)) {
            problems.add("Esperava exatamente uma regra dentro de 'rules:'.");
            return problems;
        }
        if (!semgrepId(slug).equals(String.valueOf(rule.get("id")))) {
            problems.add("O id dentro da regra deve ser '" + semgrepId(slug) + "'.");
        }
        if (!(rule.get("languages") instanceof List<?> langs) || langs.isEmpty()) {
            problems.add("Falta 'languages'.");
        }
        if (!(rule.get("message") instanceof String m) || m.isBlank()) {
            problems.add("Falta 'message'.");
        }
        if (!SEVERITIES.contains(String.valueOf(rule.get("severity")))) {
            problems.add("'severity' deve ser ERROR, WARNING ou INFO.");
        }
        long matchers = rule.keySet().stream().filter(k -> MATCHERS.contains(String.valueOf(k))).count();
        if (matchers != 1) {
            problems.add("Use exatamente um de: pattern, patterns, pattern-either, pattern-regex.");
        }
        for (Object k : rule.keySet()) {
            if (FORBIDDEN.contains(String.valueOf(k))) {
                problems.add("O campo '" + k + "' não é permitido em regra proposta.");
            }
        }
        checkRegexes(rule, problems);
        return problems;
    }

    private static void checkRegexes(Object node, List<String> problems) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if ("pattern-regex".equals(String.valueOf(e.getKey())) || "regex".equals(String.valueOf(e.getKey()))) {
                    String rx = String.valueOf(e.getValue());
                    if (rx.length() > MAX_REGEX) {
                        problems.add("Expressão regular com mais de " + MAX_REGEX + " caracteres.");
                    } else if (NESTED_QUANTIFIER.matcher(rx).find()) {
                        problems.add("Expressão regular com repetição dentro de repetição (risco de travar a análise).");
                    } else {
                        try {
                            Pattern.compile(rx);
                        } catch (RuntimeException ex) {
                            problems.add("Expressão regular inválida.");
                        }
                    }
                }
                checkRegexes(e.getValue(), problems);
            }
        } else if (node instanceof List<?> l) {
            l.forEach(x -> checkRegexes(x, problems));
        }
    }
}
