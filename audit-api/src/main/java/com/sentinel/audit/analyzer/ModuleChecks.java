package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.ArchitecturePolicyAnalyzer.ArchPlace;
import com.sentinel.audit.analyzer.ArchitecturePolicyAnalyzer.ArchRule;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.pack.ArchitecturePolicy.ModuleRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras de monólito modular que precisam olhar o projeto inteiro: dependência circular entre módulos
 * e acesso ao interior de outro módulo (só a API pública é permitida). Lê só os imports Java.
 */
final class ModuleChecks {

    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+?)(?:\\.\\*)?\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    /** Um import de um módulo em outro, com o lugar onde aparece. */
    private record Edge(String from, String to, String file, int line, String snippet, String subpackage) { }

    private final List<ModuleRule> rules;
    private final Map<ModuleRule, List<Edge>> edges = new LinkedHashMap<>();

    ModuleChecks(List<ModuleRule> rules) {
        this.rules = rules;
        rules.forEach(r -> edges.put(r, new ArrayList<>()));
    }

    void read(String rel, String content) {
        if (rules.isEmpty()) {
            return;
        }
        if (rel.matches(".*\\.(ts|tsx|js|jsx|mjs|vue)$")) {
            readTs(rel, content);
            return;
        }
        if (!rel.endsWith(".java")) {
            return;
        }
        Matcher pm = PACKAGE.matcher(content);
        if (!pm.find()) {
            return;
        }
        String[] lines = content.split("\n", -1);
        for (ModuleRule r : rules) {
            if (r.isPath()) {
                continue;
            }
            String own = segmentAfter(pm.group(1), r.root());
            if (own == null) {
                continue;
            }
            for (int i = 0; i < lines.length; i++) {
                Matcher m = IMPORT.matcher(lines[i]);
                if (!m.find()) {
                    continue;
                }
                String target = segmentAfter(m.group(1), r.root());
                if (target != null && !target.equals(own)) {
                    edges.get(r).add(new Edge(own, target, rel, i + 1, lines[i].strip(), subpackage(m.group(1), r.root(), target)));
                }
            }
        }
    }

    private static final Pattern TS_IMPORT = Pattern.compile(
            "(?:\\b(?:import|export)\\s+(?:type\\s+)?(?:[^'\"\\n;]*?\\s+from\\s+)?|\\brequire\\(\\s*|\\bimport\\(\\s*)['\"]([^'\"\\n]+)['\"]");

    /** Imports TypeScript: o módulo é a primeira pasta depois de {@code root}; vale alias ({@code @/features/x}) e caminho relativo. */
    private void readTs(String rel, String content) {
        String[] lines = content.split("\n", -1);
        for (ModuleRule r : rules) {
            if (!r.isPath()) {
                continue;
            }
            String base = r.root().replaceAll("/+$", "");
            String own = firstDirAfter(rel, base);
            if (own == null) {
                continue;
            }
            for (int i = 0; i < lines.length; i++) {
                Matcher m = TS_IMPORT.matcher(lines[i]);
                while (m.find()) {
                    String rest = tsTargetPath(rel, m.group(1), base, r.alias());
                    String target = rest == null ? null : firstSegment(rest);
                    if (target != null && !target.equals(own)) {
                        edges.get(r).add(new Edge(own, target, rel, i + 1, lines[i].strip(), deepPart(rest, target)));
                    }
                }
            }
        }
    }

    private static String firstDirAfter(String rel, String base) {
        if (!rel.startsWith(base + "/")) {
            return null;
        }
        String rest = rel.substring(base.length() + 1);
        int slash = rest.indexOf('/');
        return slash < 0 ? null : rest.substring(0, slash);
    }

    /** Caminho do alvo depois da raiz ({@code login/ui/X}), ou nulo se o import não cai sob a raiz. */
    private static String tsTargetPath(String fromFile, String spec, String base, String alias) {
        if (alias != null && (spec.equals(alias) || spec.startsWith(alias + "/"))) {
            return spec.length() <= alias.length() + 1 ? null : spec.substring(alias.length() + 1);
        }
        if (!spec.startsWith(".")) {
            return null;
        }
        java.nio.file.Path parent = java.nio.file.Path.of(fromFile).getParent();
        String resolved = (parent == null ? java.nio.file.Path.of(spec) : parent.resolve(spec)).normalize().toString().replace('\\', '/');
        return resolved.startsWith(base + "/") ? resolved.substring(base.length() + 1) : null;
    }

    private static String firstSegment(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    /** "interno" quando o import passa do index da fatia (login/ui/X); nulo quando é a própria fatia (login ou login/index). */
    private static String deepPart(String rest, String target) {
        String after = rest.length() > target.length() ? rest.substring(target.length() + 1) : "";
        return after.isEmpty() || after.equals("index") || after.startsWith("index.") ? null : after;
    }

    void report(AuditRun run, List<Finding> out) {
        for (ModuleRule r : rules) {
            List<Edge> es = edges.get(r);
            if (!r.publicApi().isEmpty()) {
                internalAccess(r, es, run, out);
            }
            if (r.noCross()) {
                crossImports(r, es, run, out);
            }
            if (r.noCycles()) {
                cycles(r, es, run, out);
            }
        }
    }

    private static void internalAccess(ModuleRule r, List<Edge> es, AuditRun run, List<Finding> out) {
        for (Edge e : es) {
            if (e.subpackage() == null || (!r.isPath() && r.publicApi().contains(e.subpackage()))) {
                continue;
            }
            out.add(ArchitecturePolicyAnalyzer.archFinding(run, new ArchPlace(e.file(), e.line(), e.snippet()), new ArchRule(
                    "sentinel-arch-module-internal", r.severity(), Category.ARCHITECTURE,
                    "Módulo '" + e.from() + "' usa o interior do módulo '" + e.to() + "'",
                    r.message() != null ? r.message() : r.isPath()
                            ? "Importe '" + e.to() + "' pelo index (API pública), não por '" + e.subpackage() + "'."
                            : "Só a API pública de '" + e.to() + "' (" + String.join(", ", r.publicApi())
                            + ") pode ser usada por outros módulos; '" + e.subpackage() + "' é interno.")));
        }
    }

    private static void crossImports(ModuleRule r, List<Edge> es, AuditRun run, List<Finding> out) {
        for (Edge e : es) {
            out.add(ArchitecturePolicyAnalyzer.archFinding(run, new ArchPlace(e.file(), e.line(), e.snippet()), new ArchRule(
                    "sentinel-arch-module-cross", r.severity(), Category.ARCHITECTURE,
                    "Fatia '" + e.from() + "' importa a fatia irmã '" + e.to() + "'",
                    r.message() != null ? r.message() : "Fatias do mesmo nível não devem se importar. Suba o código compartilhado para uma camada abaixo ou componha as duas em uma camada acima.")));
        }
    }

    /** Um achado por grupo de módulos que dependem uns dos outros em círculo (componente fortemente conexo). */
    private static void cycles(ModuleRule r, List<Edge> es, AuditRun run, List<Finding> out) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (Edge e : es) {
            graph.computeIfAbsent(e.from(), k -> new LinkedHashSet<>()).add(e.to());
            graph.computeIfAbsent(e.to(), k -> new LinkedHashSet<>());
        }
        for (Set<String> group : new Tarjan(graph).components()) {
            if (group.size() < 2) {
                continue;
            }
            Edge first = es.stream().filter(e -> group.contains(e.from()) && group.contains(e.to()))
                    .min((a, b) -> a.file().equals(b.file()) ? Integer.compare(a.line(), b.line()) : a.file().compareTo(b.file()))
                    .orElseThrow();
            String names = String.join(" -> ", new TreeSet<>(group)) + " -> ...";
            out.add(ArchitecturePolicyAnalyzer.archFinding(run, new ArchPlace(first.file(), first.line(), first.snippet()), new ArchRule(
                    "sentinel-arch-module-cycle", r.severity(), Category.ARCHITECTURE,
                    "Dependência circular entre módulos: " + String.join(", ", new TreeSet<>(group)),
                    r.message() != null ? r.message() : "Os módulos dependem uns dos outros em círculo (" + names
                            + "). Quebre o ciclo com um evento, uma interface no módulo dono ou movendo o código comum.")));
        }
    }

    /** Primeiro segmento do nome depois de {@code root}, ou nulo se o nome não está sob {@code root}. */
    static String segmentAfter(String name, String root) {
        if (!name.startsWith(root + ".")) {
            return null;
        }
        String rest = name.substring(root.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /** Subpacote do módulo alvo usado no import; nulo quando é a classe da raiz do módulo (a API de base). */
    private static String subpackage(String fqn, String root, String module) {
        String prefix = root + "." + module + ".";
        String rest = fqn.startsWith(prefix) ? fqn.substring(prefix.length()) : "";
        if (rest.isEmpty()) {
            return null;
        }
        int dot = rest.indexOf('.');
        String first = dot < 0 ? rest : rest.substring(0, dot);
        return Character.isUpperCase(first.charAt(0)) ? null : first;
    }

    /** Componentes fortemente conexos (Tarjan). */
    private static final class Tarjan {
        private final Map<String, Set<String>> graph;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> low = new HashMap<>();
        private final List<String> stack = new ArrayList<>();
        private final Set<String> onStack = new LinkedHashSet<>();
        private final List<Set<String>> result = new ArrayList<>();
        private int counter;

        Tarjan(Map<String, Set<String>> graph) {
            this.graph = graph;
        }

        List<Set<String>> components() {
            for (String v : new TreeSet<>(graph.keySet())) {
                if (!index.containsKey(v)) {
                    visit(v);
                }
            }
            return result;
        }

        private void visit(String v) {
            index.put(v, counter);
            low.put(v, counter++);
            stack.add(v);
            onStack.add(v);
            for (String w : graph.get(v)) {
                if (!index.containsKey(w)) {
                    visit(w);
                    low.put(v, Math.min(low.get(v), low.get(w)));
                } else if (onStack.contains(w)) {
                    low.put(v, Math.min(low.get(v), index.get(w)));
                }
            }
            if (low.get(v).equals(index.get(v))) {
                Set<String> comp = new LinkedHashSet<>();
                String w;
                do {
                    w = stack.remove(stack.size() - 1);
                    onStack.remove(w);
                    comp.add(w);
                } while (!w.equals(v));
                result.add(comp);
            }
        }
    }
}
