package com.sentinel.audit.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Leitura dos arquivos {@code rules/*.yml} de um pacote, comum aos catálogos de regras e do guia. */
final class RuleYaml {

    private RuleYaml() {}

    /** Arquivos {@code .yml} e {@code .yaml} da pasta, em ordem alfabética. */
    static List<Path> yamlFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".yml") || p.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * As regras (cada item de {@code rules:}) do arquivo, como mapas. Itens que não são mapas ficam de fora; arquivo
     * sem a chave {@code rules} dá lista vazia.
     *
     * @throws IOException      se o arquivo não puder ser lido
     * @throws RuntimeException se o YAML for inválido
     */
    static List<Map<String, Object>> rules(Path file) throws IOException {
        Map<String, Object> root = Yamls.map(Yamls.parse(Files.readString(file)));
        List<Object> items = root == null ? null : Yamls.list(root.get("rules"));
        List<Map<String, Object>> rules = new ArrayList<>();
        if (items == null) {
            return rules;
        }
        for (Object item : items) {
            Map<String, Object> rule = Yamls.map(item);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }
}
