package com.sentinel.audit.pack;

import com.sentinel.audit.model.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Junta o mesmo problema reportado por ferramentas diferentes (ex.: a regra Semgrep do Sentinel e o Trivy
 * apontando o mesmo "Dockerfile sem USER"), para ele não pesar duas vezes no score.
 * <p>
 * Só se juntam problemas que <b>sabidamente</b> são o mesmo: cada "tópico" abaixo lista os ids de regra
 * equivalentes de cada ferramenta. Regra desconhecida nunca é tocada — errar para o lado de manter duplicata
 * é melhor do que esconder um problema distinto. Para cobrir mais casos, acrescente um tópico em {@code dedup.yml} de um pacote.
 * <p>
 * Ao juntar, fica o finding da ferramenta preferida ({@link #PREFERRED_TOOL}: tem a orientação de correção
 * do catálogo e id de regra estável), com a maior severidade entre os dois e uma nota citando a outra ferramenta.
 * O {@code ruleId} do que fica nunca muda, para o fingerprint (acompanhamento entre auditorias) não se alterar.
 */
public final class FindingDeduplicator {

    private FindingDeduplicator() {}

    static final String PREFERRED_TOOL = "Semgrep";

    private static volatile List<DedupTopic> topics = List.of();

    /** Substitui as equivalências conhecidas (vêm de {@code dedup.yml} dos pacotes, instaladas pelo PackRegistry). */
    public static void install(List<DedupTopic> list) {
        topics = List.copyOf(list);
    }

    /** Devolve nova lista sem as duplicatas; a original não é alterada (os findings mantidos podem ser enriquecidos). */
    public static List<Finding> apply(List<Finding> findings) {
        return apply(findings, topics);
    }

    public static List<Finding> apply(List<Finding> findings, List<DedupTopic> rules) {
        List<Finding> removed = new ArrayList<>();
        for (DedupTopic topic : rules) {
            List<Finding> inTopic = findings.stream().filter(f -> topicOf(f, rules) == topic).toList();
            for (Finding candidate : inTopic) {
                if (removed.contains(candidate) || isPreferred(candidate, topic)) {
                    continue;
                }
                Finding anchor = inTopic.stream()
                        .filter(a -> isPreferred(a, topic) && !removed.contains(a))
                        .filter(a -> sameFile(a, candidate) && near(a, candidate, topic.maxLines()))
                        .findFirst().orElse(null);
                if (anchor != null) {
                    merge(anchor, candidate);
                    removed.add(candidate);
                }
            }
        }
        List<Finding> result = new ArrayList<>(findings.size());
        for (Finding f : findings) {
            if (!removed.contains(f)) {
                result.add(f);
            }
        }
        return result;
    }

    private static DedupTopic topicOf(Finding f, List<DedupTopic> rules) {
        String rule = f.getRuleId() == null ? "" : f.getRuleId().toLowerCase(Locale.ROOT);
        if (rule.isEmpty()) {
            return null;
        }
        for (DedupTopic t : rules) {
            for (String m : t.rules()) {
                if (rule.contains(m)) {
                    return t;
                }
            }
        }
        return null;
    }

    /** O achado que fica é o da ferramenta que o tópico prefere (padrão: {@link #PREFERRED_TOOL}). */
    private static boolean isPreferred(Finding f, DedupTopic topic) {
        String tool = topic.prefer() == null || topic.prefer().isBlank() ? PREFERRED_TOOL : topic.prefer();
        return tool.equalsIgnoreCase(f.getToolSource());
    }

    private static boolean sameFile(Finding a, Finding b) {
        String fa = a.getFilePath() == null ? "" : a.getFilePath();
        String fb = b.getFilePath() == null ? "" : b.getFilePath();
        return !fa.isEmpty() && fa.equals(fb);
    }

    /** Linha 0/ausente significa "sem posição": não impede a junção. */
    private static boolean near(Finding a, Finding b, int maxLines) {
        if (maxLines < 0) {
            return true;
        }
        int la = a.getLineNumber() == null ? 0 : a.getLineNumber();
        int lb = b.getLineNumber() == null ? 0 : b.getLineNumber();
        return la == 0 || lb == 0 || Math.abs(la - lb) <= maxLines;
    }

    private static void merge(Finding kept, Finding dropped) {
        if (dropped.getSeverity() != null && (kept.getSeverity() == null
                || dropped.getSeverity().ordinal() < kept.getSeverity().ordinal())) {
            kept.setSeverity(dropped.getSeverity());        // Severity vem ordenada do mais grave para o menos grave
        }
        String note = " Também detectado por " + dropped.getToolSource()
                + (dropped.getRuleId() == null ? "" : " (" + dropped.getRuleId() + ")") + ".";
        String desc = kept.getDescription() == null ? "" : kept.getDescription();
        if (!desc.contains(note)) {
            kept.setDescription(desc + note);
        }
    }
}
