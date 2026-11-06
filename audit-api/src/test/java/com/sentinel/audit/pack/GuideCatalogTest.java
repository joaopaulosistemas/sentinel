package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void recursoPequenoEhLidoPorInteiro() throws IOException {
        byte[] texto = "stacks:\n  - id: java\n".getBytes(StandardCharsets.UTF_8);

        assertEquals("stacks:\n  - id: java\n", GuideCatalog.readBounded(new ByteArrayInputStream(texto), "x.yml"));
    }

    @Test
    void recursoNoLimiteAindaEhLidoEUmByteAMaisEhRecusado() throws IOException {
        byte[] noLimite = new byte[GuideCatalog.MAX_RESOURCE_BYTES];
        java.util.Arrays.fill(noLimite, (byte) 'a');
        byte[] acima = new byte[GuideCatalog.MAX_RESOURCE_BYTES + 1];
        java.util.Arrays.fill(acima, (byte) 'a');

        assertEquals(GuideCatalog.MAX_RESOURCE_BYTES, GuideCatalog.readBounded(new ByteArrayInputStream(noLimite), "x").length());
        assertNull(GuideCatalog.readBounded(new ByteArrayInputStream(acima), "x"));
    }

    @Test
    void recursoDoClasspathExistenteVemEInexistenteDaNulo() {
        assertNotNull(GuideCatalog.resource("/guia/stacks.yml"));
        assertNull(GuideCatalog.resource("/guia/nao-existe.yml"));
    }

    @Test
    void linguagemVemDasExtensoesDosCaminhosNaOrdemDaDeclaracao() {
        Map<String, Object> rule = Map.of(
                "languages", List.of("regex", "Java", "generic"),
                "paths", Map.of("include", List.of("**/*.tsx", "src/app.jsx", "pom.properties", "tsconfig.json", "x.vue", "README.md")));

        assertEquals(List.of("java", "typescript", "javascript", "properties", "vue"), GuideCatalog.languages(rule));
    }

    @Test
    void oLeitorDeRegrasListaOsYamlEmOrdemEPulaItensQueNaoSaoMapas() throws IOException {
        Files.writeString(tempDir.resolve("b.yml"), "rules:\n  - id: regra-b\n    severity: ERROR\n    message: Titulo B. Faca assim.\n");
        Files.writeString(tempDir.resolve("a.yaml"), "rules:\n  - id: regra-a\n    message: Titulo A\n  - message: sem id\n  - texto solto\n");
        Files.writeString(tempDir.resolve("c.yml"), "isso: nao tem regras\n");
        Files.writeString(tempDir.resolve("d.txt"), "rules:\n  - id: ignorado\n");

        assertEquals(List.of(tempDir.resolve("a.yaml"), tempDir.resolve("b.yml"), tempDir.resolve("c.yml")), RuleYaml.yamlFiles(tempDir));
        assertEquals(2, RuleYaml.rules(tempDir.resolve("a.yaml")).size(), "o item que não é mapa fica de fora");
        assertTrue(RuleYaml.rules(tempDir.resolve("c.yml")).isEmpty());
    }
}
