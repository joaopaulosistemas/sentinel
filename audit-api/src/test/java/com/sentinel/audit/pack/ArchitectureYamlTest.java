package com.sentinel.audit.pack;

import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureYamlTest {

    private static Path pacoteCom(String conteudo) throws IOException {
        Path dir = Files.createTempDirectory("arq");
        Files.writeString(dir.resolve("architecture.yml"), conteudo);
        return dir;
    }

    @Test
    void semArquivoDevolveAPoliticaVazia() throws Exception {
        List<String> errors = new ArrayList<>();
        assertSame(ArchitecturePolicy.EMPTY, ArchitectureYaml.parse(Files.createTempDirectory("sem"), errors));
        assertTrue(errors.isEmpty());
    }

    @Test
    void leCamadasERegrasComSeveridade() throws Exception {
        List<String> errors = new ArrayList<>();
        ArchitecturePolicy p = ArchitectureYaml.parse(pacoteCom("""
                strict: true
                layers:
                  - {name: resource, packages: [com.x.resource]}
                  - {name: repository, paths: ["**/repository/**"]}
                forbid:
                  - {from: resource, to: repository, message: "use o service", severity: high}
                allow:
                  - {from: resource, to: resource}
                """), errors);

        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(2, p.layers().size());
        assertEquals("resource", p.layers().get(0).name());
        assertEquals(List.of("**/repository/**"), p.layers().get(1).paths());
        assertEquals(1, p.forbid().size());
        assertEquals(Severity.HIGH, p.forbid().get(0).severity());
        assertEquals("use o service", p.forbid().get(0).message());
        assertEquals(Severity.MEDIUM, p.allow().get(0).severity(), "sem severidade vale MEDIUM");
        assertTrue(p.strict());
    }

    @Test
    void camadaSemCaminhoNemPacoteRepetidaOuSemNomeSaoErros() throws Exception {
        List<String> errors = new ArrayList<>();
        ArchitectureYaml.parse(pacoteCom("""
                layers:
                  - {name: vazia}
                  - {name: dup, paths: ["a/**"]}
                  - {name: dup, paths: ["b/**"]}
                  - {paths: ["c/**"]}
                """), errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("layer 'vazia': informe 'paths' e/ou 'packages'")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("layer 'dup' repetida")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("'name' é obrigatório")), errors.toString());
    }

    @Test
    void regraQueCitaCamadaNaoDefinidaOuSemFromToEErro() throws Exception {
        List<String> errors = new ArrayList<>();
        ArchitectureYaml.parse(pacoteCom("""
                layers:
                  - {name: resource, packages: [com.x]}
                forbid:
                  - {from: resource, to: fantasma}
                  - {from: resource}
                """), errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("camada 'fantasma' não foi definida")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("'from' e 'to' são obrigatórios")), errors.toString());
    }

    @Test
    void chaveDesconhecidaEArquivoQueNaoEMapaViramErroSemExcecao() throws Exception {
        List<String> errors = new ArrayList<>();
        ArchitectureYaml.parse(pacoteCom("camadas: []\n"), errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("camadas")), errors.toString());

        List<String> errors2 = new ArrayList<>();
        assertSame(ArchitecturePolicy.EMPTY, ArchitectureYaml.parse(pacoteCom("- 1\n- 2\n"), errors2));
        assertTrue(errors2.stream().anyMatch(e -> e.contains("esperava um mapa de chaves")), errors2.toString());
    }

    @Test
    void convencaoComIdInvalidoRepetidoOuSemArquivosEErro() throws Exception {
        List<String> errors = new ArrayList<>();
        ArchitectureYaml.parse(pacoteCom("""
                conventions:
                  - {id: Errado_Id, files: ["**/*.java"], require: "x"}
                  - {id: ok-um, files: ["**/*.java"], require: "x"}
                  - {id: ok-um, files: ["**/*.java"], require: "x"}
                  - {id: sem-arquivos, require: "x"}
                """), errors);

        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("id")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("ok-um")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("sem-arquivos")), errors.toString());
    }
}
