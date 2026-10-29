package com.sentinel.audit.pack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;

/**
 * O guia de código: as tecnologias (de {@code guia/stacks.yml}, com as boas práticas escritas à mão) e as regras dos pacotes que
 * valem para cada uma. Uma regra entra numa tecnologia quando o id casa com {@code own} (específica) ou {@code also}, ou quando
 * a linguagem dela coincide com {@code languages} e nenhuma outra tecnologia a reivindica em {@code own} (herdada).
 */
public final class GuideBook {

    public record Practice(String title, String doIt, String avoid) {}

    public record Review(boolean docs, boolean team, boolean ai) {}

    public record Stack(String id, String name, String kind, String summary, List<String> languages, List<String> own,
                        List<String> also, List<String> tools, List<String> gaps, List<String> commands,
                        boolean commandsVerified, Review review, List<Practice> practices) {}

    /** Regra de uma tecnologia; {@code own} é verdadeiro quando a regra é específica dela. */
    public record StackRule(GuideCatalog.GuideRule rule, boolean own) {}

    private static final Logger LOG = Logger.getLogger(GuideBook.class);
    private static final String STACKS_RESOURCE = "/guia/stacks.yml";
    private static final int MAX_NO_EXCEPTION_RULES = 8;
    private static final int MAX_TRAPS = 4;

    private GuideBook() {}

    // ------------------------------------------------------------------ tecnologias

    public static List<Stack> stacks() {
        String text = GuideCatalog.resource(STACKS_RESOURCE);
        if (text == null) {
            return List.of();
        }
        try {
            Map<String, Object> root = Yamls.map(Yamls.parse(text));
            List<Object> items = root == null ? null : Yamls.list(root.get("stacks"));
            List<Stack> out = new ArrayList<>();
            if (items != null) {
                for (Object item : items) {
                    Map<String, Object> m = Yamls.map(item);
                    if (m != null && Yamls.str(m, "id") != null) {
                        out.add(toStack(m));
                    }
                }
            }
            return out;
        } catch (RuntimeException e) {
            LOG.warnf("guia/stacks.yml inválido, o guia fica sem tecnologias (%s)", e.getMessage());
            return List.of();
        }
    }

    /** Práticas de processo (commit, PR, revisão), iguais para todas as tecnologias. */
    public static List<Practice> process() {
        String text = GuideCatalog.resource(STACKS_RESOURCE);
        if (text == null) {
            return List.of();
        }
        try {
            Map<String, Object> root = Yamls.map(Yamls.parse(text));
            return root == null ? List.of() : practices(root, "process");
        } catch (RuntimeException e) {
            LOG.warnf("guia/stacks.yml inválido, o guia fica sem processo (%s)", e.getMessage());
            return List.of();
        }
    }

    private static Stack toStack(Map<String, Object> m) {
        List<String> problems = new ArrayList<>();
        String id = Yamls.str(m, "id");
        Map<String, Object> review = Yamls.map(m.get("review"));
        return new Stack(id, orElse(Yamls.str(m, "name"), id), orElse(Yamls.str(m, "kind"), "backend"), orElse(Yamls.str(m, "summary"), ""),
                strings(m, "languages", problems), strings(m, "own", problems), strings(m, "also", problems),
                strings(m, "tools", problems), strings(m, "gaps", problems), strings(m, "commands", problems),
                Yamls.bool(m, "commandsVerified", false),
                new Review(review != null && Yamls.bool(review, "docs", false), review != null && Yamls.bool(review, "team", false),
                        review != null && Yamls.bool(review, "ai", false)),
                practices(m, "practices"));
    }

    private static List<String> strings(Map<String, Object> m, String key, List<String> problems) {
        List<String> v = Yamls.strings(m, key, problems, "stack");
        return v == null ? List.of() : v;
    }

    private static List<Practice> practices(Map<String, Object> m, String key) {
        List<Object> items = Yamls.list(m.get(key));
        List<Practice> out = new ArrayList<>();
        if (items != null) {
            for (Object item : items) {
                Map<String, Object> p = Yamls.map(item);
                if (p != null && Yamls.str(p, "title") != null) {
                    out.add(new Practice(Yamls.str(p, "title"), orElse(Yamls.str(p, "do"), ""), orElse(Yamls.str(p, "avoid"), "")));
                }
            }
        }
        return out;
    }

    private static String orElse(String value, String fallback) {
        return value == null ? fallback : value;
    }

    // ------------------------------------------------------------------ regras por tecnologia

    /** Regras que valem para a tecnologia: as específicas primeiro, depois as herdadas. */
    public static List<StackRule> rulesOf(Stack stack, List<Stack> all, List<GuideCatalog.GuideRule> rules) {
        List<StackRule> out = new ArrayList<>();
        for (GuideCatalog.GuideRule r : rules) {
            if (matchesAny(r.id(), stack.own())) {
                out.add(new StackRule(r, true));
            } else if (matchesAny(r.id(), stack.also()) || (sharesLanguage(r, stack) && !ownedByOther(r, stack, all))) {
                out.add(new StackRule(r, false));
            }
        }
        out.sort(Comparator.comparing((StackRule s) -> !s.own()).thenComparing(s -> s.rule().id()));
        return out;
    }

    private static boolean sharesLanguage(GuideCatalog.GuideRule r, Stack stack) {
        for (String l : r.languages()) {
            if (stack.languages().contains(l)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownedByOther(GuideCatalog.GuideRule r, Stack stack, List<Stack> all) {
        for (Stack other : all) {
            if (!other.id().equals(stack.id()) && matchesAny(r.id(), other.own())) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesAny(String id, List<String> globs) {
        for (String glob : globs) {
            if (matches(id, glob)) {
                return true;
            }
        }
        return false;
    }

    /** Casamento com {@code *} em qualquer posição; o resto do texto vale literalmente. */
    static boolean matches(String id, String glob) {
        StringBuilder regex = new StringBuilder();
        for (String part : glob.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return id.matches(regex.toString());
    }

    // ------------------------------------------------------------------ CLAUDE.md

    /** CLAUDE.md enxuto da tecnologia: comandos, convenções, armadilhas, o que não se negocia, limites e modo de trabalho. */
    public static String claudeMd(Stack stack, List<StackRule> rules) {
        List<String> l = new ArrayList<>();
        l.add("# Regras de código — " + stack.name());
        l.add("");
        l.add("Este sistema é auditado pelo Sentinel. Aqui fica só o que não dá para deduzir do código.");
        l.add("");
        l.add("## Verifique antes de dizer que terminou");
        l.add("");
        for (String c : stack.commands()) {
            l.add("- `" + c + "`");
        }
        l.add("- Só diga que terminou com tudo isso passando; se algo falhar, corrija ou diga o que falhou.");
        l.add("");
        l.add("## Convenções");
        l.add("");
        for (Practice p : stack.practices()) {
            l.add("- " + p.title() + ": " + p.doIt());
        }
        l.add("");
        l.add("## Armadilhas");
        l.add("");
        stack.practices().stream().limit(MAX_TRAPS).forEach(p -> l.add("- " + p.avoid()));
        l.add("- Não copie padrões do código legado para código novo.");
        l.add("");
        List<StackRule> high = rules.stream()
                .filter(s -> "alta".equals(s.rule().severity()) && !s.rule().action().isEmpty())
                .limit(MAX_NO_EXCEPTION_RULES)
                .toList();
        if (!high.isEmpty()) {
            l.add("## Sem exceção (severidade alta no Sentinel)");
            l.add("");
            for (StackRule s : high) {
                l.add("- " + s.rule().title() + " → " + s.rule().action());
            }
            l.add("");
        }
        l.add("## Limites");
        l.add("");
        l.add("- Função: até 60 linhas, complexidade 12, aninhamento 4. Até 5 parâmetros.");
        l.add("- Classe: até 500 linhas. Sem números mágicos.");
        l.add("");
        l.add("## Como trabalhar");
        l.add("");
        l.add("- Faça a solução mais simples que resolve o problema de hoje.");
        l.add("- Todo comportamento novo vem com teste que falha se o código quebrar.");
        l.add("- Toda chamada remota tem timeout; repita só operação idempotente, com espera exponencial e jitter.");
        l.add("- Exceção a uma regra: registre no `.sentinel.yml` com motivo e prazo.");
        l.add("");
        l.add("## Commits e revisão");
        l.add("");
        l.add("- Commit: `tipo(escopo): descrição` no imperativo, até 72 caracteres (feat, fix, docs, refactor, test, chore). O corpo explica o porquê.");
        l.add("- Um assunto por mudança; refatoração separada de comportamento.");
        l.add("- Se algo ficou fora do escopo, abra uma issue em vez de deixar \"depois eu arrumo\".");
        l.add("- Ao revisar, comente o código (não a pessoa) e marque `Nit:`, `Opcional:` ou `FYI:`.");
        return String.join("\n", l) + "\n";
    }
}
