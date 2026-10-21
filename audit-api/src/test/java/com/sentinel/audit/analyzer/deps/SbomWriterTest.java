package com.sentinel.audit.analyzer.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SbomWriterTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void montaPurlPorEcossistema() {
        assertEquals("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1",
                SbomWriter.purl(new Dependency("Maven", "org.apache.logging.log4j:log4j-core", "2.14.1", "pom.xml")));
        assertEquals("pkg:npm/lodash@4.17.20", SbomWriter.purl(new Dependency("npm", "lodash", "4.17.20", "package-lock.json")));
        assertEquals("pkg:npm/%40angular/core@15.0.0", SbomWriter.purl(new Dependency("npm", "@angular/core", "15.0.0", "package.json")));
        assertEquals("pkg:pypi/my-pkg@1.0", SbomWriter.purl(new Dependency("PyPI", "My_Pkg", "1.0", "requirements.txt")));
        assertEquals("pkg:nuget/Newtonsoft.Json@12.0.1", SbomWriter.purl(new Dependency("NuGet", "Newtonsoft.Json", "12.0.1", "a.csproj")));
    }

    @Test
    void semDadosSuficientesNaoGeraPurl() {
        assertNull(SbomWriter.purl(new Dependency("Maven", "semgrupo", "1.0", "pom.xml")));
        assertNull(SbomWriter.purl(new Dependency("Maven", "g:a", "", "pom.xml")));
        assertNull(SbomWriter.purl(new Dependency("Cargo", "serde", "1.0", "Cargo.toml")));
        assertNull(SbomWriter.purl(null));
    }

    @Test
    void geraCycloneDxValidoSemDuplicatas() throws Exception {
        List<Dependency> deps = List.of(
                new Dependency("Maven", "org.example:lib", "1.0", "pom.xml"),
                new Dependency("Maven", "org.example:lib", "1.0", "modulo/pom.xml"),    // mesmo pacote em outro arquivo
                new Dependency("Maven", "org.example:lib", "2.0", "pom.xml"),           // outra versão = outro componente
                new Dependency("Cargo", "serde", "1.0", "Cargo.toml"));                 // não suportado: fica fora

        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        JsonNode bom = M.readTree(SbomWriter.toCycloneDx("Portal", "main", deps, Instant.parse("2026-10-17T12:00:00Z"), id));

        assertEquals("CycloneDX", bom.path("bomFormat").asText());
        assertEquals("1.5", bom.path("specVersion").asText());
        assertEquals("urn:uuid:00000000-0000-0000-0000-000000000001", bom.path("serialNumber").asText());
        assertEquals("Portal", bom.path("metadata").path("component").path("name").asText());
        assertEquals(2, bom.path("components").size());

        JsonNode c = bom.path("components").get(0);
        assertEquals("org.example", c.path("group").asText());
        assertEquals("lib", c.path("name").asText());
        assertEquals("pkg:maven/org.example/lib@1.0", c.path("bom-ref").asText());
        assertEquals(2, c.path("properties").size());      // os dois arquivos de origem
    }

    @Test
    void listaVaziaOuNulaGeraSbomValidoSemComponentes() throws Exception {
        for (List<Dependency> deps : new List[] {List.of(), null}) {
            JsonNode bom = M.readTree(SbomWriter.toCycloneDx(null, null, deps, Instant.now(), UUID.randomUUID()));
            assertEquals(0, bom.path("components").size());
            assertEquals("desconhecido", bom.path("metadata").path("component").path("name").asText());
        }
    }
}
