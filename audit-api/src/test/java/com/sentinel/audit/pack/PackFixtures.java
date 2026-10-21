package com.sentinel.audit.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Monta pacotes em diretórios temporários para os testes. */
public final class PackFixtures {

    private PackFixtures() {}

    public static Path tempRoot() throws IOException {
        return Files.createTempDirectory("packs").toRealPath();
    }

    public static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    public static final String RULE = """
            rules:
              - id: minha-regra
                message: "Não faça isso"
                severity: WARNING
                languages: [java]
                pattern: foo()
            """;

    /** Pacote mínimo e válido. */
    public static Path pack(Path root, String id, String version, boolean enabledByDefault) throws IOException {
        Path dir = root.resolve(id);
        write(dir.resolve("pack.yml"), "id: " + id + "\nname: Pacote " + id + "\nversion: " + version
                + "\nenabled-by-default: " + enabledByDefault + "\n");
        return dir;
    }

    /** Pacote válido com uma regra Semgrep e o teste dela. */
    public static Path packWithRule(Path root, String id, String version) throws IOException {
        Path dir = pack(root, id, version, true);
        write(dir.resolve("rules/r.yml"), RULE);
        write(dir.resolve("tests/r.java"), "// ruleid: minha-regra\nfoo();\n");
        return dir;
    }

    public static void profile(Path root, String name, String yaml) throws IOException {
        write(root.resolve("profiles").resolve(name + ".yml"), yaml);
    }
}
