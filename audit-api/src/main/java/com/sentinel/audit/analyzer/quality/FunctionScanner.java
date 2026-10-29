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

    /** Quantos caracteres antes do nome são olhados para reconhecer {@code new X(...)}, record, class etc. */
    private static final int LOOKBEHIND_CHARS = 40;


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

    /** Contexto de uma varredura: texto limpo, linhas, sintaxe e onde acumular as funções. */
    private record Ctx(String clean, int[] lineStarts, boolean ts, List<Fn> out, java.util.Set<Integer> seen) { }

    /** Parâmetros e abertura do corpo de uma arrow function. */
    private record Arrow(String params, int bodyOpen) { }

    /** Encontra as funções de um arquivo. {@code ts} escolhe a sintaxe (false = Java). */
    public static List<Fn> scan(String clean, boolean ts) {
        List<Fn> result = new ArrayList<>();
        Ctx c = new Ctx(clean, lineStarts(clean), ts, result, new java.util.HashSet<>());
        scanNamed(c);
        if (ts) {
            scanArrows(c);
        }
        result.sort(java.util.Comparator.comparingInt(Fn::startIdx));
        return result;
    }

    private static void scanNamed(Ctx c) {
        String clean = c.clean();
        Matcher m = IDENT_PAREN.matcher(clean);
        while (m.find()) {
            String name = m.group(1);
            if (NOT_FUNCTIONS.contains(name) || followsTypeKeyword(clean, m.start(1))) {
                continue;                                            // classe anônima, record, declaração de tipo, "extends Base(...)"
            }
            int open = m.end() - 1;
            int close = matchClosing(clean, open, '(', ')');
            if (close < 0) {
                continue;
            }
            Matcher tail = (c.ts() ? TS_TAIL : JAVA_TAIL).matcher(clean);
            tail.region(close + 1, clean.length());
            if (!tail.lookingAt()) {
                continue;
            }
            add(c, name, m.start(1), tail.end() - 1, clean.substring(open + 1, close));
        }
    }

    private static boolean followsTypeKeyword(String clean, int nameStart) {
        String before = clean.substring(Math.max(0, nameStart - LOOKBEHIND_CHARS), nameStart);
        Matcher prev = PREV_TOKEN.matcher(before);
        if (!prev.find()) {
            return false;
        }
        String token = prev.group(1);
        return "new".equals(token) || "record".equals(token) || "class".equals(token) || "interface".equals(token)
                || "extends".equals(token) || "implements".equals(token);
    }

    private static void scanArrows(Ctx c) {
        Matcher a = ARROW_DECL.matcher(c.clean());
        while (a.find()) {
            Arrow arrow = arrowOf(c.clean(), a);
            if (arrow != null) {
                add(c, a.group(1), a.start(1), arrow.bodyOpen(), arrow.params());
            }
        }
    }

    /** Parâmetros e chave de abertura da arrow function em {@code a}; nulo se não for uma com corpo em bloco. */
    private static Arrow arrowOf(String clean, Matcher a) {
        if ("(".equals(a.group(2))) {
            int open = a.end() - 1;
            int close = matchClosing(clean, open, '(', ')');
            if (close < 0) {
                return null;
            }
            Matcher tail = ARROW_TAIL.matcher(clean);
            tail.region(close + 1, clean.length());
            return tail.lookingAt() ? new Arrow(clean.substring(open + 1, close), tail.end() - 1) : null;
        }
        Matcher tail = Pattern.compile("\\G\\s*\\{").matcher(clean);        // x => { ... }
        tail.region(a.end(), clean.length());
        return tail.lookingAt() ? new Arrow("x", tail.end() - 1) : null;
    }

    private static void add(Ctx c, String name, int startIdx, int openBrace, String params) {
        if (!c.seen().add(openBrace)) {
            return;
        }
        int closeBrace = matchClosing(c.clean(), openBrace, '{', '}');
        if (closeBrace < 0) {
            return;
        }
        String body = c.clean().substring(openBrace + 1, closeBrace);
        int startLine = lineOf(c.lineStarts(), startIdx);
        int endLine = lineOf(c.lineStarts(), closeBrace);
        c.out().add(new Fn(name, startIdx, openBrace, closeBrace, startLine, endLine,
                countParams(params), codeLines(c.clean(), c.lineStarts(), startLine, endLine), complexity(body), nesting(body)));
    }

    // ------------------------------------------------------------------ métricas

    /** Complexidade ciclomática aproximada: 1 + pontos de decisão. */
    static int complexity(String body) {
        return 1 + count(DECISION, body) + count(NULLISH, body) + count(TERNARY, body);
    }

    /**
     * Níveis de blocos aninhados dentro do corpo (o corpo em si não conta). Chaves de objeto literal, de tipo
     * anônimo ({@code x: { a: string }}) e de desestruturação não são blocos de lógica e não contam: medido em
     * repositórios reais, um esquema Swagger de 11 níveis aparecia como "aninhamento 11".
     */
    static int nesting(String body) {
        java.util.ArrayDeque<Boolean> pilha = new java.util.ArrayDeque<>();   // true = bloco de lógica
        int depth = 0;
        int max = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                boolean bloco = !abreLiteral(body, i);
                pilha.push(bloco);
                if (bloco) {
                    depth++;
                    max = Math.max(max, depth);
                }
            } else if (c == '}') {
                if (!pilha.isEmpty() && pilha.pop()) {
                    depth = Math.max(0, depth - 1);
                }
            }
        }
        return max;
    }

    private static final String LITERAL_PREV = "(,=:[?&|!+*%";

    /** A chave em {@code idx} abre um objeto literal/tipo (e não um bloco)? Olha o caractere significativo anterior. */
    private static boolean abreLiteral(String s, int idx) {
        int j = idx - 1;
        while (j >= 0 && Character.isWhitespace(s.charAt(j))) {
            j--;
        }
        if (j < 0) {
            return false;
        }
        char p = s.charAt(j);
        if (LITERAL_PREV.indexOf(p) >= 0) {
            return true;
        }
        if (p == '-') {
            return !(j > 0 && s.charAt(j - 1) == '-');
        }
        return Character.isLetter(p) && isValueKeyword(s, j);
    }

    /** A palavra que termina em {@code j} é uma das que introduzem um valor ({@code return {...}})? */
    private static boolean isValueKeyword(String s, int j) {
        int k = j;
        while (k >= 0 && (Character.isLetterOrDigit(s.charAt(k)) || s.charAt(k) == '_' || s.charAt(k) == '$')) {
            k--;
        }
        String palavra = s.substring(k + 1, j + 1);
        return "return".equals(palavra) || "yield".equals(palavra) || "await".equals(palavra) || "typeof".equals(palavra);
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
            } else if (c == ',' && depth == 0) {
                count++;
            } else {
                depth = Math.max(0, depth + depthDelta(c));
            }
        }
        // vírgula final ("a, b,") não cria um parâmetro a mais
        return params.stripTrailing().endsWith(",") ? count - 1 : count;
    }

    private static int depthDelta(char c) {
        if (c == '<' || c == '(' || c == '[' || c == '{') {
            return 1;
        }
        return c == '>' || c == ')' || c == ']' || c == '}' ? -1 : 0;
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
