package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.PackFixtures;
import com.sentinel.audit.pack.PackLoader;
import com.sentinel.audit.pack.RulePack;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ArchitecturePolicyAnalyzerTest {

    private static final String POLICY = """
            layers:
              - {name: resource, paths: ["**/resource/**"]}
              - {name: service, paths: ["**/service/**"]}
              - {name: repository, paths: ["**/repository/**"], packages: ["*.repository.*"]}
              - {name: ui, paths: ["src/ui/**"]}
              - {name: api, paths: ["src/api/**"], packages: ["@app/api*"]}
            forbid:
              - {from: resource, to: repository, severity: high, message: "Passe pelo serviço"}
              - {from: ui, to: api}
            conventions:
              - id: roles
                files: ["**/resource/**/*.java"]
                exclude: ["**/*Test.java"]
                require: "@RolesAllowed"
                severity: medium
              - id: sem-print
                files: ["**/*.java"]
                forbid: "System\\\\.out\\\\.print"
                severity: low
            required-files:
              - {path: README.md, severity: low}
              - {path: "docs/*.md"}
            """;

    private static AuditRun run() {
        AuditRun r = new AuditRun();
        r.setId(1L);
        return r;
    }

    private static AuditPolicy policy(String architectureYaml) throws IOException {
        Path root = PackFixtures.tempRoot();
        Path dir = PackFixtures.pack(root, "arq", "1.0.0", true);
        PackFixtures.write(dir.resolve("architecture.yml"), architectureYaml);
        RulePack p = PackLoader.load(dir);
        return AuditPolicy.defaults(List.of(p));
    }

    private static Path project(String... fileAndContent) throws IOException {
        Path p = Files.createTempDirectory("proj").toRealPath();
        for (int i = 0; i < fileAndContent.length; i += 2) {
            PackFixtures.write(p.resolve(fileAndContent[i]), fileAndContent[i + 1]);
        }
        return p;
    }

    private static List<Finding> analyze(Path proj, AuditPolicy pol) {
        return new ArchitecturePolicyAnalyzer().analyze(proj, run(), pol).findings();
    }

    private static List<String> rules(List<Finding> fs) {
        return fs.stream().map(f -> f.getRuleId() + "@" + f.getFilePath() + ":" + f.getLineNumber()).sorted().collect(Collectors.toList());
    }

    @Test
    void semPoliticaNaoFazNada() throws IOException {
        Path proj = project("a/resource/X.java", "import a.repository.Y;\n");
        assertEquals(0, new ArchitecturePolicyAnalyzer().analyze(proj, run()).findings().size());
        assertEquals(0, analyze(proj, AuditPolicy.defaults(List.of())).size());
    }

    @Test
    void detectaImportJavaProibidoEntreCamadas() throws IOException {
        Path proj = project(
                "src/main/java/app/resource/PedidoResource.java",
                "package app.resource;\n\nimport app.service.PedidoService;\nimport app.repository.PedidoRepository;\n// import app.repository.Comentado;\n@RolesAllowed(\"x\") class PedidoResource {}\n",
                "src/main/java/app/service/PedidoService.java",
                "package app.service;\nimport app.repository.PedidoRepository;\nclass PedidoService {}\n",
                "README.md", "x", "docs/a.md", "x");

        List<Finding> fs = analyze(proj, policy(POLICY));

        assertEquals(List.of("sentinel-arch-layer-resource-repository@src/main/java/app/resource/PedidoResource.java:4"), rules(fs));
        Finding f = fs.get(0);
        assertEquals(Severity.HIGH, f.getSeverity());
        assertEquals("Passe pelo serviço", f.getDescription());
        assertEquals("import app.repository.PedidoRepository;", f.getCodeSnippet());
        assertEquals(com.sentinel.audit.model.Category.ARCHITECTURE, f.getCategory());
    }

    @Test
    void camadaDoArquivoTambemVemDoPacoteDeclarado() throws IOException {
        // o arquivo está fora de qualquer pasta de camada, mas declara o pacote do repositório; importa o resource
        Path proj = project("outro/Solto.java", "package app.repository.impl;\nimport app.resource.X;\nclass Solto {}\n",
                "README.md", "x", "docs/a.md", "x");
        String yaml = POLICY.replace("forbid:\n", "forbid:\n  - {from: repository, to: resource}\n");
        yaml = yaml.replace("paths: [\"**/resource/**\"]}", "paths: [\"**/resource/**\"], packages: [\"*.resource.*\"]}");

        assertEquals(List.of("sentinel-arch-layer-repository-resource@outro/Solto.java:2"), rules(analyze(proj, policy(yaml))));
    }

    @Test
    void detectaImportTypeScriptRelativoEPorPacote() throws IOException {
        Path proj = project(
                "src/ui/Tela.tsx",
                "import React from 'react';\nimport { x } from '../api/client';\nimport y from \"@app/api/outro\";\nconst z = require('./local');\n",
                "src/ui/local.ts", "export const a = 1;\n",
                "src/api/client.ts", "export const x = 1;\n",
                "README.md", "x", "docs/a.md", "x");

        List<Finding> fs = analyze(proj, policy(POLICY));

        assertEquals(List.of("sentinel-arch-layer-ui-api@src/ui/Tela.tsx:2", "sentinel-arch-layer-ui-api@src/ui/Tela.tsx:3"), rules(fs));
        assertEquals(Severity.MEDIUM, fs.get(0).getSeverity(), "sem severidade declarada: padrão do carregador");
    }

    @Test
    void modoEstritoSoAceitaDependenciasListadasEmAllow() throws IOException {
        String yaml = """
                layers:
                  - {name: web, paths: ["web/**"]}
                  - {name: core, paths: ["core/**"]}
                  - {name: data, paths: ["data/**"]}
                strict: true
                allow:
                  - {from: web, to: core}
                  - {from: core, to: data}
                """;
        Path proj = project("web/A.ts", "import '../core/b';\nimport '../data/c';\n", "core/b.ts", "import '../data/c';\n", "data/c.ts", "export {};\n");

        List<Finding> fs = analyze(proj, policy(yaml));

        assertEquals(List.of("sentinel-arch-layer-web-data@web/A.ts:2"), rules(fs));
        assertEquals(Severity.MEDIUM, fs.get(0).getSeverity());
    }

    @Test
    void convencaoExigeEProibeTrechos() throws IOException {
        Path proj = project(
                "a/resource/SemRole.java", "class SemRole {}\n",
                "a/resource/ComRole.java", "@RolesAllowed(\"x\") class ComRole {}\n",
                "a/resource/SemRoleTest.java", "class SemRoleTest {}\n",
                "a/service/Log.java", "class Log {\n  void f() {\n    System.out.println(\"x\");\n  }\n}\n",
                "README.md", "x", "docs/a.md", "x");

        List<Finding> fs = analyze(proj, policy(POLICY));

        assertEquals(List.of("sentinel-arch-conv-roles@a/resource/SemRole.java:1", "sentinel-arch-conv-sem-print@a/service/Log.java:3"), rules(fs));
        assertEquals("System.out.print", fs.stream().filter(f -> f.getRuleId().endsWith("sem-print")).findFirst().get().getCodeSnippet());
    }

    @Test
    void arquivosObrigatoriosAceitamGlob() throws IOException {
        Path proj = project("docs/guia.md", "x");
        List<Finding> fs = analyze(proj, policy(POLICY));
        assertEquals(List.of("sentinel-arch-required-file@README.md:0"), rules(fs), "docs/*.md existe, README.md não");
    }

    @Test
    void ignoraPastasDeDependenciaELinks() throws IOException {
        Path proj = project("node_modules/x/resource/A.java", "import a.repository.B;\n", "README.md", "x", "docs/a.md", "x");
        Files.createSymbolicLink(proj.resolve("link"), Path.of("/etc"));
        assertEquals(List.of(), rules(analyze(proj, policy(POLICY))));
    }

    @Test
    void regexLentaNaoTravaAAuditoria() throws IOException {
        String yaml = """
                conventions:
                  - id: catastrofica
                    files: ["**/*.txt"]
                    forbid: "(a+)+$"
                    severity: low
                """;
        Path proj = project("x.txt", "a".repeat(60) + "!\n");
        long t = System.nanoTime();
        List<Finding> fs = analyze(proj, policy(yaml));
        assertTrue((System.nanoTime() - t) / 1_000_000_000L < 20, "deve abortar pelo orçamento de tempo");
        assertEquals(0, fs.size());
    }

    // ------------------------------------------------------------------ componentes

    private static final String POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion><groupId>g</groupId><artifactId>a</artifactId><version>1</version>
              <dependencies>
                <dependency><groupId>log4j</groupId><artifactId>log4j</artifactId><version>1.2.17</version></dependency>
                <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><version>2.14.1</version></dependency>
                <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.0.0-jre</version></dependency>
              </dependencies>
            </project>
            """;

    @Test
    void politicaDeComponentes() throws IOException {
        String yaml = """
                components:
                  forbidden:
                    - {dep: "log4j:log4j", reason: "descontinuada"}
                  min-version:
                    - {dep: "org.apache.logging.log4j:log4j-core", version: ">=2.17.1", severity: critical}
                  required:
                    - {dep: "org.slf4j:*"}
                """;
        Path proj = project("pom.xml", POM);

        List<Finding> fs = analyze(proj, policy(yaml));

        assertEquals(List.of("sentinel-comp-forbidden@pom.xml:0", "sentinel-comp-min-version@pom.xml:0",
                "sentinel-comp-required@(dependências):0"), rules(fs));
        Finding min = fs.stream().filter(f -> f.getRuleId().endsWith("min-version")).findFirst().get();
        assertEquals(Severity.CRITICAL, min.getSeverity());
        assertTrue(min.getTitle().contains("2.14.1"));
        assertEquals(com.sentinel.audit.model.Category.DEPENDENCY, min.getCategory());
        assertEquals("descontinuada", fs.stream().filter(f -> f.getRuleId().endsWith("forbidden")).findFirst().get().getDescription().split(" \\(")[0]);
    }

    @Test
    void versaoDesconhecidaNaoReprovaPorVersaoMinima() throws IOException {
        String yaml = "components:\n  min-version:\n    - {dep: \"com.google.guava:guava\", version: \">=99\"}\n";
        String pom = POM.replace("33.0.0-jre", "${guava.version}");
        assertEquals(0, analyze(project("pom.xml", pom), policy(yaml)).size(), "não dá para saber: não acusa");
    }

    @Test
    void listaAprovadaIgnoraLockfilesETransitivas() throws IOException {
        String yaml = "components:\n  approved-only:\n    - {dep: \"com.google.*\"}\n    - {dep: \"org.apache.*\"}\n";
        List<Finding> fs = analyze(project("pom.xml", POM), policy(yaml));
        assertEquals(List.of("sentinel-comp-not-approved@pom.xml:0"), rules(fs));
        assertTrue(fs.get(0).getTitle().contains("log4j:log4j"));
    }

    @Test
    void pacoteEmbutidoDeExemploDetectaAViolacaoTipica() throws IOException {
        RulePack arq = PackLoader.load(com.sentinel.audit.pack.TestPacks.builtinRoot().resolve("core-architecture"));
        Path proj = project("src/main/java/app/resource/R.java", "package app.resource;\nimport app.repository.Repo;\n@RolesAllowed(\"x\") class R { }\n",
                "pom.xml", POM);

        List<String> ids = analyze(proj, AuditPolicy.defaults(List.of(arq))).stream().map(Finding::getRuleId).distinct().sorted().toList();

        assertTrue(ids.contains("sentinel-arch-layer-resource-repository"), ids.toString());
        assertTrue(ids.contains("sentinel-comp-forbidden"), ids.toString());
        assertTrue(ids.contains("sentinel-comp-min-version"), ids.toString());
        assertTrue(ids.contains("sentinel-arch-required-file"), "projeto de teste não tem README: " + ids);
    }
}
