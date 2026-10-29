package com.sentinel.audit.analyzer.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mensagens de commit: a primeira linha precisa dizer o que mudou, no padrão Conventional Commits
 * ({@code tipo(escopo): descrição}), curta e sem descrição vazia de sentido ("wip", "ajustes"). Merge, revert e
 * fixup/squash ficam de fora: são gerados pelo Git. O histórico vem de {@code git log --pretty=format:%H%x1f%s}.
 */
public final class CommitMessages {

    private CommitMessages() {}

    public static final int MAX_SUBJECT = 100;
    private static final char SEP = '\u001f';
    private static final Pattern CONVENTIONAL = Pattern.compile(
            "^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\\([^)\\s]+\\))?!?: (\\S.*)$");
    private static final Set<String> VAGUE = Set.of("wip", "update", "updates", "fix", "fixes", "change", "changes", "stuff",
            "ajuste", "ajustes", "correcao", "correção", "teste", "tmp", "misc", "x", "...", ".");

    public record Commit(String sha, String subject) {}

    /** Resultado: commits avaliados, os que fogem do padrão e alguns exemplos "sha curto: assunto — motivo". */
    public record Report(int checked, int bad, List<String> examples) {
        public int percent() {
            return checked == 0 ? 0 : bad * 100 / checked;
        }
    }

    /** Lê a saída do {@code git log}; linha sem separador é ignorada. */
    public static List<Commit> parse(String output) {
        List<Commit> out = new ArrayList<>();
        if (output != null) {
            for (String line : output.split("\\R")) {
                int sep = line.indexOf(SEP);
                if (sep > 0) {
                    out.add(new Commit(line.substring(0, sep), line.substring(sep + 1).strip()));
                }
            }
        }
        return out;
    }

    /** Motivo pelo qual o assunto foge do padrão, ou {@code null} se está bom ou se não se avalia (merge, revert, fixup). */
    public static String problem(String subject) {
        if (subject == null || subject.isBlank()) {
            return "assunto vazio";
        }
        if (subject.startsWith("Merge ") || subject.startsWith("Revert \"") || subject.startsWith("fixup!")
                || subject.startsWith("squash!")) {
            return null;
        }
        var m = CONVENTIONAL.matcher(subject);
        if (!m.matches()) {
            return "fora do padrão tipo(escopo): descrição";
        }
        if (VAGUE.contains(m.group(3).strip().toLowerCase(Locale.ROOT))) {
            return "descrição sem informação";
        }
        return subject.length() > MAX_SUBJECT ? "mais de " + MAX_SUBJECT + " caracteres" : null;
    }

    public static Report check(List<Commit> commits, int maxExamples) {
        int checked = 0;
        int bad = 0;
        List<String> examples = new ArrayList<>();
        for (Commit c : commits) {
            if (c.subject().startsWith("Merge ") || c.subject().startsWith("Revert \"") || c.subject().startsWith("fixup!")
                    || c.subject().startsWith("squash!")) {
                continue;
            }
            checked++;
            String why = problem(c.subject());
            if (why != null) {
                bad++;
                if (examples.size() < maxExamples) {
                    examples.add(c.sha().substring(0, Math.min(7, c.sha().length())) + ": " + c.subject() + " — " + why);
                }
            }
        }
        return new Report(checked, bad, examples);
    }
}
