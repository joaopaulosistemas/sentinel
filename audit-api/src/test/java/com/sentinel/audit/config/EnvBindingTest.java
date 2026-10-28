package com.sentinel.audit.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toda variável de ambiente que o docker-compose.yml e o chart Helm entregam à API precisa estar ligada a uma
 * propriedade em application.properties (via {@code ${NOME:padrão}}). O SmallRye só mapeia sozinho o nome
 * "sentinel.audit.x.y" para SENTINEL_AUDIT_X_Y; um nome curto como TRIVY_ENABLED ou PACKS_DISABLED é ignorado em
 * silêncio se ninguém o referenciar — foi o que aconteceu quando as ligações foram removidas por engano.
 */
class EnvBindingTest {

    /** Variáveis lidas por outra via que não application.properties (infra do contêiner/Quarkus). */
    private static final Set<String> FORA_DO_PROPERTIES = Set.of(
            "JAVA_TOOL_OPTIONS");

    private static Path raiz() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.isRegularFile(p.resolve("docker-compose.yml"))) {
            p = p.getParent();
        }
        assertTrue(p != null, "docker-compose.yml não encontrado acima de " + System.getProperty("user.dir"));
        return p;
    }

    private static String ler(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** Nomes de variável do bloco "environment:" do serviço audit-api no compose. */
    static Set<String> variaveisDoCompose(String compose) {
        Set<String> out = new TreeSet<>();
        List<String> linhas = compose.lines().toList();
        boolean noServico = false;
        boolean noEnv = false;
        for (String l : linhas) {
            if (l.matches("^  [A-Za-z0-9_-]+:\\s*$")) {
                noServico = l.trim().equals("audit-api:");
                noEnv = false;
                continue;
            }
            if (!noServico) {
                continue;
            }
            if (l.matches("^    environment:\\s*$")) {
                noEnv = true;
                continue;
            }
            if (noEnv) {
                if (l.matches("^    \\S.*")) {
                    noEnv = false;                       // próxima chave do serviço
                    continue;
                }
                Matcher m = Pattern.compile("^      ([A-Z][A-Z0-9_]*):").matcher(l);
                if (m.find()) {
                    out.add(m.group(1));
                }
            }
        }
        return out;
    }

    static Set<String> variaveisDoHelm(String yaml) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("\\{\\s*name:\\s*([A-Z][A-Z0-9_]*)\\s*,").matcher(yaml);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static boolean ligada(String properties, String var) {
        return properties.contains("${" + var + ":") || properties.contains("${" + var + "}");
    }

    private static void confere(String origem, Set<String> vars, String properties) {
        assertFalse(vars.isEmpty(), "nenhuma variável encontrada em " + origem + " (o parser quebrou?)");
        Set<String> soltas = new TreeSet<>();
        for (String v : vars) {
            if (!FORA_DO_PROPERTIES.contains(v) && !ligada(properties, v)) {
                soltas.add(v);
            }
        }
        assertTrue(soltas.isEmpty(), origem + " define variáveis que a API ignora (sem ${VAR:padrão} em "
                + "application.properties): " + soltas);
    }

    @Test
    void variaveisDoComposeEstaoLigadas() throws IOException {
        Path r = raiz();
        confere("docker-compose.yml", variaveisDoCompose(ler(r.resolve("docker-compose.yml"))),
                ler(r.resolve("audit-api/src/main/resources/application.properties")));
    }

    @Test
    void variaveisDoHelmEstaoLigadas() throws IOException {
        Path r = raiz();
        confere("deploy/helm/sentinel/templates/api.yaml",
                variaveisDoHelm(ler(r.resolve("deploy/helm/sentinel/templates/api.yaml"))),
                ler(r.resolve("audit-api/src/main/resources/application.properties")));
    }

    @Test
    void parserDoComposeLeSoOServicoDaApi() {
        String compose = """
                services:
                  postgres:
                    environment:
                      POSTGRES_DB: x
                  audit-api:
                    environment:
                      DB_URL: a
                      # comentário
                      TRIVY_ENABLED: ${TRIVY_ENABLED:-true}
                    ports:
                      - "1:1"
                  audit-dashboard:
                    environment:
                      API_UPSTREAM: y
                """;
        assertTrue(variaveisDoCompose(compose).equals(Set.of("DB_URL", "TRIVY_ENABLED")),
                variaveisDoCompose(compose).toString());
    }
}
