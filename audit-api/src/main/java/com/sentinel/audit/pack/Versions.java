package com.sentinel.audit.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comparação de versões para a política de componentes. Compara só a parte numérica (1.2.3-SNAPSHOT = 1.2.3):
 * suficiente para "versão mínima". Versão que não dá para interpretar (variável Maven, intervalo) devolve
 * {@code null}: na dúvida, nenhum finding é gerado.
 */
public final class Versions {

    private static final Pattern NUMERIC = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)");
    private static final Pattern SPEC = Pattern.compile("^(>=|<=|>|<|=)?\\s*(.+)$");

    private Versions() {}

    /** Partes numéricas iniciais da versão, ou {@code null} se ela não começa com número. */
    static List<Integer> parse(String version) {
        if (version == null) {
            return null;
        }
        Matcher m = NUMERIC.matcher(version.strip());
        if (!m.find()) {
            return null;
        }
        List<Integer> parts = new ArrayList<>();
        for (String p : m.group(1).split("\\.")) {
            try {
                parts.add(Integer.parseInt(p));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return parts;
    }

    static int compare(List<Integer> a, List<Integer> b) {
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int x = i < a.size() ? a.get(i) : 0;
            int y = i < b.size() ? b.get(i) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    /**
     * @param spec {@code >=2.17.1}, {@code >1}, {@code <=3}, {@code <6}, {@code =1.2} ou {@code 1.2} (igual),
     *             {@code 1.*} (prefixo)
     * @return se a versão satisfaz a regra; {@code null} se a versão ou a regra não puderem ser interpretadas
     */
    public static Boolean satisfies(String version, String spec) {
        List<Integer> v = parse(version);
        if (v == null || spec == null) {
            return null;
        }
        Matcher m = SPEC.matcher(spec.strip());
        if (!m.matches()) {
            return null;
        }
        String op = m.group(1) == null ? "=" : m.group(1);
        String target = m.group(2).strip();
        if (target.endsWith(".*")) {
            return matchesPrefix(v, target.substring(0, target.length() - 2), op);
        }
        List<Integer> t = parse(target);
        if (t == null) {
            return null;
        }
        return holds(op, compare(v, t));
    }

    /** O operador vale para o resultado da comparação (negativo, zero ou positivo)? */
    private static boolean holds(String op, int c) {
        return switch (op) {
            case ">=" -> c >= 0;
            case ">" -> c > 0;
            case "<=" -> c <= 0;
            case "<" -> c < 0;
            default -> c == 0;
        };
    }

    /** {@code 1.*}: a versão começa pelos mesmos componentes; só vale com o operador igual. */
    private static Boolean matchesPrefix(List<Integer> v, String prefixText, String op) {
        List<Integer> prefix = parse(prefixText);
        if (prefix == null || !"=".equals(op)) {
            return null;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (i >= v.size() || !v.get(i).equals(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }
}
