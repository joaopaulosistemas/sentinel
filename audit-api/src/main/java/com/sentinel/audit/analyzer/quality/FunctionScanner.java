package com.sentinel.audit.analyzer.quality;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Localiza métodos/funções em código Java ou TypeScript já "limpo" (veja {@link SourceSanitizer}) e calcula
 * métricas simples de cada um. Não é um parser completo: é uma aproximação deliberada, pensada para errar
 * para o lado de <b>não</b> reportar quando houver dúvida.
 */
public final class FunctionScanner {

    private FunctionScanner() {}

    /** Uma função encontrada. Os índices são posições no texto limpo. */
    public record Fn(String name, int startIdx, int openBrace, int closeBrace, int startLine, int endLine,
                     int paramCount, int codeLines, int complexity, int nesting) {}

    private static final Set<String> NOT_FUNCTIONS = Set.of(
            "if", "for", "while", "switch", "catch", "synchronized", "try", "else", "do", "return", "new", "throw",
            "assert", "function", "super", "this", "with", "await", "typeof", "instanceof", "void", "yield", "in", "of");

    private static final Pattern IDENT_PAREN = Pattern.compile("(?<![\\w$.])([A-Za-z_$][\\w$]*)\\s*(?:<[^()]*?>)?\\s*\\(");
    private static final Pattern JAVA_TAIL = Pattern.compile("\\G\\s*(?:throws\\s+[\\w.$,\\s<>]+?)?\\s*\\{");
    private static final Pattern TS_TAIL = Pattern.compile("\\G\\s*(?::\\s*[^{};=]+?)?\\s*\\{");
    private static final Pattern ARROW_DECL = Pattern.compile(
            "(?<![\\w$.])(?:(?:export\\s+)?(?:const|let|var)\\s+)?([A-Za-z_$][\\w$]*)\\s*(?::[^=;{}]*)?=\\s*"
                    + "(?:async\\s+)?(?:<[^()]*?>\\s*)?(\\(|[A-Za-z_$][\\w$]*\\s*=>)");
    private static final Pattern ARROW_TAIL = Pattern.compile("\\G\\s*(?::\\s*[^{};=]+?)?\\s*=>\\s*\\{");
    private static final Pattern PREV_TOKEN = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*$");

    private static final Pattern DECISION = Pattern.compile("\\b(?:if|for|while|case|catch)\\b|&&|\\|\\|");
    private static final Pattern NULLISH = Pattern.compile("\\?\\?");
    private static final Pattern TERNARY = Pattern.compile("(?<![?<])\\?(?![?.:>,])(?!\\s*(?:extends|super)\\b)");

    /** Encontra as funções de um arquivo. {@code ts} escolhe a sintaxe (false = Java). */
    public static List<Fn> scan(String clean, boolean ts) {
        int[] lineStarts = lineStarts(clean);
        List<Fn> result = new ArrayList<>();
        java.util.Set<Integer> seenBodies = new java.util.HashSet<>();

        Matcher m = IDENT_PAREN.matcher(clean);
        while (m.find()) {
            String name = m.group(1);
            if (NOT_FUNCTIONS.contains(name)) {
                continue;
            }
            String before = clean.substring(Math.max(0, m.start(1) - 40), m.start(1));
            Matcher prev = PREV_TOKEN.matcher(before);
            if (prev.find() && ("new".equals(prev.group(1)) || "record".equals(prev.group(1))
                    || "class".equals(prev.group(1)) || "interface".equals(prev.group(1)))) {
                continue;                                            // classe anônima, record, declaração de tipo
            }
            int open = m.end() - 1;
            int close = matchClosing(clean, open, '(', ')');
            if (close < 0) {
                continue;
            }
            Matcher tail = (ts ? TS_TAIL : JAVA_TAIL).matcher(clean);
            tail.region(close + 1, clean.length());
            if (!tail.lookingAt()) {
                continue;
            }
            add(result, seenBodies, clean, lineStarts, name, m.start(1), tail.end() - 1,
                    clean.substring(open + 1, close), ts);
        }

        if (ts) {
            Matcher a = ARROW_DECL.matcher(clean);
            while (a.find()) {
                String name = a.group(1);
                int idx;
                String params;
                int bodyOpen;
                if ("(".equals(a.group(2))) {
                    int open = a.end() - 1;
                    int close = matchClosing(clean, open, '(', ')');
                    if (close < 0) {
                        continue;
                    }
                    Matcher tail = ARROW_TAIL.matcher(clean);
                    tail.region(close + 1, clean.length());
                    if (!tail.lookingAt()) {
                        continue;
                    }
                    params = clean.substring(open + 1, close);
                    bodyOpen = tail.end() - 1;
                } else {                                             // x => { ... }
                    Matcher tail = Pattern.compile("\\G\\s*\\{").matcher(clean);
                    tail.region(a.end(), clean.length());
                    if (!tail.lookingAt()) {
                        continue;
                    }
                    params = "x";
                    bodyOpen = tail.end() - 1;
                }
                idx = a.start(1);
                add(result, seenBodies, clean, lineStarts, name, idx, bodyOpen, params, true);
            }
        }
        result.sort(java.util.Comparator.comparingInt(Fn::startIdx));
        return result;
    }

    private static void add(List<Fn> out, java.util.Set<Integer> seen, String clean, int[] lineStarts, String name,
                            int startIdx, int openBrace, String params, boolean ts) {
        if (!seen.add(openBrace)) {
            return;
        }
        int closeBrace = matchClosing(clean, openBrace, '{', '}');
        if (closeBrace < 0) {
            return;
        }
        String body = clean.substring(openBrace + 1, closeBrace);
        int startLine = lineOf(lineStarts, startIdx);
        int endLine = lineOf(lineStarts, closeBrace);
        out.add(new Fn(name, startIdx, openBrace, closeBrace, startLine, endLine,
                countParams(params), codeLines(clean, lineStarts, startLine, endLine), complexity(body), nesting(body)));
    }

    // ------------------------------------------------------------------ métricas

    /** Complexidade ciclomática aproximada: 1 + pontos de decisão. */
    static int complexity(String body) {
        return 1 + count(DECISION, body) + count(NULLISH, body) + count(TERNARY, body);
    }

    /** Níveis de blocos aninhados dentro do corpo (o corpo em si não conta). */
    static int nesting(String body) {
        int depth = 0;
        int max = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                depth++;
                max = Math.max(max, depth);
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return max;
    }

    /** Conta parâmetros no nível de topo (vírgulas dentro de <>, (), [], {} não separam parâmetros). */
    static int countParams(String params) {
        if (params == null || params.isBlank()) {
            return 0;
        }
        int depth = 0;
        int count = 1;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '=' && i + 1 < params.length() && params.charAt(i + 1) == '>') {
                i++;                                                 // "=>" não é um '>' de genérico
            } else if (c == '<' || c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']' || c == '}') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                count++;
            }
        }
        // vírgula final ("a, b,") não cria um parâmetro a mais
        return params.stripTrailing().endsWith(",") ? count - 1 : count;
    }

    private static int codeLines(String clean, int[] lineStarts, int startLine, int endLine) {
        int count = 0;
        for (int line = startLine; line <= endLine; line++) {
            int from = lineStarts[line - 1];
            int to = line < lineStarts.length ? lineStarts[line] : clean.length();
            if (!clean.substring(from, to).isBlank()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ utilidades

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Índice do delimitador que fecha o aberto em {@code open}, ou -1 se não fecha. */
    static int matchClosing(String s, int open, char openCh, char closeCh) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == openCh) {
                depth++;
            } else if (c == closeCh) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static int[] lineStarts(String s) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Número da linha (a partir de 1) que contém o índice. */
    static int lineOf(int[] lineStarts, int idx) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= idx) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo + 1;
    }
}
