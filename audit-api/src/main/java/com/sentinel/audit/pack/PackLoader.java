package com.sentinel.audit.pack;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Lê e <b>valida</b> um pacote de regras. A validação é rigorosa de propósito: pacote é "plug and play" só se
 * erro de digitação, regra sem teste ou referência quebrada aparecem na hora, com o arquivo e o campo, e não
 * como uma regra que silenciosamente não faz nada.
 *
 * <pre>
 * meu-pacote/
 *   pack.yml            (obrigatório) id, name, version...
 *   rules/**.yml        regras Semgrep
 *   tests/**            casos "ruleid:/ok:" do Semgrep
 *   remediation/*.yml   orientação de correção por regra
 *   dedup.yml           equivalências entre ferramentas
 *   tools/*.yml         fontes de SARIF
 *   architecture.yml    camadas, convenções e componentes
 * </pre>
 */
public final class PackLoader {

    static final long MAX_FILE_BYTES = 1024 * 1024;
    static final int MAX_FILES = 5000;
    private static final Pattern SEMGREP_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");
    private static final Set<String> SEMGREP_SEVERITY = Set.of("ERROR", "WARNING", "INFO");
    private static final Set<String> RULE_STATUS = Set.of("draft", "experimental", "stable", "deprecated");
    private static final Set<String> LEVELS = Set.of("error", "warning", "note", "none");

    private PackLoader() {}

    /** Resultado de inspecionar um diretório: o pacote (se não houver erro), os erros e os avisos. */
    public record Report(RulePack pack, List<String> errors, List<String> warnings) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** Pacotes aceitos e, por diretório, os motivos da rejeição dos demais. */
    public record Discovery(List<RulePack> packs, Map<String, List<String>> rejected, List<String> warnings) {}

    /** Carrega o pacote ou lança {@link PackException} com todos os problemas. */
    public static RulePack load(Path dir) {
        Report r = inspect(dir);
        if (!r.ok()) {
            throw new PackException(r.errors());
        }
        return r.pack();
    }

    /** Todo diretório filho de {@code root} que tem {@code pack.yml}; os inválidos são rejeitados com os motivos. */
    public static Discovery discover(Path root) {
        List<RulePack> packs = new ArrayList<>();
        Map<String, List<String>> rejected = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return new Discovery(packs, rejected, warnings);
        }
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.sorted().toList()) {
                if (!Files.isDirectory(child) || !Files.isRegularFile(child.resolve("pack.yml"))) {
                    continue;
                }
                Report r = inspect(child);
                if (r.ok()) {
                    packs.add(r.pack());
                    r.warnings().forEach(w -> warnings.add(r.pack().id() + ": " + w));
                } else {
                    rejected.put(child.getFileName().toString(), r.errors());
                }
            }
        } catch (IOException e) {
            rejected.put(root.toString(), List.of("não foi possível listar o diretório: " + e.getMessage()));
        }
        return new Discovery(packs, rejected, warnings);
    }

    /** Inspeciona o diretório sem lançar exceção. */
    public static Report inspect(Path dir) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            errors.add("não é um diretório: " + dir);
            return new Report(null, errors, warnings);
        }
        checkTree(dir, errors);
        Path manifestFile = dir.resolve("pack.yml");
        if (!Files.isRegularFile(manifestFile)) {
            errors.add("pack.yml não encontrado");
            return new Report(null, errors, warnings);
        }

        PackManifest manifest = parseManifest(manifestFile, errors);
        Set<String> ruleIds = new HashSet<>();
        int ruleCount = parseSemgrepRules(dir, ruleIds, errors);
        warnUntested(dir, ruleIds, warnings);
        List<RemediationEntry> remediation = parseRemediation(dir, errors);
        List<DedupTopic> dedup = parseDedup(dir, errors);
        List<ToolSpec> tools = parseTools(dir, errors);
        ArchitecturePolicy architecture = parseArchitecture(dir, errors);

        if (!errors.isEmpty() || manifest == null) {
            return new Report(null, errors, warnings);
        }
        return new Report(new RulePack(manifest, dir.toAbsolutePath().normalize(), remediation, dedup, architecture,
                tools, ruleCount), errors, warnings);
    }

    // ------------------------------------------------------------------ estrutura

    private static void checkTree(Path dir, List<String> errors) {
        try (Stream<Path> all = Files.walk(dir)) {
            int[] count = {0};
            all.forEach(p -> {
                if (++count[0] > MAX_FILES && count[0] == MAX_FILES + 1) {
                    errors.add("pacote com arquivos demais (limite " + MAX_FILES + ")");
                }
                if (Files.isSymbolicLink(p)) {
                    errors.add("link simbólico não é permitido: " + dir.relativize(p));
                } else if (Files.isRegularFile(p)) {
                    try {
                        String n = p.getFileName().toString();
                        if ((n.endsWith(".yml") || n.endsWith(".yaml")) && Files.size(p) > MAX_FILE_BYTES) {
                            errors.add(dir.relativize(p) + ": arquivo YAML maior que 1 MB");
                        }
                    } catch (IOException e) {
                        errors.add(dir.relativize(p) + ": ilegível (" + e.getMessage() + ")");
                    }
                }
            });
        } catch (IOException e) {
            errors.add("não foi possível percorrer o pacote: " + e.getMessage());
        }
    }

    private static List<Path> yamlFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p)
                    && (p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Lê o YAML ou registra o erro de leitura/sintaxe; devolve {@code null} se não deu. */
    private static Object read(Path file, Path base, List<String> errors) {
        String name = base.relativize(file).toString();
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return null;                              // já reportado em checkTree
            }
            return Yamls.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage()).lines().findFirst().orElse("");
            errors.add(name + ": YAML inválido: " + msg);
            return null;
        }
    }

    private static void checkKeys(Map<String, Object> m, Set<String> allowed, String where, List<String> errors) {
        for (String k : m.keySet()) {
            if (!allowed.contains(k)) {
                errors.add(where + ": chave desconhecida '" + k + "' (permitidas: " + String.join(", ", allowed) + ")");
            }
        }
    }

    private static Severity severity(Map<String, Object> m, String key, Severity fallback, String where, List<String> errors) {
        String v = Yamls.str(m, key);
        if (v == null) {
            return fallback;
        }
        try {
            return Severity.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(where + "." + key + ": severidade inválida '" + v + "' (use CRITICAL, HIGH, MEDIUM, LOW ou INFO)");
            return fallback;
        }
    }

    private static Category category(Map<String, Object> m, String key, Category fallback, String where, List<String> errors) {
        String v = Yamls.str(m, key);
        if (v == null) {
            return fallback;
        }
        try {
            return Category.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(where + "." + key + ": categoria inválida '" + v + "' (use SECURITY, QUALITY, ARCHITECTURE, DEPENDENCY, OBSERVABILITY ou PERFORMANCE)");
            return fallback;
        }
    }

    // ------------------------------------------------------------------ pack.yml

    private static PackManifest parseManifest(Path file, List<String> errors) {
        Object root = read(file, file.getParent(), errors);
        Map<String, Object> m = Yamls.map(root);
        if (m == null) {
            if (root != null) {
                errors.add("pack.yml: esperava um mapa de chaves");
            }
            return null;
        }
        checkKeys(m, Set.of("id", "name", "version", "description", "author", "languages", "enabled-by-default",
                "min-sentinel"), "pack.yml", errors);
        String id = Yamls.str(m, "id");
        String name = Yamls.str(m, "name");
        String version = Yamls.str(m, "version");
        if (id == null) {
            errors.add("pack.yml: 'id' é obrigatório");
        } else if (!PackManifest.ID.matcher(id).matches()) {
            errors.add("pack.yml: id '" + id + "' inválido (minúsculas, números e hífen; 2 a 63 caracteres)");
        }
        if (name == null) {
            errors.add("pack.yml: 'name' é obrigatório");
        }
        if (version == null) {
            errors.add("pack.yml: 'version' é obrigatório");
        } else if (!PackManifest.VERSION.matcher(version).matches()) {
            errors.add("pack.yml: version '" + version + "' inválida (use x.y.z, ex.: 1.0.0)");
        }
        List<String> languages = Yamls.strings(m, "languages", errors, "pack.yml");
        if (id == null || name == null || version == null) {
            return null;
        }
        return new PackManifest(id, name, version, Yamls.str(m, "description"), Yamls.str(m, "author"),
                languages == null ? List.of() : languages, Yamls.bool(m, "enabled-by-default", true));
    }

    // ------------------------------------------------------------------ regras Semgrep

    private static int parseSemgrepRules(Path dir, Set<String> ids, List<String> errors) {
        Path rulesRoot = dir.resolve("rules");
        int count = 0;
        for (Path file : yamlFiles(rulesRoot)) {
            String name = dir.relativize(file).toString();
            Object root = read(file, dir, errors);
            if (root == null) {
                continue;
            }
            Map<String, Object> top = Yamls.map(root);
            List<Object> rules = top == null ? null : Yamls.list(top.get("rules"));
            if (rules == null) {
                errors.add(name + ": esperava a chave 'rules' com uma lista de regras");
                continue;
            }
            for (int i = 0; i < rules.size(); i++) {
                Map<String, Object> r = Yamls.map(rules.get(i));
                String where = name + " regra #" + (i + 1);
                if (r == null) {
                    errors.add(where + ": esperava um mapa");
                    continue;
                }
                String id = Yamls.str(r, "id");
                if (id == null || !SEMGREP_ID.matcher(id).matches()) {
                    errors.add(where + ": 'id' ausente ou inválido");
                } else {
                    where = name + " regra '" + id + "'";
                    if (!ids.add(id)) {
                        errors.add(where + ": id repetido no pacote");
                    }
                }
                if (Yamls.str(r, "message") == null) {
                    errors.add(where + ": 'message' é obrigatório");
                }
                String sev = Yamls.str(r, "severity");
                if (sev == null || !SEMGREP_SEVERITY.contains(sev.toUpperCase(Locale.ROOT))) {
                    errors.add(where + ": 'severity' deve ser ERROR, WARNING ou INFO");
                }
                List<String> langs = Yamls.strings(r, "languages", errors, where);
                if (langs == null || langs.isEmpty()) {
                    errors.add(where + ": 'languages' é obrigatório");
                }
                boolean hasMatcher = r.keySet().stream().anyMatch(k -> k.startsWith("pattern")) || r.containsKey("mode");
                if (!hasMatcher) {
                    errors.add(where + ": nenhuma condição de busca (pattern, patterns, pattern-either, pattern-regex...)");
                }
                Map<String, Object> meta = Yamls.map(r.get("metadata"));
                if (meta != null && meta.get("status") != null
                        && !RULE_STATUS.contains(String.valueOf(meta.get("status")).toLowerCase(Locale.ROOT))) {
                    errors.add(where + ": metadata.status deve ser draft, experimental, stable ou deprecated");
                }
                count++;
            }
        }
        return count;
    }

    /** Regra sem caso de teste "ruleid:" é aviso (não impede o uso, mas o CLI em modo estrito reprova). */
    private static void warnUntested(Path dir, Set<String> ruleIds, List<String> warnings) {
        if (ruleIds.isEmpty()) {
            return;
        }
        Path tests = dir.resolve("tests");
        StringBuilder all = new StringBuilder();
        if (Files.isDirectory(tests)) {
            try (Stream<Path> s = Files.walk(tests)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) {
                    try {
                        if (Files.size(p) <= MAX_FILE_BYTES) {
                            all.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
                        }
                    } catch (IOException | RuntimeException ignored) {
                        // arquivo binário/ilegível não conta como teste
                    }
                }
            } catch (IOException ignored) {
                // sem testes legíveis: todas as regras avisam abaixo
            }
        }
        String text = all.toString();
        for (String id : ruleIds.stream().sorted().toList()) {
            if (!Pattern.compile("ruleid:\\s*" + Pattern.quote(id) + "(\\s|$)").matcher(text).find()) {
                warnings.add("regra '" + id + "' sem caso de teste (// ruleid: " + id + ")");
            }
        }
    }

    // ------------------------------------------------------------------ remediação, dedup, ferramentas

    private static List<Map<String, Object>> items(Object root, String file, List<String> errors) {
        List<Object> list = Yamls.list(root);
        if (list == null) {
            if (root != null) {
                errors.add(file + ": esperava uma lista");
            }
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> m = Yamls.map(list.get(i));
            if (m == null) {
                errors.add(file + " item #" + (i + 1) + ": esperava um mapa");
            } else {
                out.add(m);
            }
        }
        return out;
    }

    private static List<RemediationEntry> parseRemediation(Path dir, List<String> errors) {
        List<RemediationEntry> out = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Path file : yamlFiles(dir.resolve("remediation"))) {
            String name = dir.relativize(file).toString();
            for (Map<String, Object> m : items(read(file, dir, errors), name, errors)) {
                checkKeys(m, Set.of("key", "summary", "before", "after", "refs", "effort", "regression-risk"), name, errors);
                String key = Yamls.str(m, "key");
                String summary = Yamls.str(m, "summary");
                if (key == null || summary == null) {
                    errors.add(name + ": 'key' e 'summary' são obrigatórios em cada item");
                    continue;
                }
                if (!keys.add(key.toLowerCase(Locale.ROOT))) {
                    errors.add(name + ": key '" + key + "' repetida no pacote");
                }
                List<String> refs = Yamls.strings(m, "refs", errors, name);
                String effort = Yamls.str(m, "effort");
                if (effort != null) {
                    effort = effort.trim().toUpperCase(Locale.ROOT);
                    if (!Set.of("S", "M", "L").contains(effort)) {
                        errors.add(name + ": 'effort' de '" + key + "' deve ser S, M ou L");
                        effort = null;
                    }
                }
                String risk = Yamls.str(m, "regression-risk");
                if (risk != null) {
                    risk = risk.trim().toUpperCase(Locale.ROOT);
                    if (!Set.of("LOW", "MEDIUM", "HIGH").contains(risk)) {
                        errors.add(name + ": 'regression-risk' de '" + key + "' deve ser LOW, MEDIUM ou HIGH");
                        risk = null;
                    }
                }
                out.add(new RemediationEntry(key.toLowerCase(Locale.ROOT), summary, Yamls.str(m, "before"),
                        Yamls.str(m, "after"), refs == null ? List.of() : refs, effort, risk));
            }
        }
        return out;
    }

    private static List<DedupTopic> parseDedup(Path dir, List<String> errors) {
        Path file = dir.resolve("dedup.yml");
        List<DedupTopic> out = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        for (Map<String, Object> m : items(read(file, dir, errors), "dedup.yml", errors)) {
            checkKeys(m, Set.of("name", "rules", "max-lines"), "dedup.yml", errors);
            String name = Yamls.str(m, "name");
            List<String> rules = Yamls.strings(m, "rules", errors, "dedup.yml");
            if (name == null || rules == null || rules.size() < 2) {
                errors.add("dedup.yml: cada tópico precisa de 'name' e de ao menos 2 'rules' (as equivalentes)");
                continue;
            }
            Set<String> lower = new HashSet<>();
            rules.forEach(r -> lower.add(r.toLowerCase(Locale.ROOT)));
            out.add(new DedupTopic(name, lower, Yamls.integer(m, "max-lines", 1, errors, "dedup.yml")));
        }
        return out;
    }

    private static List<ToolSpec> parseTools(Path dir, List<String> errors) {
        List<ToolSpec> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path file : yamlFiles(dir.resolve("tools"))) {
            String name = dir.relativize(file).toString();
            for (Map<String, Object> m : items(read(file, dir, errors), name, errors)) {
                checkKeys(m, Set.of("id", "name", "command", "input", "category", "timeout-seconds", "severity-map", "stdout", "applies-to"), name, errors);
                String id = Yamls.str(m, "id");
                if (id == null || !PackManifest.ID.matcher(id).matches()) {
                    errors.add(name + ": 'id' ausente ou inválido");
                    continue;
                }
                if (!ids.add(id)) {
                    errors.add(name + ": ferramenta '" + id + "' repetida");
                }
                List<String> command = Yamls.strings(m, "command", errors, name + " '" + id + "'");
                String input = Yamls.str(m, "input");
                boolean hasCmd = command != null && !command.isEmpty();
                if (hasCmd == (input != null)) {
                    errors.add(name + " '" + id + "': informe exatamente um entre 'command' e 'input'");
                    continue;
                }
                boolean stdout = Yamls.bool(m, "stdout", false);
                if (hasCmd && !stdout && command.stream().noneMatch(a -> a.contains("{output}"))) {
                    errors.add(name + " '" + id + "': o comando precisa do placeholder {output} (onde a ferramenta grava o SARIF) ou 'stdout: true'");
                }
                if (hasCmd && stdout && command.stream().anyMatch(a -> a.contains("{output}"))) {
                    errors.add(name + " '" + id + "': com 'stdout: true' o comando não usa {output}");
                }
                if (!hasCmd && stdout) {
                    errors.add(name + " '" + id + "': 'stdout' só vale com 'command'");
                }
                List<String> applies = Yamls.strings(m, "applies-to", errors, name + " '" + id + "'");
                if (applies == null) {
                    applies = List.of();
                }
                for (String g : applies) {
                    if (g.startsWith("/") || g.contains("..")) {
                        errors.add(name + " '" + id + "': 'applies-to' deve ser glob relativo ao projeto, sem '..'");
                    }
                }
                if (hasCmd && command.stream().anyMatch(a -> a.equals("{files}")) && applies.isEmpty()) {
                    errors.add(name + " '" + id + "': {files} exige 'applies-to'");
                }
                if (input != null && (input.startsWith("/") || input.contains(".."))) {
                    errors.add(name + " '" + id + "': 'input' deve ser um glob relativo ao projeto, sem '..'");
                }
                Map<String, Severity> levels = new LinkedHashMap<>();
                Map<String, Object> sm = Yamls.map(m.get("severity-map"));
                if (sm != null) {
                    for (String lvl : sm.keySet()) {
                        if (!LEVELS.contains(lvl)) {
                            errors.add(name + " '" + id + "': nível '" + lvl + "' inválido em severity-map (error, warning, note, none)");
                        } else {
                            levels.put(lvl, severity(sm, lvl, Severity.MEDIUM, name + " '" + id + "'.severity-map", errors));
                        }
                    }
                }
                out.add(new ToolSpec(id, Yamls.str(m, "name") == null ? id : Yamls.str(m, "name"),
                        hasCmd ? command : List.of(), input,
                        category(m, "category", Category.QUALITY, name + " '" + id + "'", errors), levels,
                        Yamls.integer(m, "timeout-seconds", 600, errors, name), stdout, applies));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ architecture.yml

    private static ArchitecturePolicy parseArchitecture(Path dir, List<String> errors) {
        Path file = dir.resolve("architecture.yml");
        if (!Files.isRegularFile(file)) {
            return ArchitecturePolicy.EMPTY;
        }
        Object root = read(file, dir, errors);
        Map<String, Object> m = Yamls.map(root);
        if (m == null) {
            if (root != null) {
                errors.add("architecture.yml: esperava um mapa de chaves");
            }
            return ArchitecturePolicy.EMPTY;
        }
        String f = "architecture.yml";
        checkKeys(m, Set.of("layers", "forbid", "allow", "strict", "conventions", "required-files", "components"), f, errors);

        List<ArchitecturePolicy.Layer> layers = new ArrayList<>();
        Set<String> layerNames = new HashSet<>();
        for (Map<String, Object> l : items(m.get("layers"), f + " layers", errors)) {
            checkKeys(l, Set.of("name", "paths", "packages"), f + " layers", errors);
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
        List<ArchitecturePolicy.LayerRule> forbid = layerRules(m.get("forbid"), "forbid", layerNames, f, errors);
        List<ArchitecturePolicy.LayerRule> allow = layerRules(m.get("allow"), "allow", layerNames, f, errors);

        List<ArchitecturePolicy.Convention> conventions = new ArrayList<>();
        Set<String> convIds = new HashSet<>();
        for (Map<String, Object> c : items(m.get("conventions"), f + " conventions", errors)) {
            checkKeys(c, Set.of("id", "title", "files", "exclude", "require", "forbid", "message", "severity", "category"), f + " conventions", errors);
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
            conventions.add(new ArchitecturePolicy.Convention(id, Yamls.str(c, "title") == null ? id : Yamls.str(c, "title"),
                    files == null ? List.of() : files, exclude == null ? List.of() : exclude, require, forb,
                    Yamls.str(c, "message"), severity(c, "severity", Severity.MEDIUM, where, errors),
                    category(c, "category", Category.ARCHITECTURE, where, errors)));
        }

        List<ArchitecturePolicy.RequiredFile> required = new ArrayList<>();
        for (Map<String, Object> r : items(m.get("required-files"), f + " required-files", errors)) {
            checkKeys(r, Set.of("path", "message", "severity"), f + " required-files", errors);
            String path = Yamls.str(r, "path");
            if (path == null) {
                errors.add(f + " required-files: 'path' é obrigatório");
                continue;
            }
            required.add(new ArchitecturePolicy.RequiredFile(path, Yamls.str(r, "message"),
                    severity(r, "severity", Severity.LOW, f + " required-file '" + path + "'", errors)));
        }

        ArchitecturePolicy.Components components = parseComponents(Yamls.map(m.get("components")), f, errors);
        return new ArchitecturePolicy(layers, forbid, allow, Yamls.bool(m, "strict", false), conventions, required, components);
    }

    private static List<ArchitecturePolicy.LayerRule> layerRules(Object raw, String kind, Set<String> layerNames,
                                                               String f, List<String> errors) {
        List<ArchitecturePolicy.LayerRule> out = new ArrayList<>();
        for (Map<String, Object> r : items(raw, f + " " + kind, errors)) {
            checkKeys(r, Set.of("from", "to", "message", "severity"), f + " " + kind, errors);
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
                    severity(r, "severity", Severity.MEDIUM, f + " " + kind + " " + from + "->" + to, errors)));
        }
        return out;
    }

    private static ArchitecturePolicy.Components parseComponents(Map<String, Object> m, String f, List<String> errors) {
        if (m == null) {
            return ArchitecturePolicy.EMPTY.components();
        }
        checkKeys(m, Set.of("forbidden", "min-version", "required", "approved-only"), f + " components", errors);
        return new ArchitecturePolicy.Components(
                depRules(m.get("forbidden"), "forbidden", false, Severity.HIGH, f, errors),
                depRules(m.get("min-version"), "min-version", true, Severity.HIGH, f, errors),
                depRules(m.get("required"), "required", false, Severity.MEDIUM, f, errors),
                depRules(m.get("approved-only"), "approved-only", false, Severity.LOW, f, errors));
    }

    private static List<ArchitecturePolicy.DepRule> depRules(Object raw, String kind, boolean needsVersion,
                                                           Severity fallback, String f, List<String> errors) {
        List<ArchitecturePolicy.DepRule> out = new ArrayList<>();
        for (Map<String, Object> d : items(raw, f + " components." + kind, errors)) {
            checkKeys(d, Set.of("dep", "version", "reason", "severity"), f + " components." + kind, errors);
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
                    severity(d, "severity", fallback, f + " components." + kind + " '" + dep + "'", errors)));
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
