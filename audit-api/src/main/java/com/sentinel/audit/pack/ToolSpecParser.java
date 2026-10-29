package com.sentinel.audit.pack;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Severity;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lê e valida {@code tools/*.yml} de um pacote: ferramentas externas que produzem SARIF. */
final class ToolSpecParser {
    private static final int DEFAULT_TOOL_TIMEOUT_SECONDS = 600;
    private static final Set<String> LEVELS = Set.of("error", "warning", "note", "none");

    private ToolSpecParser() {
    }

    static List<ToolSpec> parse(Path dir, List<String> errors) {
        List<ToolSpec> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path file : PackLoader.yamlFiles(dir.resolve("tools"))) {
            String name = dir.relativize(file).toString();
            for (Map<String, Object> m : PackLoader.items(PackLoader.read(file, dir, errors), name, errors)) {
                ToolSpec spec = parseTool(m, name, ids, errors);
                if (spec != null) {
                    out.add(spec);
                }
            }
        }
        return out;
    }

    /** Uma ferramenta de {@code tools/*.yml}; devolve {@code null} quando o item é inutilizável (o erro já foi registrado). */
    private static ToolSpec parseTool(Map<String, Object> m, String name, Set<String> ids, List<String> errors) {
        PackLoader.checkKeys(m, Set.of("id", "name", "command", "input", "category", "timeout-seconds", "severity-map", "stdout", "applies-to"), name, errors);
        String id = Yamls.str(m, "id");
        if (id == null || !PackManifest.ID.matcher(id).matches()) {
            errors.add(name + ": 'id' ausente ou inválido");
            return null;
        }
        if (!ids.add(id)) {
            errors.add(name + ": ferramenta '" + id + "' repetida");
        }
        String where = name + " '" + id + "'";
        List<String> command = Yamls.strings(m, "command", errors, where);
        String input = Yamls.str(m, "input");
        boolean hasCmd = command != null && !command.isEmpty();
        if (hasCmd == (input != null)) {
            errors.add(where + ": informe exatamente um entre 'command' e 'input'");
            return null;
        }
        boolean stdout = Yamls.bool(m, "stdout", false);
        List<String> args = hasCmd ? command : List.of();
        checkToolOutput(args, hasCmd, stdout, where, errors);
        List<String> applies = Yamls.strings(m, "applies-to", errors, where);
        if (applies == null) {
            applies = List.of();
        }
        checkToolGlobs(args, applies, input, where, errors);
        String toolName = Yamls.str(m, "name");
        return new ToolSpec(id, toolName == null ? id : toolName, args, input,
                PackLoader.category(m, "category", Category.QUALITY, where, errors), toolSeverityMap(m, where, errors),
                Yamls.integer(m, "timeout-seconds", DEFAULT_TOOL_TIMEOUT_SECONDS, errors, name), stdout, applies);
    }

    /** O comando grava o SARIF em {output} ou escreve no stdout; nunca os dois, e 'stdout' só vale com 'command'. */
    private static void checkToolOutput(List<String> command, boolean hasCmd, boolean stdout, String where, List<String> errors) {
        boolean usesOutput = command.stream().anyMatch(a -> a.contains("{output}"));
        if (hasCmd && !stdout && !usesOutput) {
            errors.add(where + ": o comando precisa do placeholder {output} (onde a ferramenta grava o SARIF) ou 'stdout: true'");
        }
        if (hasCmd && stdout && usesOutput) {
            errors.add(where + ": com 'stdout: true' o comando não usa {output}");
        }
        if (!hasCmd && stdout) {
            errors.add(where + ": 'stdout' só vale com 'command'");
        }
    }

    /** Globs de 'applies-to' e 'input' são relativos ao projeto; {files} só faz sentido com 'applies-to'. */
    private static void checkToolGlobs(List<String> command, List<String> applies, String input, String where, List<String> errors) {
        for (String g : applies) {
            if (g.startsWith("/") || g.contains("..")) {
                errors.add(where + ": 'applies-to' deve ser glob relativo ao projeto, sem '..'");
            }
        }
        if (command.stream().anyMatch(a -> a.equals("{files}")) && applies.isEmpty()) {
            errors.add(where + ": {files} exige 'applies-to'");
        }
        if (input != null && (input.startsWith("/") || input.contains(".."))) {
            errors.add(where + ": 'input' deve ser um glob relativo ao projeto, sem '..'");
        }
    }

    private static Map<String, Severity> toolSeverityMap(Map<String, Object> m, String where, List<String> errors) {
        Map<String, Severity> levels = new LinkedHashMap<>();
        Map<String, Object> sm = Yamls.map(m.get("severity-map"));
        if (sm == null) {
            return levels;
        }
        for (String lvl : sm.keySet()) {
            if (LEVELS.contains(lvl)) {
                levels.put(lvl, PackLoader.severity(sm, lvl, Severity.MEDIUM, where + ".severity-map", errors));
            } else {
                errors.add(where + ": nível '" + lvl + "' inválido em severity-map (error, warning, note, none)");
            }
        }
        return levels;
    }
}
