package com.sentinel.audit.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Curingas de caminho no estilo do .gitignore, usados para tirar da auditoria arquivos que o time decidiu não avaliar
 * (fixtures de teste, documentação, arquivos de regras).
 * <ul>
 *   <li>{@code *} casa qualquer coisa menos {@code /}; {@code **} casa qualquer coisa, inclusive {@code /}; {@code ?} casa um caractere;</li>
 *   <li>terminando em {@code /}, vale para a pasta e tudo dentro dela;</li>
 *   <li>começando em {@code /}, vale a partir da raiz do projeto;</li>
 *   <li>sem {@code /} (fora o do fim), vale em qualquer pasta: {@code *.md} pega {@code docs/a.md} e {@code a.md}.</li>
 * </ul>
 */
public final class PathGlobs {

    private PathGlobs() {}

    /** Normaliza para barra comum e sem {@code ./} no começo. */
    static String normalize(String path) {
        String p = path == null ? "" : path.replace('\\', '/').strip();
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p.startsWith("/") ? p.substring(1) : p;
    }

    /** Verdadeiro para os caminhos que casam com algum dos padrões. Sem padrões, nunca casa. */
    public static Predicate<String> matcher(Set<String> patterns) {
        List<Pattern> compiled = new ArrayList<>();
        for (String raw : patterns) {
            String p = raw == null ? "" : raw.strip().replace('\\', '/');
            if (p.isEmpty() || p.startsWith("#")) {
                continue;
            }
            compiled.add(Pattern.compile(toRegex(p)));
        }
        if (compiled.isEmpty()) {
            return path -> false;
        }
        return path -> {
            String n = normalize(path);
            for (Pattern c : compiled) {
                if (c.matcher(n).matches()) {
                    return true;
                }
            }
            return false;
        };
    }

    static String toRegex(String pattern) {
        boolean anchored = pattern.startsWith("/");
        String p = anchored ? pattern.substring(1) : pattern;
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        boolean anywhere = !anchored && !p.contains("/");
        StringBuilder re = new StringBuilder();
        if (anywhere) {
            re.append("(?:.*/)?");
        }
        int i = 0;
        while (i < p.length()) {
            char c = p.charAt(i);
            if (c == '*') {
                i = appendStar(p, i, re);
            } else {
                re.append(c == '?' ? "[^/]" : Pattern.quote(String.valueOf(c)));
            }
            i++;
        }
        // casou uma pasta: vale também para tudo dentro dela (como no .gitignore)
        re.append("(?:/.*)?");
        return re.toString();
    }

    /** Traduz o {@code *} (ou {@code **}) em {@code i}; devolve o índice do último caractere consumido. */
    private static int appendStar(String p, int i, StringBuilder re) {
        if (i + 1 >= p.length() || p.charAt(i + 1) != '*') {
            re.append("[^/]*");
            return i;
        }
        int next = i + 1;
        // "**/" também casa "nada" (a/**/b casa a/b)
        if (next + 1 < p.length() && p.charAt(next + 1) == '/') {
            next++;
            re.append("(?:.*/)?");
        } else {
            re.append(".*");
        }
        return next;
    }
}
