package com.sentinel.audit.pack;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Severity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Lê o {@code architecture.yml} de um pacote (camadas, convenções, arquivos obrigatórios, dependências proibidas). */
final class ArchitectureYaml {

    private ArchitectureYaml() {
    }

    static ArchitecturePolicy parse(Path dir, List<String> errors) {
        Path file = dir.resolve("architecture.yml");
        if (!Files.isRegularFile(file)) {
            return ArchitecturePolicy.EMPTY;
        }
        Object root = PackLoader.read(file, dir, errors);
        Map<String, Object> m = Yamls.map(root);
        if (m == null) {
            if (root != null) {
                errors.add("architecture.yml: esperava um mapa de chaves");
            }
            return ArchitecturePolicy.EMPTY;
        }
        String f = "architecture.yml";
        PackLoader.checkKeys(m, Set.of("layers", "forbid", "allow", "strict", "conventions", "required-files", "components"), f, errors);

        List<ArchitecturePolicy.Layer> layers = parseLayers(m.get("layers"), f, errors);
        Set<String> layerNames = new HashSet<>();
        layers.forEach(l -> layerNames.add(l.name()));
        List<ArchitecturePolicy.LayerRule> forbid = layerRules(m.get("forbid"), "forbid", layerNames, f, errors);
        List<ArchitecturePolicy.LayerRule> allow = layerRules(m.get("allow"), "allow", layerNames, f, errors);
        List<ArchitecturePolicy.Convention> conventions = parseConventions(m.get("conventions"), f, errors);
        List<ArchitecturePolicy.RequiredFile> required = parseRequiredFiles(m.get("required-files"), f, errors);
        ArchitecturePolicy.Components components = parseComponents(Yamls.map(m.get("components")), f, errors);
        return new ArchitecturePolicy(layers, forbid, allow, Yamls.bool(m, "strict", false), conventions, required, components);
    }

    private static List<ArchitecturePolicy.Layer> parseLayers(Object raw, String f, List<String> errors) {
        List<ArchitecturePolicy.Layer> layers = new ArrayList<>();
        Set<String> layerNames = new HashSet<>();
        for (Map<String, Object> l : PackLoader.items(raw, f + " layers", errors)) {
            PackLoader.checkKeys(l, Set.of("name", "paths", "packages"), f + " layers", errors);
            String name = Yamls.str(l, "name");
            List<String> paths = Yamls.strings(l, "paths", errors, f + " layers");
            List<String> pkgs = Yamls.strings(l, "packages", errors, f + " layers");
            if (name == null) {
                errors.add(f + " layers: 'name' é obrigatório");
                continue;
            }
            if ((paths == null || paths.isEmpty()) && (pkgs == null || pkgs.isEmpty())) {
                errors.add(f + " layer '" + name + "': informe 'paths' e/ou 'packages'");
            }
            if (!layerNames.add(name)) {
                errors.add(f + " layer '" + name + "' repetida");
            }
            layers.add(new ArchitecturePolicy.Layer(name, paths == null ? List.of() : paths, pkgs == null ? List.of() : pkgs));
        }
        return layers;
    }

    private static List<ArchitecturePolicy.Convention> parseConventions(Object raw, String f, List<String> errors) {
        List<ArchitecturePolicy.Convention> conventions = new ArrayList<>();
        Set<String> convIds = new HashSet<>();
        for (Map<String, Object> c : PackLoader.items(raw, f + " conventions", errors)) {
            PackLoader.checkKeys(c, Set.of("id", "title", "files", "exclude", "require", "forbid", "message", "severity", "category"), f + " conventions", errors);
            String id = Yamls.str(c, "id");
            List<String> files = Yamls.strings(c, "files", errors, f + " conventions");
            if (id == null || !PackManifest.ID.matcher(id).matches()) {
                errors.add(f + " conventions: 'id' ausente ou inválido");
                continue;
            }
            String where = f + " convention '" + id + "'";
            if (!convIds.add(id)) {
                errors.add(where + " repetida");
            }
            conventions.add(buildConvention(c, id, files, where, errors));
        }
        return conventions;
    }

    /** Monta a convenção {@code id}, registrando o que faltar (arquivos, require/forbid, regex inválida). */
    private static ArchitecturePolicy.Convention buildConvention(Map<String, Object> c, String id, List<String> files,
                                                                 String where, List<String> errors) {
        if (files == null || files.isEmpty()) {
            errors.add(where + ": 'files' (glob) é obrigatório");
        }
        String require = Yamls.str(c, "require");
        String forb = Yamls.str(c, "forbid");
        if (require == null && forb == null) {
            errors.add(where + ": informe 'require' e/ou 'forbid' (regex)");
        }
        checkRegex(require, where + ".require", errors);
        checkRegex(forb, where + ".forbid", errors);
        List<String> exclude = Yamls.strings(c, "exclude", errors, where);
        String title = Yamls.str(c, "title");
        return new ArchitecturePolicy.Convention(id, title == null ? id : title,
                files == null ? List.of() : files, exclude == null ? List.of() : exclude, require, forb,
                Yamls.str(c, "message"), PackLoader.severity(c, "severity", Severity.MEDIUM, where, errors),
                PackLoader.category(c, "category", Category.ARCHITECTURE, where, errors));
    }

    private static List<ArchitecturePolicy.RequiredFile> parseRequiredFiles(Object raw, String f, List<String> errors) {
        List<ArchitecturePolicy.RequiredFile> required = new ArrayList<>();
        for (Map<String, Object> r : PackLoader.items(raw, f + " required-files", errors)) {
            PackLoader.checkKeys(r, Set.of("path", "message", "severity"), f + " required-files", errors);
            String path = Yamls.str(r, "path");
            if (path == null) {
                errors.add(f + " required-files: 'path' é obrigatório");
                continue;
            }
            required.add(new ArchitecturePolicy.RequiredFile(path, Yamls.str(r, "message"),
                    PackLoader.severity(r, "severity", Severity.LOW, f + " required-file '" + path + "'", errors)));
        }
        return required;
    }

    private static List<ArchitecturePolicy.LayerRule> layerRules(Object raw, String kind, Set<String> layerNames,
                                                               String f, List<String> errors) {
        List<ArchitecturePolicy.LayerRule> out = new ArrayList<>();
        for (Map<String, Object> r : PackLoader.items(raw, f + " " + kind, errors)) {
            PackLoader.checkKeys(r, Set.of("from", "to", "message", "severity"), f + " " + kind, errors);
            String from = Yamls.str(r, "from");
            String to = Yamls.str(r, "to");
            if (from == null || to == null) {
                errors.add(f + " " + kind + ": 'from' e 'to' são obrigatórios");
                continue;
            }
            for (String n : List.of(from, to)) {
                if (!layerNames.contains(n)) {
                    errors.add(f + " " + kind + ": camada '" + n + "' não foi definida em 'layers'");
                }
            }
            out.add(new ArchitecturePolicy.LayerRule(from, to, Yamls.str(r, "message"),
                    PackLoader.severity(r, "severity", Severity.MEDIUM, f + " " + kind + " " + from + "->" + to, errors)));
        }
        return out;
    }

    private static ArchitecturePolicy.Components parseComponents(Map<String, Object> m, String f, List<String> errors) {
        if (m == null) {
            return ArchitecturePolicy.EMPTY.components();
        }
        PackLoader.checkKeys(m, Set.of("forbidden", "min-version", "required", "approved-only"), f + " components", errors);
        return new ArchitecturePolicy.Components(
                depRules(m.get("forbidden"), new DepKind("forbidden", false, Severity.HIGH), f, errors),
                depRules(m.get("min-version"), new DepKind("min-version", true, Severity.HIGH), f, errors),
                depRules(m.get("required"), new DepKind("required", false, Severity.MEDIUM), f, errors),
                depRules(m.get("approved-only"), new DepKind("approved-only", false, Severity.LOW), f, errors));
    }

    /** O tipo de regra de componente: nome no YAML, se exige versão e a severidade quando não informada. */
    private record DepKind(String kind, boolean needsVersion, Severity fallback) { }

    private static List<ArchitecturePolicy.DepRule> depRules(Object raw, DepKind depKind, String f, List<String> errors) {
        String kind = depKind.kind();
        boolean needsVersion = depKind.needsVersion();
        Severity fallback = depKind.fallback();
        List<ArchitecturePolicy.DepRule> out = new ArrayList<>();
        for (Map<String, Object> d : PackLoader.items(raw, f + " components." + kind, errors)) {
            PackLoader.checkKeys(d, Set.of("dep", "version", "reason", "severity"), f + " components." + kind, errors);
            String dep = Yamls.str(d, "dep");
            String version = Yamls.str(d, "version");
            if (dep == null) {
                errors.add(f + " components." + kind + ": 'dep' é obrigatório");
                continue;
            }
            if (needsVersion && (version == null || Versions.satisfies("1.0.0", version) == null)) {
                errors.add(f + " components." + kind + " '" + dep + "': 'version' ausente ou inválida (ex.: >=2.17.1)");
            }
            out.add(new ArchitecturePolicy.DepRule(dep, version, Yamls.str(d, "reason"),
                    PackLoader.severity(d, "severity", fallback, f + " components." + kind + " '" + dep + "'", errors)));
        }
        return out;
    }

    private static void checkRegex(String regex, String where, List<String> errors) {
        if (regex == null) {
            return;
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            errors.add(where + ": expressão regular inválida (" + e.getDescription() + ")");
        }
    }
}
