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

    private static final String MODULES = """
            modules:
              - id: modulos
                root: com.acme
                no-cycles: true
                public-api: [api, spi]
                severity: high
            """;

    @Test
    void detectaCicloEntreModulos() throws IOException {
        Path proj = project(
                "src/com/acme/pedido/PedidoService.java", "package com.acme.pedido.api;\nimport com.acme.cliente.api.ClienteApi;\nclass A {}\n",
                "src/com/acme/cliente/ClienteService.java", "package com.acme.cliente.api;\nimport com.acme.pedido.api.PedidoApi;\nclass B {}\n",
                "src/com/acme/relatorio/R.java", "package com.acme.relatorio;\nimport com.acme.pedido.api.PedidoApi;\nclass R {}\n");

        List<Finding> fs = analyze(proj, policy(MODULES));

        assertEquals(1, fs.size(), rules(fs).toString());
        assertEquals("sentinel-arch-module-cycle", fs.get(0).getRuleId());
        assertEquals(Severity.HIGH, fs.get(0).getSeverity());
        assertTrue(fs.get(0).getDescription().contains("cliente") && fs.get(0).getDescription().contains("pedido"));
        assertFalse(fs.get(0).getDescription().contains("relatorio"));
    }

    @Test
    void acessoAoInternoDeOutroModuloEhProibidoMasApiEClasseDaRaizSaoLivres() throws IOException {
        Path proj = project(
                "src/com/acme/pedido/P.java",
                "package com.acme.pedido;\nimport com.acme.cliente.internal.Repo;\nimport com.acme.cliente.api.ClienteApi;\n"
                        + "import com.acme.cliente.ClienteFacade;\nimport com.acme.pedido.internal.Meu;\nimport java.util.List;\nclass P {}\n");

        List<Finding> fs = analyze(proj, policy(MODULES));

        assertEquals(List.of("sentinel-arch-module-internal@src/com/acme/pedido/P.java:2"), rules(fs));
        assertEquals("import com.acme.cliente.internal.Repo;", fs.get(0).getCodeSnippet());
    }

    @Test
    void semCicloNemAcessoInternoNaoReportaNada() throws IOException {
        Path proj = project(
                "src/com/acme/a/A.java", "package com.acme.a;\nimport com.acme.b.api.B;\nclass A {}\n",
                "src/com/acme/b/B.java", "package com.acme.b.api;\nclass B {}\n");

        assertEquals(0, analyze(proj, policy(MODULES)).size());
    }

    @Test
    void cicloDeTresModulosEReportadoUmaVez() throws IOException {
        Path proj = project(
                "a/A.java", "package com.acme.a;\nimport com.acme.b.X;\nclass A {}\n",
                "b/B.java", "package com.acme.b;\nimport com.acme.c.X;\nclass B {}\n",
                "c/C.java", "package com.acme.c;\nimport com.acme.a.X;\nclass C {}\n");

        List<Finding> fs = analyze(proj, policy(MODULES));

        assertEquals(1, fs.size());
        assertTrue(fs.get(0).getDescription().contains("a") && fs.get(0).getDescription().contains("b") && fs.get(0).getDescription().contains("c"));
    }

    @Test
    void politicaDeModulosInvalidaEhRejeitadaNoCarregamento() throws IOException {
        Path root = PackFixtures.tempRoot();
        Path dir = PackFixtures.pack(root, "arq", "1.0.0", true);
        PackFixtures.write(dir.resolve("architecture.yml"), "modules:\n  - {id: m, no-cycles: true}\n");
        assertThrows(RuntimeException.class, () -> PackLoader.load(dir));
    }

    private static List<String> ruleIds(List<Finding> fs) {
        return fs.stream().map(Finding::getRuleId).distinct().sorted().toList();
    }

    private static AuditPolicy builtin(String pack) {
        return AuditPolicy.defaults(List.of(PackLoader.load(com.sentinel.audit.pack.TestPacks.builtinRoot().resolve(pack))));
    }

    @Test
    void pacoteHexagonalPegaDominioQueConheceAdaptadorEFrameworkMasAceitaNucleoLimpo() throws IOException {
        Path proj = project(
                "src/main/java/app/domain/Pedido.java",
                "package app.domain;\nimport app.adapter.out.PedidoJpa;\nimport jakarta.persistence.Entity;\n@Transactional class Pedido {}\n",
                "src/main/java/app/application/CriarPedido.java",
                "package app.application;\nimport app.domain.Pedido;\nimport app.adapter.out.PedidoJpa;\nimport jakarta.ws.rs.core.Response;\nclass CriarPedido {}\n",
                "src/main/java/app/adapter/in/rest/PedidoResource.java",
                "package app.adapter.in.rest;\nimport app.application.CriarPedido;\nimport app.adapter.out.PedidoJpa;\nclass PedidoResource {}\n",
                "src/main/java/app/adapter/out/PedidoJpa.java",
                "package app.adapter.out;\nimport app.domain.Pedido;\nimport jakarta.persistence.Entity;\nclass PedidoJpa {}\n");

        List<String> ids = ruleIds(analyze(proj, builtin("arq-hexagonal")));

        assertEquals(List.of("sentinel-arch-conv-aplicacao-sem-web", "sentinel-arch-conv-dominio-sem-framework",
                "sentinel-arch-conv-dominio-sem-transacao", "sentinel-arch-layer-adapter-in-adapter-out",
                "sentinel-arch-layer-application-adapter-out", "sentinel-arch-layer-domain-adapter-out"), ids);
    }

    @Test
    void pacoteHexagonalNaoReportaNadaQuandoADependenciaApontaParaDentro() throws IOException {
        Path proj = project(
                "src/main/java/app/domain/Pedido.java", "package app.domain;\nimport java.util.List;\nclass Pedido {}\n",
                "src/main/java/app/domain/PedidoRepository.java", "package app.domain;\ninterface PedidoRepository {}\n",
                "src/main/java/app/application/CriarPedido.java", "package app.application;\nimport app.domain.PedidoRepository;\nclass CriarPedido {}\n",
                "src/main/java/app/adapter/out/PedidoJpa.java", "package app.adapter.out;\nimport app.domain.PedidoRepository;\nimport app.application.CriarPedido;\nclass PedidoJpa {}\n");

        assertEquals(List.of(), rules(analyze(proj, builtin("arq-hexagonal"))));
    }

    @Test
    void pacoteFsdProibeImportarDeCamadaAcimaEFurarAApiPublicaDaFatia() throws IOException {
        Path proj = project(
                "src/entities/usuario/model.ts", "import { Tela } from '@/features/login';\nexport const u = 1;\n",
                "src/features/login/index.ts", "export * from './ui';\nexport { x } from './model';\n",
                "src/pages/home/Home.tsx", "import { Botao } from '@/features/login/ui/Botao';\nimport { Login } from '@/features/login';\n",
                "src/shared/api/pedido.ts", "export function calcularPedido() {}\n");

        List<String> ids = ruleIds(analyze(proj, builtin("arq-frontend-fsd")));

        assertEquals(List.of("sentinel-arch-conv-fatia-sem-import-profundo", "sentinel-arch-conv-shared-sem-regra-de-negocio", "sentinel-arch-layer-entities-features"), ids);
    }

    @Test
    void pacoteFsdAceitaCamadasQueSoImportamDeBaixo() throws IOException {
        Path proj = project(
                "src/pages/home/Home.tsx", "import { Login } from '@/features/login';\nimport { Usuario } from '@/entities/usuario';\n",
                "src/features/login/index.ts", "export { Login } from './ui/Login';\n",
                "src/features/login/ui/Login.tsx", "import { Usuario } from '@/entities/usuario';\nimport { http } from '@/shared/api';\n",
                "src/entities/usuario/index.ts", "export type Usuario = { id: string };\n");

        assertEquals(List.of(), rules(analyze(proj, builtin("arq-frontend-fsd"))));
    }

    @Test
    void pacoteMonolitoModularEstaDesligadoPorPadraoEFuncionaComARaizAjustada() throws IOException {
        RulePack pack = PackLoader.load(com.sentinel.audit.pack.TestPacks.builtinRoot().resolve("arq-monolito-modular"));
        assertFalse(pack.manifest().enabledByDefault());
        Path proj = project(
                "a/A.java", "package com.example.a;\nimport com.example.b.internal.X;\nclass A {}\n",
                "b/B.java", "package com.example.b;\nimport com.example.a.api.Y;\nclass B {}\n");

        List<String> ids = ruleIds(analyze(proj, AuditPolicy.defaults(List.of(pack))));

        assertEquals(List.of("sentinel-arch-module-cycle", "sentinel-arch-module-internal"), ids);
    }

    private static final String TS_MODULES = """
            modules:
              - {id: fatias, root: src/features, alias: "@/features", no-cross: true, no-cycles: true, public-api: [index], severity: medium}
            """;

    @Test
    void fatiasIrmasNaoSeImportamEImportProfundoFuraOIndex() throws IOException {
        Path proj = project(
                "src/features/login/ui/Login.tsx", "import { Carrinho } from '@/features/carrinho';\nimport { x } from '../model';\n",
                "src/features/carrinho/index.ts", "export { Carrinho } from './ui/Carrinho';\n",
                "src/features/carrinho/ui/Carrinho.tsx", "import { y } from '../../login/ui/Login';\n",
                "src/pages/Home.tsx", "import { Login } from '@/features/login/ui/Login';\nimport { Carrinho } from '@/features/carrinho';\n");

        List<Finding> fs = analyze(proj, policy(TS_MODULES));

        assertEquals(List.of(
                "sentinel-arch-module-cross@src/features/carrinho/ui/Carrinho.tsx:1",
                "sentinel-arch-module-cross@src/features/login/ui/Login.tsx:1",
                "sentinel-arch-module-cycle@src/features/carrinho/ui/Carrinho.tsx:1"), rules(fs).stream().filter(x -> !x.contains("internal")).toList());
    }

    @Test
    void fatiaImportadaPeloIndexDeOutraCamadaNaoReportaNada() throws IOException {
        Path proj = project(
                "src/features/login/index.ts", "export const a = 1;\n",
                "src/features/login/ui/Login.tsx", "import { a } from '../index';\nimport { b } from './util';\n");

        assertEquals(List.of(), rules(analyze(proj, policy(TS_MODULES))));
    }

    @Test
    void raizDePastasSemAliasEhRejeitada() throws IOException {
        Path root = PackFixtures.tempRoot();
        Path dir = PackFixtures.pack(root, "arq", "1.0.0", true);
        PackFixtures.write(dir.resolve("architecture.yml"), "modules:\n  - {id: m, root: src/features}\n");
        assertThrows(RuntimeException.class, () -> PackLoader.load(dir));
    }

    @Test
    void pacoteFsdPegaFatiaIrmaNaMesmaCamada() throws IOException {
        Path proj = project(
                "src/features/login/index.ts", "import { c } from '@/features/carrinho';\nexport const l = 1;\n",
                "src/features/carrinho/index.ts", "export const c = 1;\n");

        assertEquals(List.of("sentinel-arch-module-cross"), ruleIds(analyze(proj, builtin("arq-frontend-fsd"))));
    }

    @Test
    void pacoteNxPegaUiQueBuscaDadosBibliotecaQueImportaDoAppECicloEntreEscopos() throws IOException {
        Path proj = project(
                "libs/loja/ui-lista/src/lista.component.ts",
                "import { HttpClient } from '@angular/common/http';\nimport { Cart } from '@org/loja/feature-carrinho';\n@Component({standalone: false}) export class L {}\n",
                "libs/loja/util-formato/src/f.ts", "import { app } from '../../../../apps/web/src/main';\n",
                "libs/loja/feature-carrinho/src/c.ts", "import { x } from '@org/estoque/data-access-itens';\n",
                "libs/estoque/data-access-itens/src/d.ts", "import { y } from '@org/loja/util-formato';\n");

        List<String> ids = ruleIds(analyze(proj, builtin("arq-angular-nx")));

        assertEquals(List.of("sentinel-arch-conv-componente-standalone", "sentinel-arch-conv-ui-sem-acesso-a-dados",
                "sentinel-arch-layer-nx-ui-nx-feature", "sentinel-arch-layer-nx-util-nx-app", "sentinel-arch-module-cycle"), ids);
    }

    @Test
    void pacoteNxAceitaFeatureUsandoUiDataAccessEUtil() throws IOException {
        Path proj = project(
                "libs/loja/feature-carrinho/src/c.ts",
                "import { L } from '@org/loja/ui-lista';\nimport { D } from '@org/loja/data-access-itens';\nimport { f } from '@org/loja/util-formato';\n",
                "libs/loja/data-access-itens/src/d.ts", "import { f } from '@org/loja/util-formato';\n",
                "libs/loja/ui-lista/src/l.ts", "import { f } from '@org/loja/util-formato';\n",
                "apps/web/src/main.ts", "import { C } from '@org/loja/feature-carrinho';\n");

        assertEquals(List.of(), rules(analyze(proj, builtin("arq-angular-nx"))));
    }

    @Test
    void pacoteHexagonalTrataApplicationDomainComoAplicacaoENaoComoDominioPuro() throws IOException {
        // layout do buckpal: o serviço de caso de uso fica em application/domain/service e usa @Transactional
        Path proj = project("src/main/java/app/application/domain/service/SendMoneyService.java",
                "package app.application.domain.service;\nimport jakarta.transaction.Transactional;\n@Transactional\nclass SendMoneyService {}\n");

        assertEquals(List.of(), rules(analyze(proj, builtin("arq-hexagonal"))));
    }

    @Test
    void pacoteFsdFuncionaEmSubprojetoDeUmMonorepo() throws IOException {
        Path proj = project(
                "exemplos/loja/src/entities/usuario/model.ts", "import { Tela } from '@/features/login';\n",
                "exemplos/loja/src/features/login/index.ts", "export const t = 1;\n");

        assertEquals(List.of("sentinel-arch-layer-entities-features"), ruleIds(analyze(proj, builtin("arq-frontend-fsd"))));
    }
}
