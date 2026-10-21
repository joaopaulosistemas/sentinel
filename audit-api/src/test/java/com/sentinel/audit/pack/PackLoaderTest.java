package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static com.sentinel.audit.pack.PackFixtures.pack;
import static com.sentinel.audit.pack.PackFixtures.packWithRule;
import static com.sentinel.audit.pack.PackFixtures.tempRoot;
import static com.sentinel.audit.pack.PackFixtures.write;
import static org.junit.jupiter.api.Assertions.*;

class PackLoaderTest {

    private static String all(List<String> errors) {
        return String.join(" | ", errors);
    }

    // ------------------------------------------------------------------ pacotes embutidos

    @Test
    void ospacotesEmbutidosSaoValidosECompletos() {
        PackLoader.Discovery d = PackLoader.discover(TestPacks.builtinRoot());

        assertEquals(java.util.Map.of(), d.rejected(), "nenhum pacote embutido pode ser rejeitado");
        assertEquals(List.of("core-architecture", "core-frontend", "core-iac", "core-quality", "core-security", "core-spring", "market-tools", "ts-clean-code"),
                d.packs().stream().map(RulePack::id).sorted().collect(Collectors.toList()));
        RulePack quality = d.packs().stream().filter(p -> p.id().equals("core-quality")).findFirst().get();
        assertEquals(35, quality.semgrepRuleCount());
        assertNotNull(quality.rulesDir());
        assertTrue(quality.remediation().size() >= 40);
        assertTrue(quality.dedup().size() >= 6);
        // as regras de qualidade têm teste; o aviso só pode vir de regras sem caso "ruleid:"
        assertTrue(d.warnings().stream().noneMatch(w -> w.startsWith("core-quality") && w.contains("sentinel-ts-")),
                "regras TypeScript têm teste: " + d.warnings());
    }

    // ------------------------------------------------------------------ manifesto

    @Test
    void pacoteMinimoEValido() throws IOException {
        Path root = tempRoot();
        RulePack p = PackLoader.load(pack(root, "meu-pacote", "1.2.3", true));

        assertEquals("meu-pacote", p.id());
        assertEquals("1.2.3", p.manifest().version());
        assertTrue(p.manifest().enabledByDefault());
        assertNull(p.rulesDir(), "sem regras de código");
        assertTrue(p.architecture().isEmpty());
    }

    @Test
    void relataTodosOsProblemasDoManifestoDeUmaVez() throws IOException {
        Path dir = tempRoot().resolve("x");
        write(dir.resolve("pack.yml"), "description: sem nada\nnmae: typo\n");

        PackException e = assertThrows(PackException.class, () -> PackLoader.load(dir));
        String msg = all(e.problems());
        assertTrue(msg.contains("'id' é obrigatório"), msg);
        assertTrue(msg.contains("'name' é obrigatório"), msg);
        assertTrue(msg.contains("'version' é obrigatório"), msg);
        assertTrue(msg.contains("chave desconhecida 'nmae'"), "erro de digitação precisa aparecer: " + msg);
    }

    @Test
    void rejeitaIdEVersaoMalFormados() throws IOException {
        Path root = tempRoot();
        write(root.resolve("a/pack.yml"), "id: Pacote_Ruim\nname: x\nversion: 1.0.0\n");
        write(root.resolve("b/pack.yml"), "id: bom\nname: x\nversion: um\n");

        assertTrue(all(PackLoader.inspect(root.resolve("a")).errors()).contains("id 'Pacote_Ruim' inválido"));
        assertTrue(all(PackLoader.inspect(root.resolve("b")).errors()).contains("version 'um' inválida"));
    }

    @Test
    void yamlComSintaxeInvalidaEReportadoComOArquivo() throws IOException {
        Path dir = tempRoot().resolve("x");
        write(dir.resolve("pack.yml"), "id: [abc\nname: x\n");

        assertTrue(all(PackLoader.inspect(dir).errors()).contains("pack.yml: YAML inválido"));
    }

    @Test
    void yamlNaoInstanciaClassesJava() throws IOException {
        Path dir = pack(tempRoot(), "malicioso", "1.0.0", true);
        write(dir.resolve("remediation/r.yml"),
                "- key: x\n  summary: !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL [\"http://127.0.0.1:1/x\"]]]]\n");

        PackLoader.Report r = PackLoader.inspect(dir);

        assertFalse(r.ok(), "tag de classe Java deve ser recusada pelo parser seguro");
        assertTrue(all(r.errors()).contains("YAML inválido"), all(r.errors()));
    }

    @Test
    void linkSimbolicoNoPacoteERejeitado() throws IOException {
        Path dir = pack(tempRoot(), "com-link", "1.0.0", true);
        Files.createSymbolicLink(dir.resolve("atalho.yml"), Path.of("/etc/passwd"));

        assertTrue(all(PackLoader.inspect(dir).errors()).contains("link simbólico não é permitido: atalho.yml"));
    }

    // ------------------------------------------------------------------ regras Semgrep

    @Test
    void validaCadaRegraSemgrep() throws IOException {
        Path dir = pack(tempRoot(), "regras", "1.0.0", true);
        write(dir.resolve("rules/r.yml"), """
                rules:
                  - id: sem-mensagem
                    severity: WARNING
                    languages: [java]
                    pattern: foo()
                  - id: severidade-ruim
                    message: m
                    severity: ALTA
                    languages: [java]
                    pattern: foo()
                  - id: sem-linguagem
                    message: m
                    severity: INFO
                    pattern: foo()
                  - id: sem-condicao
                    message: m
                    severity: INFO
                    languages: [java]
                  - id: status-ruim
                    message: m
                    severity: INFO
                    languages: [java]
                    pattern: foo()
                    metadata: {status: quase}
                  - id: sem-mensagem
                    message: m
                    severity: INFO
                    languages: [java]
                    pattern: foo()
                """);

        String msg = all(PackLoader.inspect(dir).errors());

        assertTrue(msg.contains("regra 'sem-mensagem': 'message' é obrigatório"), msg);
        assertTrue(msg.contains("regra 'severidade-ruim': 'severity' deve ser ERROR, WARNING ou INFO"), msg);
        assertTrue(msg.contains("regra 'sem-linguagem': 'languages' é obrigatório"), msg);
        assertTrue(msg.contains("regra 'sem-condicao': nenhuma condição de busca"), msg);
        assertTrue(msg.contains("regra 'status-ruim': metadata.status deve ser"), msg);
        assertTrue(msg.contains("regra 'sem-mensagem': id repetido no pacote"), msg);
    }

    @Test
    void regraSemTesteEAvisoENaoErro() throws IOException {
        Path dir = pack(tempRoot(), "sem-teste", "1.0.0", true);
        write(dir.resolve("rules/r.yml"), PackFixtures.RULE);

        PackLoader.Report r = PackLoader.inspect(dir);

        assertTrue(r.ok(), "regra sem teste não impede o uso");
        assertEquals(1, r.pack().semgrepRuleCount());
        assertTrue(all(r.warnings()).contains("regra 'minha-regra' sem caso de teste"), all(r.warnings()));
    }

    @Test
    void regraComTesteNaoGeraAviso() throws IOException {
        PackLoader.Report r = PackLoader.inspect(packWithRule(tempRoot(), "com-teste", "1.0.0"));

        assertTrue(r.ok());
        assertEquals(List.of(), r.warnings());
    }

    // ------------------------------------------------------------------ remediação, dedup, ferramentas

    @Test
    void validaRemediacaoDedupEFerramentas() throws IOException {
        Path dir = pack(tempRoot(), "dados", "1.0.0", true);
        write(dir.resolve("remediation/r.yml"), "- key: a\n  summary: ok\n- summary: sem chave\n- key: A\n  summary: repetida\n");
        write(dir.resolve("dedup.yml"), "- name: sozinha\n  rules: [so-uma]\n");
        write(dir.resolve("tools/t.yml"), """
                - id: dupla
                  command: [x, "{output}"]
                  input: "*.sarif"
                - id: sem-output
                  command: [x, --out, fixo.sarif]
                - id: fora
                  input: "../fora/*.sarif"
                - id: nivel-ruim
                  input: "*.sarif"
                  severity-map: {grave: HIGH}
                """);

        String msg = all(PackLoader.inspect(dir).errors());

        assertTrue(msg.contains("'key' e 'summary' são obrigatórios"), msg);
        assertTrue(msg.contains("key 'A' repetida no pacote"), "chaves comparadas sem diferenciar maiúsculas: " + msg);
        assertTrue(msg.contains("cada tópico precisa de 'name' e de ao menos 2 'rules'"), msg);
        assertTrue(msg.contains("informe exatamente um entre 'command' e 'input'"), msg);
        assertTrue(msg.contains("precisa do placeholder {output}"), msg);
        assertTrue(msg.contains("sem '..'"), msg);
        assertTrue(msg.contains("nível 'grave' inválido"), msg);
    }

    @Test
    void leFerramentaSarifValida() throws IOException {
        Path dir = pack(tempRoot(), "sarif", "1.0.0", true);
        write(dir.resolve("tools/t.yml"), """
                - id: spotbugs
                  name: SpotBugs
                  command: [spotbugs-sarif, --project, "{project}", --output, "{output}"]
                  category: quality
                  timeout-seconds: 120
                  severity-map: {error: critical, note: low}
                - id: ci-reports
                  input: "reports/*.sarif"
                """);

        RulePack p = PackLoader.load(dir);

        assertEquals(2, p.tools().size());
        ToolSpec sb = p.tools().get(0);
        assertTrue(sb.executes());
        assertEquals(120, sb.timeoutSeconds());
        assertEquals(com.sentinel.audit.model.Severity.CRITICAL, sb.levelSeverity().get("error"));
        assertFalse(p.tools().get(1).executes());
        assertEquals("reports/*.sarif", p.tools().get(1).input());
    }

    // ------------------------------------------------------------------ arquitetura

    @Test
    void validaPoliticaDeArquitetura() throws IOException {
        Path dir = pack(tempRoot(), "arq", "1.0.0", true);
        write(dir.resolve("architecture.yml"), """
                layers:
                  - {name: resource, paths: ["**/resource/**"]}
                  - {name: vazia}
                forbid:
                  - {from: resource, to: repositorio}
                conventions:
                  - {id: sem-regex, files: ["**/*.java"]}
                  - {id: regex-ruim, files: ["**/*.java"], require: "("}
                components:
                  min-version:
                    - {dep: "org.x:y", version: "bom"}
                  forbidden:
                    - {reason: sem dep}
                """);

        String msg = all(PackLoader.inspect(dir).errors());

        assertTrue(msg.contains("layer 'vazia': informe 'paths' e/ou 'packages'"), msg);
        assertTrue(msg.contains("camada 'repositorio' não foi definida em 'layers'"), msg);
        assertTrue(msg.contains("convention 'sem-regex': informe 'require' e/ou 'forbid'"), msg);
        assertTrue(msg.contains("convention 'regex-ruim'.require: expressão regular inválida"), msg);
        assertTrue(msg.contains("components.min-version 'org.x:y': 'version' ausente ou inválida"), msg);
        assertTrue(msg.contains("components.forbidden: 'dep' é obrigatório"), msg);
    }

    @Test
    void leArquiteturaValidaEJuntaPoliticasDePacotes() throws IOException {
        Path root = tempRoot();
        Path a = pack(root, "arq-a", "1.0.0", true);
        write(a.resolve("architecture.yml"), """
                layers:
                  - {name: resource, paths: ["**/resource/**"], packages: ["*.resource.*"]}
                  - {name: repository, paths: ["**/repository/**"]}
                forbid:
                  - {from: resource, to: repository, message: "Use o serviço", severity: high}
                required-files:
                  - {path: README.md, severity: low}
                components:
                  forbidden: [{dep: "log4j:log4j", reason: "descontinuada"}]
                  min-version: [{dep: "org.apache.logging.log4j:log4j-core", version: ">=2.17.1"}]
                """);
        Path b = pack(root, "arq-b", "1.0.0", true);
        write(b.resolve("architecture.yml"), """
                layers:
                  - {name: resource, paths: ["**/api/**"]}
                conventions:
                  - {id: roles, files: ["**/*Resource.java"], require: "@RolesAllowed", message: "Proteja o endpoint"}
                """);

        RulePack pa = PackLoader.load(a);
        RulePack pb = PackLoader.load(b);
        ArchitecturePolicy merged = ArchitecturePolicy.merge(List.of(pa.architecture(), pb.architecture()));

        assertEquals(com.sentinel.audit.model.Severity.HIGH, pa.architecture().forbid().get(0).severity());
        assertEquals(2, merged.layers().size());
        assertEquals(List.of("**/api/**"), merged.layers().stream().filter(l -> l.name().equals("resource")).findFirst().get().paths(),
                "camada de mesmo nome é substituída pela do pacote seguinte");
        assertEquals(1, merged.forbid().size());
        assertEquals(1, merged.conventions().size());
        assertEquals(1, merged.requiredFiles().size());
        assertEquals(1, merged.components().forbidden().size());
        assertEquals(">=2.17.1", merged.components().minVersion().get(0).version());
    }

    // ------------------------------------------------------------------ descoberta

    @Test
    void descobertaAceitaOsBonsERejeitaOsRuinsComMotivo() throws IOException {
        Path root = tempRoot();
        pack(root, "bom", "1.0.0", true);
        write(root.resolve("ruim/pack.yml"), "id: ruim\n");
        write(root.resolve("sem-manifesto/arquivo.txt"), "nada");
        write(root.resolve("profiles/p.yml"), "id: p\n");     // não é pacote

        PackLoader.Discovery d = PackLoader.discover(root);

        assertEquals(List.of("bom"), d.packs().stream().map(RulePack::id).collect(Collectors.toList()));
        assertEquals(java.util.Set.of("ruim"), d.rejected().keySet());
        assertTrue(all(d.rejected().get("ruim")).contains("'name' é obrigatório"));
        assertTrue(PackLoader.discover(root.resolve("nao-existe")).packs().isEmpty());
        assertTrue(PackLoader.discover(null).packs().isEmpty());
    }

    @Test
    void ferramentaValidaStdoutAppliesToEFiles() throws Exception {
        Path root = PackFixtures.tempRoot();
        Path dir = PackFixtures.pack(root, "ferr-ok", "1.0.0", true);
        PackFixtures.write(dir.resolve("tools/t.yml"),
                "- id: hl\n  stdout: true\n  applies-to: [\"**/Dockerfile\"]\n  command: [hadolint, -f, sarif, \"{files}\"]\n");
        PackLoader.Report r = PackLoader.inspect(dir);
        assertTrue(r.ok(), r.errors().toString());
        ToolSpec t = r.pack().tools().get(0);
        assertTrue(t.stdout());
        assertEquals(List.of("**/Dockerfile"), t.appliesTo());
    }

    @Test
    void ferramentaInvalidaStdoutComOutputFilesSemAppliesToEGlobFora() throws Exception {
        Path root = PackFixtures.tempRoot();
        Path dir = PackFixtures.pack(root, "ferr-ruim", "1.0.0", true);
        PackFixtures.write(dir.resolve("tools/t.yml"),
                "- id: a1\n  stdout: true\n  command: [x, \"{output}\"]\n"
                        + "- id: b1\n  command: [x, \"{files}\", \"{output}\"]\n"
                        + "- id: c1\n  command: [x]\n"
                        + "- id: d1\n  applies-to: [\"../fora\"]\n  stdout: true\n  command: [x]\n");
        PackLoader.Report r = PackLoader.inspect(dir);
        assertFalse(r.ok());
        String all = String.join("\n", r.errors());
        assertTrue(all.contains("com 'stdout: true' o comando não usa {output}"), all);
        assertTrue(all.contains("{files} exige 'applies-to'"), all);
        assertTrue(all.contains("o comando precisa do placeholder {output}"), all);
        assertTrue(all.contains("sem '..'"), all);
    }

    @Test
    void pacoteMarketToolsEmbutidoEValidoETemAsFerramentas() {
        PackLoader.Report r = PackLoader.inspect(TestPacks.builtinRoot().resolve("market-tools"));
        assertTrue(r.ok(), r.errors().toString());
        assertEquals(8, r.pack().tools().size());
        assertTrue(r.pack().tools().stream().map(ToolSpec::id).toList()
                .containsAll(List.of("njsscan", "stylelint", "checkov", "hadolint", "kube-linter", "kube-score", "checkstyle", "jscpd")));
    }

    @Test
    void pacoteTsCleanCodeEmbutidoEValidoEEstaDesligadoPorPadrao() {
        PackLoader.Report r = PackLoader.inspect(TestPacks.builtinRoot().resolve("ts-clean-code"));
        assertTrue(r.ok(), r.errors().toString());
        assertFalse(r.pack().manifest().enabledByDefault());
        assertEquals(7, r.pack().semgrepRuleCount());
        assertTrue(r.warnings().isEmpty(), "toda regra tem caso de teste: " + r.warnings());
    }
}
