package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.structure.SourceFiles.Src;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        // hash da janela -> posições (arquivo << 32 | índice da linha significativa)
        Map<Long, List<Long>> windows = new HashMap<>();
        int total = 0;
        for (int fi = 0; fi < norms.size() && total < MAX_WINDOWS; fi++) {
            Norm n = norms.get(fi);
            for (int i = 0; i + window <= n.lines.length && total < MAX_WINDOWS; i++) {
                if (!varied(n.lines, i, window)) {
                    continue;
                }
                long h = 1125899906842597L;
                for (int k = 0; k < window; k++) {
                    h = h * 31 + n.hash[i + k];
                }
                windows.computeIfAbsent(h, x -> new ArrayList<>(2)).add(((long) fi << 32) | i);
                total++;
            }
        }
        // (arquivo A, arquivo B, deslocamento) -> índices de A em que a janela coincide
        Map<String, TreeSet<Integer>> matches = new HashMap<>();
        for (List<Long> pos : windows.values()) {
            if (pos.size() < 2) {
                continue;
            }
            long first = pos.get(0);
            for (int p = 1; p < pos.size(); p++) {
                long other = pos.get(p);
                int fa = (int) (first >>> 32);
                int ia = (int) first;
                int fb = (int) (other >>> 32);
                int ib = (int) other;
                if (!sameWindow(norms.get(fa), ia, norms.get(fb), ib, window)) {
                    continue;                                   // colisão de hash
                }
                matches.computeIfAbsent(fa + "|" + fb + "|" + (ib - ia), x -> new TreeSet<>()).add(ia);
            }
        }
        List<Clone> clones = new ArrayList<>();
        for (Map.Entry<String, TreeSet<Integer>> e : matches.entrySet()) {
            String[] key = e.getKey().split("\\|");
            Norm a = norms.get(Integer.parseInt(key[0]));
            Norm b = norms.get(Integer.parseInt(key[1]));
            int delta = Integer.parseInt(key[2]);
            int runStart = -1;
            int prev = -1;
            for (int idx : e.getValue()) {
                if (runStart < 0) {
                    runStart = idx;
                } else if (idx != prev + 1) {
                    addRun(clones, a, b, delta, runStart, prev, window, minLines);
                    runStart = idx;
                }
                prev = idx;
            }
            if (runStart >= 0) {
                addRun(clones, a, b, delta, runStart, prev, window, minLines);
            }
        }
        clones.sort(Comparator.comparingInt(Clone::lines).reversed().thenComparing(Clone::fileB).thenComparingInt(Clone::lineB));
        return clones.size() > maxClones ? new ArrayList<>(clones.subList(0, maxClones)) : clones;
    }

    private static void addRun(List<Clone> out, Norm a, Norm b, int delta, int start, int end, int window, int minLines) {
        int lines = end - start + window;
        if (lines < minLines) {
            return;
        }
        if (a == b && delta < lines) {
            return;                                             // trechos que se sobrepõem no mesmo arquivo (código periódico)
        }
        out.add(new Clone(a.file, a.origLine[start], b.file, b.origLine[start + delta], lines, a.lines[start]));
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
        for (int k = 0; k < window && distinct.size() < 3; k++) {
            distinct.add(lines[from + k]);
        }
        return distinct.size() >= 3;
    }

    static Norm normalize(Src f) {
        String[] raw = f.text().split("\n", -1);
        List<String> kept = new ArrayList<>();
        List<Integer> orig = new ArrayList<>();
        boolean inBlockComment = false;
        for (int i = 0; i < raw.length; i++) {
            String line = raw[i].strip().replaceAll("\\s+", " ");
            if (inBlockComment) {
                if (line.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (line.startsWith("/*")) {
                inBlockComment = !line.contains("*/");
                continue;
            }
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*") || line.length() <= 3
                    || line.startsWith("import ") || line.startsWith("package ") || line.startsWith("@Override")) {
                continue;
            }
            if (ACCESSOR.matcher(line).matches()) {
                continue;                                       // getters, setters e atribuições simples: boilerplate, não lógica
            }
            kept.add(line);
            orig.add(i + 1);
        }
        String[] lines = kept.toArray(new String[0]);
        long[] hash = new long[lines.length];
        for (int i = 0; i < lines.length; i++) {
            long h = 1469598103934665603L;
            for (int c = 0; c < lines[i].length(); c++) {
                h = (h ^ lines[i].charAt(c)) * 1099511628211L;
            }
            hash[i] = h;
        }
        return new Norm(f.rel(), lines, orig.stream().mapToInt(Integer::intValue).toArray(), hash);
    }
}
