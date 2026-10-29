package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.structure.SourceFiles.Src;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Blocos de código duplicados entre arquivos (ou dentro de um arquivo) de produção.
 * <p>
 * Compara linhas "significativas" normalizadas: ignora comentários (cabeçalho de licença inclusive), imports,
 * pacote, linhas só de chaves e espaços em branco. Só vale um trecho com várias linhas diferentes entre si e com
 * pelo menos {@code minLines} linhas significativas seguidas iguais. Não olha XML, HTML nem Markdown.
 */
public final class Duplication {

    /** Semente e multiplicador do hash polinomial das janelas (valores clássicos de Java: semente de djb-like e 31). */
    private static final long WINDOW_HASH_SEED = 1125899906842597L;
    private static final long WINDOW_HASH_MULTIPLIER = 31;
    /** Constantes do FNV-1a de 64 bits, usado no hash de cada linha. */
    private static final long FNV_OFFSET_BASIS = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;
    /** Posição (arquivo, linha) é empacotada num long: arquivo nos 32 bits altos, linha nos baixos. */
    private static final int FILE_INDEX_SHIFT = 32;
    /** Mínimo de linhas diferentes numa janela para ela contar como duplicação real. */
    private static final int MIN_DISTINCT_LINES = 3;
    /** Linhas com até esse tamanho ({@code "});"}, {@code "}"}) não entram na comparação. */
    private static final int TRIVIAL_LINE_MAX_CHARS = 3;


    private Duplication() {}

    /** Um bloco repetido: o original, a cópia e quantas linhas significativas se repetem. */
    public record Clone(String fileA, int lineA, String fileB, int lineB, int lines, String firstLine) {}

    private static final int MAX_WINDOWS = 1_500_000;
    private static final java.util.regex.Pattern ACCESSOR = java.util.regex.Pattern.compile(
            "^(?:(?:public|protected|private) )?[\\w<>\\[\\], ?]+ (?:get|set|is)[A-Z]\\w*\\([^)]*\\) \\{$"
                    + "|^return (?:this\\.)?\\w+;$|^this\\.\\w+ = \\w+;$");

    private record Norm(String file, String[] lines, int[] origLine, long[] hash) {}

    public static List<Clone> find(List<Src> files, int window, int minLines, int maxClones) {
        List<Norm> norms = new ArrayList<>();
        for (Src f : files) {
            Norm n = normalize(f);
            if (n.lines.length >= window) {
                norms.add(n);
            }
        }
        Map<String, NavigableSet<Integer>> matches = matchingWindows(norms, indexWindows(norms, window), window);
        List<Clone> clones = new ArrayList<>();
        for (Map.Entry<String, NavigableSet<Integer>> e : matches.entrySet()) {
            collectRuns(clones, norms, e, new Cfg(window, minLines));
        }
        clones.sort(Comparator.comparingInt(Clone::lines).reversed().thenComparing(Clone::fileB).thenComparingInt(Clone::lineB));
        return clones.size() > maxClones ? new ArrayList<>(clones.subList(0, maxClones)) : clones;
    }

    /** Tamanho da janela e mínimo de linhas de um clone. */
    private record Cfg(int window, int minLines) { }

    /** Os dois arquivos comparados e o deslocamento entre eles. */
    private record Pair(Norm a, Norm b, int delta) { }

    /** hash da janela -> posições (arquivo << 32 | índice da linha significativa). */
    private static Map<Long, List<Long>> indexWindows(List<Norm> norms, int window) {
        Map<Long, List<Long>> windows = new HashMap<>();
        int total = 0;
        for (int fi = 0; fi < norms.size() && total < MAX_WINDOWS; fi++) {
            Norm n = norms.get(fi);
            for (int i = 0; i + window <= n.lines.length && total < MAX_WINDOWS; i++) {
                if (!varied(n.lines, i, window)) {
                    continue;
                }
                long h = WINDOW_HASH_SEED;
                for (int k = 0; k < window; k++) {
                    h = h * WINDOW_HASH_MULTIPLIER + n.hash[i + k];
                }
                windows.computeIfAbsent(h, x -> new ArrayList<>(2)).add(((long) fi << FILE_INDEX_SHIFT) | i);
                total++;
            }
        }
        return windows;
    }

    /** (arquivo A, arquivo B, deslocamento) -> índices de A em que a janela coincide. */
    private static Map<String, NavigableSet<Integer>> matchingWindows(List<Norm> norms, Map<Long, List<Long>> windows, int window) {
        Map<String, NavigableSet<Integer>> matches = new HashMap<>();
        for (List<Long> pos : windows.values()) {
            if (pos.size() < 2) {
                continue;
            }
            long first = pos.get(0);
            for (int p = 1; p < pos.size(); p++) {
                long other = pos.get(p);
                int fa = (int) (first >>> FILE_INDEX_SHIFT);
                int ia = (int) first;
                int fb = (int) (other >>> FILE_INDEX_SHIFT);
                int ib = (int) other;
                if (!sameWindow(norms.get(fa), ia, norms.get(fb), ib, window)) {
                    continue;                                   // colisão de hash
                }
                matches.computeIfAbsent(fa + "|" + fb + "|" + (ib - ia), x -> new TreeSet<Integer>()).add(ia);
            }
        }
        return matches;
    }

    private static void collectRuns(List<Clone> clones, List<Norm> norms, Map.Entry<String, NavigableSet<Integer>> e, Cfg cfg) {
        String[] key = e.getKey().split("\\|");
        Pair pair = new Pair(norms.get(Integer.parseInt(key[0])), norms.get(Integer.parseInt(key[1])), Integer.parseInt(key[2]));
        int runStart = -1;
        int prev = -1;
        for (int idx : e.getValue()) {
            if (runStart < 0) {
                runStart = idx;
            } else if (idx != prev + 1) {
                addRun(clones, pair, runStart, prev, cfg);
                runStart = idx;
            }
            prev = idx;
        }
        if (runStart >= 0) {
            addRun(clones, pair, runStart, prev, cfg);
        }
    }

    private static void addRun(List<Clone> out, Pair pair, int start, int end, Cfg cfg) {
        Norm a = pair.a();
        Norm b = pair.b();
        int lines = end - start + cfg.window();
        if (lines < cfg.minLines()) {
            return;
        }
        if (a.equals(b) && pair.delta() < lines) {
            return;                                             // trechos que se sobrepõem no mesmo arquivo (código periódico)
        }
        out.add(new Clone(a.file, a.origLine[start], b.file, b.origLine[start + pair.delta()], lines, a.lines[start]));
    }

    private static boolean sameWindow(Norm a, int ia, Norm b, int ib, int window) {
        for (int k = 0; k < window; k++) {
            if (!a.lines[ia + k].equals(b.lines[ib + k])) {
                return false;
            }
        }
        return true;
    }

    /** Pelo menos 3 linhas diferentes: evita "a.b(); a.b(); a.b();" e tabelas repetitivas. */
    private static boolean varied(String[] lines, int from, int window) {
        Set<String> distinct = new HashSet<>();
        for (int k = 0; k < window && distinct.size() < MIN_DISTINCT_LINES; k++) {
            distinct.add(lines[from + k]);
        }
        return distinct.size() >= MIN_DISTINCT_LINES;
    }

    static Norm normalize(Src f) {
        String[] raw = f.text().split("\n", -1);
        List<String> kept = new ArrayList<>();
        List<Integer> orig = new ArrayList<>();
        boolean inBlockComment = false;
        for (int i = 0; i < raw.length; i++) {
            String line = raw[i].strip().replaceAll("\\s+", " ");
            if (inBlockComment) {
                inBlockComment = !line.contains("*/");
            } else if (line.startsWith("/*")) {
                inBlockComment = !line.contains("*/");
            } else if (!isNoise(line)) {
                kept.add(line);
                orig.add(i + 1);
            }
        }
        String[] lines = kept.toArray(new String[0]);
        long[] hash = new long[lines.length];
        for (int i = 0; i < lines.length; i++) {
            hash[i] = fnv(lines[i]);
        }
        return new Norm(f.rel(), lines, orig.stream().mapToInt(Integer::intValue).toArray(), hash);
    }

    /** Linha que não conta como código: vazia, comentário, import, trivial ou boilerplate (getter/setter/atribuição). */
    private static boolean isNoise(String line) {
        return line.isEmpty() || line.startsWith("//") || line.startsWith("*") || line.length() <= TRIVIAL_LINE_MAX_CHARS
                || line.startsWith("import ") || line.startsWith("package ") || line.startsWith("@Override")
                || ACCESSOR.matcher(line).matches();
    }

    private static long fnv(String line) {
        long h = FNV_OFFSET_BASIS;
        for (int c = 0; c < line.length(); c++) {
            h = (h ^ line.charAt(c)) * FNV_PRIME;
        }
        return h;
    }
}
