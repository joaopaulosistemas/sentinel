package com.sentinel.audit.analyzer.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pontos quentes: arquivos que mudam muito e têm função complexa. Complexidade alta num arquivo que ninguém toca
 * custa pouco; num arquivo que muda toda semana custa a cada mudança. O histórico vem de {@code git log}.
 */
public final class Hotspots {

    private Hotspots() {}

    /** Resultado do histórico: commits lidos e quantas vezes cada arquivo apareceu neles. */
    public record Log(int commits, Map<String, Integer> churn) {}

    /** Maior complexidade do arquivo e a função onde ela está. */
    public record Cx(int complexity, String function, int line) {}

    public record Hotspot(String file, int churn, Cx cx, long score) {}

    private static final Pattern SHA = Pattern.compile("^[0-9a-f]{40}$");

    /** Lê a saída de {@code git log --name-only --no-merges --pretty=format:%H}. */
    public static Log parseLog(String output) {
        int commits = 0;
        Map<String, Integer> churn = new HashMap<>();
        if (output != null) {
            for (String raw : output.split("\\R")) {
                String line = raw.strip();
                if (line.isEmpty()) {
                    continue;
                }
                if (SHA.matcher(line).matches()) {
                    commits++;
                } else {
                    churn.merge(line, 1, Integer::sum);
                }
            }
        }
        return new Log(commits, churn);
    }

    /** Arquivos com pelo menos {@code minChurn} mudanças e função acima de {@code minComplexity}, do pior para o melhor. */
    public static List<Hotspot> rank(Map<String, Integer> churn, Map<String, Cx> complexity, int minChurn, int minComplexity, int top) {
        List<Hotspot> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : churn.entrySet()) {
            Cx cx = complexity.get(e.getKey());
            if (cx != null && e.getValue() >= minChurn && cx.complexity() > minComplexity) {
                out.add(new Hotspot(e.getKey(), e.getValue(), cx, (long) e.getValue() * cx.complexity()));
            }
        }
        out.sort(Comparator.comparingLong(Hotspot::score).reversed().thenComparing(Hotspot::file));
        return out.size() > top ? new ArrayList<>(out.subList(0, top)) : out;
    }
}
