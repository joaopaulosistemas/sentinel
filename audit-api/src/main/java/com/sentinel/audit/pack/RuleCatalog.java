package com.sentinel.audit.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

/**
 * Catálogo das regras Semgrep declaradas nos pacotes: id, texto, severidade e categoria. Serve à tela de ajuste do motor,
 * para mostrar o que cada regra faz mesmo quando ela ainda não disparou em nenhuma auditoria.
 * Só leitura; arquivo ilegível é ignorado (o carregamento do pacote já registrou o problema).
 */
public final class RuleCatalog {

    /** Uma regra do catálogo. {@code message} vem numa linha só. */
    public record Entry(String id, String pack, String message, String severity, String category) {}

    private static final Logger LOG = Logger.getLogger(RuleCatalog.class);

    private RuleCatalog() {}

    public static List<Entry> of(List<RulePack> packs) {
        List<Entry> out = new ArrayList<>();
        for (RulePack pack : packs) {
            Path dir = pack.rulesDir();
            if (dir == null) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                List<Path> yamls = files
                        .filter(p -> p.getFileName().toString().endsWith(".yml") || p.getFileName().toString().endsWith(".yaml"))
                        .sorted()
                        .toList();
                for (Path file : yamls) {
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
            Map<String, Object> root = Yamls.map(Yamls.parse(Files.readString(file)));
            List<Object> rules = root == null ? null : Yamls.list(root.get("rules"));
            if (rules == null) {
                return;
            }
            for (Object item : rules) {
                Map<String, Object> rule = Yamls.map(item);
                if (rule == null) {
                    continue;
                }
                String id = Yamls.str(rule, "id");
                if (id == null) {
                    continue;
                }
                String message = Yamls.str(rule, "message");
                Map<String, Object> metadata = Yamls.map(rule.get("metadata"));
                out.add(new Entry(id, packId,
                        message == null ? null : message.replaceAll("\\s+", " ").strip(),
                        Yamls.str(rule, "severity"),
                        metadata == null ? null : Yamls.str(metadata, "category")));
            }
        } catch (IOException | RuntimeException e) {
            LOG.debugf("Arquivo de regras fora do catálogo: %s (%s)", file.getFileName(), e.getMessage());
        }
    }
}
