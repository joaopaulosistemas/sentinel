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
        String early = inputProblem(slug, yaml);
        if (early != null) {
            problems.add(early);
            return problems;
        }
        Object root = parse(yaml);
        if (root == UNREADABLE) {
            problems.add("YAML ilegível.");
            return problems;
        }
        Map<?, ?> rule = singleRule(root);
        if (rule == null) {
            problems.add("Esperava exatamente uma regra dentro de 'rules:'.");
            return problems;
        }
        checkFields(slug, rule, problems);
        checkRegexes(rule, problems);
        return problems;
    }

    private static final Object UNREADABLE = new Object();

    /** O problema mais básico (id, vazio, tamanho), ou nulo se a entrada tem forma para ser lida. */
    private static String inputProblem(String slug, String yaml) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            return "Id inválido: use letras minúsculas, números e hífen (3 a 60 caracteres).";
        }
        if (yaml == null || yaml.isBlank()) {
            return "A regra Semgrep está vazia.";
        }
        return yaml.length() > MAX_YAML ? "A regra passa de " + MAX_YAML + " caracteres." : null;
    }

    private static Object parse(String yaml) {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setCodePointLimit(MAX_YAML * 2);
            return new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (RuntimeException e) {
            return UNREADABLE;
        }
    }

    /** A única regra dentro de {@code rules:}, ou nulo se o documento não tem esse formato. */
    private static Map<?, ?> singleRule(Object root) {
        if (root instanceof Map<?, ?> top && top.get("rules") instanceof List<?> rules && rules.size() == 1
                && rules.get(0) instanceof Map<?, ?> rule) {
            return rule;
        }
        return null;
    }

    private static void checkFields(String slug, Map<?, ?> rule, List<String> problems) {
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
    }

    private static void checkRegexes(Object node, List<String> problems) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                if ("pattern-regex".equals(key) || "regex".equals(key)) {
                    checkRegex(String.valueOf(e.getValue()), problems);
                }
                checkRegexes(e.getValue(), problems);
            }
        } else if (node instanceof List<?> l) {
            l.forEach(x -> checkRegexes(x, problems));
        }
    }

    private static void checkRegex(String rx, List<String> problems) {
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
}
