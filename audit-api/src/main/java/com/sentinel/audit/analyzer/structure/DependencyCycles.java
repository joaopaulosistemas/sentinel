package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.structure.SourceFiles.Src;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ciclos de dependência: pacotes Java que se importam em círculo e arquivos TypeScript/JavaScript que se importam em
 * círculo. Só lê o texto dos imports; não compila nem executa nada do projeto. Cada ciclo é um grupo de nós em que
 * todos alcançam todos (componente fortemente conexa).
 */
public final class DependencyCycles {

    private DependencyCycles() {}

    private static final Pattern JAVA_PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern JAVA_IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+?)(?:\\.\\*)?\\s*;");
    /** import/export de TypeScript; "import type" e "export type" não existem em tempo de execução e ficam de fora. */
    private static final Pattern TS_IMPORT = Pattern.compile(
            "(?m)^\\s*(?:import|export)\\s+(type\\s+)?(?:[^'\";]*?\\s+from\\s+)?['\"]([^'\"\\n]+)['\"]");
    private static final String[] TS_EXT = {".ts", ".tsx", ".js", ".jsx", ".mjs"};

    /** Ciclos entre pacotes Java (membros em ordem alfabética, o maior ciclo primeiro). */
    public static List<List<String>> javaPackages(List<Src> files) {
        Map<String, String> pkgOfFile = new HashMap<>();
        Set<String> known = new HashSet<>();
        for (Src f : files) {
            Matcher m = JAVA_PACKAGE.matcher(f.text());
            if (m.find()) {
                pkgOfFile.put(f.rel(), m.group(1));
                known.add(m.group(1));
            }
        }
        Map<String, Set<String>> graph = new TreeMap<>();
        for (Src f : files) {
            String own = pkgOfFile.get(f.rel());
            if (own == null) {
                continue;
            }
            graph.computeIfAbsent(own, k -> new TreeSet<>());
            Matcher m = JAVA_IMPORT.matcher(f.text());
            while (m.find()) {
                String target = owningPackage(m.group(1), known);
                if (target != null && !target.equals(own)) {
                    graph.get(own).add(target);
                }
            }
        }
        return components(graph);
    }

    /** Um arquivo de cada pacote (o primeiro em ordem alfabética), para apontar o ciclo num caminho que existe. */
    public static Map<String, String> packageFiles(List<Src> files) {
        Map<String, String> out = new TreeMap<>();
        for (Src f : files) {
            Matcher m = JAVA_PACKAGE.matcher(f.text());
            if (m.find()) {
                out.merge(m.group(1), f.rel(), (a, b) -> a.compareTo(b) <= 0 ? a : b);
            }
        }
        return out;
    }

    /** Ciclos entre arquivos TypeScript/JavaScript, seguindo só imports relativos que apontam para arquivos do projeto. */
    public static List<List<String>> tsFiles(List<Src> files) {
        Set<String> known = new HashSet<>();
        for (Src f : files) {
            known.add(f.rel());
        }
        Map<String, Set<String>> graph = new TreeMap<>();
        for (Src f : files) {
            graph.computeIfAbsent(f.rel(), k -> new TreeSet<>());
            Matcher m = TS_IMPORT.matcher(f.text());
            while (m.find()) {
                String spec = m.group(2);
                if (m.group(1) != null || !spec.startsWith(".")) {
                    continue;
                }
                String target = resolve(f.rel(), spec, known);
                if (target != null && !target.equals(f.rel())) {
                    graph.get(f.rel()).add(target);
                }
            }
        }
        return components(graph);
    }

    /** O pacote do projeto a que pertence a classe importada (o mais longo que seja prefixo), ou nulo se for de fora. */
    static String owningPackage(String fqn, Set<String> known) {
        String cur = fqn;
        while (true) {
            int dot = cur.lastIndexOf('.');
            if (dot < 0) {
                return null;
            }
            cur = cur.substring(0, dot);
            if (known.contains(cur)) {
                return cur;
            }
        }
    }

    static String resolve(String fromRel, String spec, Set<String> known) {
        int slash = fromRel.lastIndexOf('/');
        String dir = slash < 0 ? "" : fromRel.substring(0, slash);
        String base = normalize(dir.isEmpty() ? spec : dir + "/" + spec);
        if (base == null) {
            return null;
        }
        if (known.contains(base)) {
            return base;
        }
        for (String ext : TS_EXT) {
            if (known.contains(base + ext)) {
                return base + ext;
            }
        }
        for (String ext : TS_EXT) {
            if (known.contains(base + "/index" + ext)) {
                return base + "/index" + ext;
            }
        }
        return null;
    }

    /** Resolve "." e ".." num caminho com "/"; nulo se sair da raiz. */
    static String normalize(String path) {
        Deque<String> parts = new ArrayDeque<>();
        for (String p : path.split("/")) {
            if (p.isEmpty() || p.equals(".")) {
                continue;
            }
            if (p.equals("..")) {
                if (parts.isEmpty()) {
                    return null;
                }
                parts.removeLast();
            } else {
                parts.addLast(p);
            }
        }
        return String.join("/", parts);
    }

    /** Componentes fortemente conexas com mais de um nó (algoritmo de Tarjan, sem recursão). */
    static List<List<String>> components(Map<String, Set<String>> graph) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        List<List<String>> out = new ArrayList<>();
        int counter = 0;
        for (String start : new TreeSet<>(graph.keySet())) {
            if (index.containsKey(start)) {
                continue;
            }
            Deque<Frame> work = new ArrayDeque<>();
            index.put(start, counter);
            low.put(start, counter++);
            stack.push(start);
            onStack.add(start);
            work.push(new Frame(start, graph.getOrDefault(start, Set.of()).iterator()));
            while (!work.isEmpty()) {
                Frame top = work.peek();
                if (top.next.hasNext()) {
                    String w = top.next.next();
                    if (!index.containsKey(w)) {
                        index.put(w, counter);
                        low.put(w, counter++);
                        stack.push(w);
                        onStack.add(w);
                        work.push(new Frame(w, graph.getOrDefault(w, Set.of()).iterator()));
                    } else if (onStack.contains(w)) {
                        low.put(top.node, Math.min(low.get(top.node), index.get(w)));
                    }
                } else {
                    work.pop();
                    if (!work.isEmpty()) {
                        String parent = work.peek().node;
                        low.put(parent, Math.min(low.get(parent), low.get(top.node)));
                    }
                    if (low.get(top.node).equals(index.get(top.node))) {
                        List<String> comp = new ArrayList<>();
                        String w;
                        do {
                            w = stack.pop();
                            onStack.remove(w);
                            comp.add(w);
                        } while (!w.equals(top.node));
                        if (comp.size() > 1) {
                            Collections.sort(comp);
                            out.add(comp);
                        }
                    }
                }
            }
        }
        out.sort(Comparator.<List<String>>comparingInt(c -> -c.size()).thenComparing(c -> c.get(0)));
        return out;
    }

    private record Frame(String node, Iterator<String> next) {}
}
