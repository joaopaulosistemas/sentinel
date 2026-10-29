package com.sentinel.audit.analyzer;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.regex.Pattern;

/** Casamento de caminhos e pacotes por glob usado pelas políticas de arquitetura. */
final class ArchGlobs {
    private ArchGlobs() {
    }

    static boolean matchesAny(List<PathMatcher> ms, String rel) {
        if (ms == null) {
            return false;
        }
        Path p = Path.of(rel);
        Path prefixed = Path.of("_", rel);          // faz "**/x/**" casar também com "x/..." na raiz
        for (PathMatcher m : ms) {
            if (m.matches(p) || m.matches(prefixed)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesAnyPackage(List<Pattern> ps, String name) {
        if (ps == null) {
            return false;
        }
        for (Pattern p : ps) {
            if (p.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Glob simples ({@code *} = qualquer coisa), sem diferenciar maiúsculas. */
    static boolean globMatches(String glob, String value) {
        return toPattern(glob, true).matcher(value).matches();
    }

    static Pattern toPattern(String glob, boolean ignoreCase) {
        String[] parts = glob.split("\\*", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            sb.append(Pattern.quote(parts[i]));
            if (i < parts.length - 1) {
                sb.append(".*");
            }
        }
        return Pattern.compile(sb.toString(), ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
    }
}
