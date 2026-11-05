package com.sentinel.audit.pack;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * De que lado da aplicação uma regra trata: back-end, front-end ou comum (Docker, Kubernetes e o que vale para qualquer
 * linguagem). A regra pode dizer o lado em {@code metadata.side}; sem isso o lado sai da linguagem e dos caminhos dela.
 */
public enum RuleSide {
    BACK("back"), FRONT("front"), COMMON("common");

    private static final Set<String> BACK_LANGUAGES = Set.of("java", "kotlin", "groovy", "scala");
    private static final Set<String> FRONT_LANGUAGES = Set.of("javascript", "typescript", "js", "ts", "tsx", "jsx");
    private static final Pattern INFRA = Pattern.compile("docker|compose|k8s|kubernetes|helm|\\.github|terraform|\\.tf", Pattern.CASE_INSENSITIVE);
    private static final List<String> FRONT_EXTENSIONS = List.of(".html", ".htm", ".scss", ".sass", ".less", ".css", ".tsx", ".jsx",
            ".vue", ".ts", ".js", ".mjs", ".cjs", ".component");
    private static final List<String> BACK_EXTENSIONS = List.of(".java", ".kt", ".groovy", ".scala", ".xml", ".properties", ".sql",
            ".gradle", ".jsp");

    private final String id;

    RuleSide(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** O lado escrito no YAML, ou {@code null} se vazio ou desconhecido. */
    public static RuleSide parse(String text) {
        if (text == null) {
            return null;
        }
        String t = text.strip().toLowerCase(Locale.ROOT);
        for (RuleSide s : values()) {
            if (s.id.equals(t)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Lado deduzido de {@code languages} e {@code paths.include}. Só linguagens do back-end (ou só do front-end) decidem;
     * regra de texto ("regex") decide pelas extensões dos caminhos; Docker, Kubernetes e Compose são comuns; dúvida é comum.
     */
    public static RuleSide infer(List<String> languages, List<String> includes) {
        if (languages != null && !languages.isEmpty()) {
            List<String> langs = languages.stream().map(l -> l.strip().toLowerCase(Locale.ROOT)).toList();
            if (BACK_LANGUAGES.containsAll(langs)) {
                return BACK;
            }
            if (langs.size() == 1 && langs.get(0).equals("dockerfile")) {
                return COMMON;
            }
            if (FRONT_LANGUAGES.containsAll(langs)) {
                return FRONT;
            }
        }
        boolean front = false;
        boolean back = false;
        if (includes != null) {
            for (String glob : includes) {
                String g = glob.strip().toLowerCase(Locale.ROOT);
                if (INFRA.matcher(g).find()) {
                    return COMMON;
                }
                front |= FRONT_EXTENSIONS.stream().anyMatch(g::endsWith);
                back |= BACK_EXTENSIONS.stream().anyMatch(g::endsWith);
            }
        }
        if (front && !back) {
            return FRONT;
        }
        if (back && !front) {
            return BACK;
        }
        return COMMON;
    }

    /** Lado de um arquivo pela extensão: {@code null} quando não é de código de um lado só (Dockerfile, YAML, Markdown). */
    public static RuleSide ofPath(String path) {
        if (path == null) {
            return null;
        }
        String p = path.strip().toLowerCase(Locale.ROOT);
        boolean front = FRONT_EXTENSIONS.stream().anyMatch(p::endsWith);
        boolean back = BACK_EXTENSIONS.stream().anyMatch(p::endsWith);
        return front && !back ? FRONT : (back && !front ? BACK : null);
    }
}
