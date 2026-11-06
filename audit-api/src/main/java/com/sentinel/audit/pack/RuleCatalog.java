package com.sentinel.audit.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

/**
 * Catálogo das regras Semgrep declaradas nos pacotes: id, texto, severidade, categoria e lado (back-end, front-end ou comum). Serve à tela de ajuste do motor,
 * para mostrar o que cada regra faz mesmo quando ela ainda não disparou em nenhuma auditoria.
 * Só leitura; arquivo ilegível é ignorado (o carregamento do pacote já registrou o problema).
 */
public final class RuleCatalog {

    /** Uma regra do catálogo. {@code message} vem numa linha só. */
    public record Entry(String id, String pack, String message, String severity, String category, String side) {}

    private static final Logger LOG = Logger.getLogger(RuleCatalog.class);

    private RuleCatalog() {}

    public static List<Entry> of(List<RulePack> packs) {
        List<Entry> out = new ArrayList<>();
        for (RulePack pack : packs) {
            Path dir = pack.rulesDir();
            if (dir == null) {
                continue;
            }
            try {
                for (Path file : RuleYaml.yamlFiles(dir)) {
                    read(pack.id(), file, out);
                }
            } catch (IOException e) {
                LOG.debugf("Pasta de regras ilegível, o pacote %s segue sem catálogo (%s)", pack.id(), e.getMessage());
            }
        }
        return out;
    }

    private static void read(String packId, Path file, List<Entry> out) {
        try {
            for (Map<String, Object> rule : RuleYaml.rules(file)) {
                Entry entry = entryOf(packId, rule);
                if (entry != null) {
                    out.add(entry);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.debugf("Arquivo de regras fora do catálogo: %s (%s)", file.getFileName(), e.getMessage());
        }
    }

    /** Uma regra do YAML como item do catálogo; {@code null} se não tem id. O lado vem de {@code metadata.side} ou da inferência. */
    static Entry entryOf(String packId, Map<String, Object> rule) {
        String id = Yamls.str(rule, "id");
        if (id == null) {
            return null;
        }
        String message = Yamls.str(rule, "message");
        Map<String, Object> metadata = Yamls.map(rule.get("metadata"));
        RuleSide side = metadata == null ? null : RuleSide.parse(Yamls.str(metadata, "side"));
        if (side == null) {
            side = RuleSide.infer(strings(rule.get("languages")), strings(includeOf(rule)));
        }
        return new Entry(id, packId,
                message == null ? null : message.replaceAll("\\s+", " ").strip(),
                Yamls.str(rule, "severity"),
                metadata == null ? null : Yamls.str(metadata, "category"),
                side.id());
    }

    private static Object includeOf(Map<String, Object> rule) {
        Map<String, Object> paths = Yamls.map(rule.get("paths"));
        return paths == null ? null : paths.get("include");
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> l) {
            return l.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
        }
        return value == null ? List.of() : List.of(String.valueOf(value));
    }
}
