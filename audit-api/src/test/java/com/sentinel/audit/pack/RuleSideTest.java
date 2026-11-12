package com.sentinel.audit.pack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** Cada regra é do back-end, do front-end ou comum; o lado vem de {@code metadata.side} ou, na falta, da linguagem e dos caminhos. */
class RuleSideTest {

    @BeforeEach
    void carregaPacotes() {
        TestPacks.registry();
    }

    @Test
    void linguagemDoLadoDecideQuandoNaoHaCaminhos() {
        assertEquals(RuleSide.BACK, RuleSide.infer(List.of("java"), List.of()));
        assertEquals(RuleSide.FRONT, RuleSide.infer(List.of("typescript", "javascript"), List.of()));
        assertEquals(RuleSide.COMMON, RuleSide.infer(List.of("dockerfile"), List.of()));
    }

    @Test
    void regraDeTextoUsaAsExtensoesDosCaminhos() {
        assertEquals(RuleSide.FRONT, RuleSide.infer(List.of("regex"), List.of("*.html", "*.component.ts")));
        assertEquals(RuleSide.BACK, RuleSide.infer(List.of("regex"), List.of("*.java", "*.xml")));
        assertEquals(RuleSide.COMMON, RuleSide.infer(List.of("regex"), List.of("*.java", "*.ts")));
        assertEquals(RuleSide.COMMON, RuleSide.infer(List.of("regex"), List.of("*Dockerfile*")));
        assertEquals(RuleSide.COMMON, RuleSide.infer(List.of("regex"), List.of("*.yml", "*.yaml")));
        assertEquals(RuleSide.COMMON, RuleSide.infer(null, null));
    }

    @Test
    void linguagensMistasDeixamOsCaminhosDecidir() {
        assertEquals(RuleSide.BACK, RuleSide.infer(List.of("java", "typescript"), List.of("*.java")));
        assertEquals(RuleSide.COMMON, RuleSide.infer(List.of("java", "typescript"), List.of()));
        assertEquals(RuleSide.COMMON, RuleSide.infer(List.of("java", "typescript"), null));
    }

    @Test
    void ladoEscritoNaRegraValeMaisQueAInferencia() {
        Map<String, Object> rule = Map.of("id", "x", "languages", List.of("typescript"),
                "metadata", Map.of("side", "back", "category", "security"));
        RuleCatalog.Entry entry = RuleCatalog.entryOf("pacote", rule);
        assertEquals("back", entry.side());
        assertEquals("security", entry.category());
    }

    @Test
    void ladoInvalidoCaiNaInferencia() {
        Map<String, Object> rule = Map.of("id", "x", "languages", List.of("java"), "metadata", Map.of("side", "meio"));
        assertEquals("back", RuleCatalog.entryOf("pacote", rule).side());
        assertNull(RuleSide.parse("meio"));
        assertEquals(RuleSide.FRONT, RuleSide.parse(" Front "));
    }

    @Test
    void todaRegraDosPacotesEmbutidosTemLadoEOsConhecidosBatem() {
        List<RuleCatalog.Entry> all = RuleCatalog.of(TestPacks.registry().snapshot().packs());
        assertFalse(all.isEmpty());
        Map<String, String> sides = new java.util.HashMap<>();
        for (RuleCatalog.Entry e : all) {
            assertNotNull(RuleSide.parse(e.side()), "regra sem lado válido: " + e.id());
            sides.put(e.id(), e.side());
        }
        assertEquals("back", sides.get("sentinel-node-sql-injection"));      // Node/Express é back-end
        assertEquals("back", sides.get("sentinel-jpa-n-mais-um"));
        assertEquals("back", sides.get("sentinel-spring-actuator-exposto-yaml"));
        assertEquals("front", sides.get("sentinel-ng-bypass-security"));
        assertEquals("front", sides.get("sentinel-boas-ts-strict-desligado"));
        assertEquals("common", sides.get("sentinel-docker-sem-user"));
        assertEquals("common", sides.get("sentinel-k8s-roda-como-root"));
    }

    @Test
    void osTresLadosTemRegras() {
        List<RuleCatalog.Entry> all = RuleCatalog.of(TestPacks.registry().snapshot().packs());
        for (String side : List.of("back", "front", "common")) {
            assertTrue(all.stream().anyMatch(e -> side.equals(e.side())), "nenhuma regra do lado " + side);
        }
    }

    // ------------------------------------------------------------------ guarda: lado de regra de código nunca fica por esquecimento

    /** Regras que a dedução classifica como comum sem que o autor tenha escrito o lado e sem serem de infraestrutura. */
    private static List<String> regrasComumPorDuvida(List<Map<String, Object>> rules, String origem) {
        List<String> suspeitas = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            Map<String, Object> metadata = Yamls.map(rule.get("metadata"));
            String escrito = metadata == null ? null : Yamls.str(metadata, "side");
            if (RuleSide.parse(escrito) != null) {
                continue;
            }
            Map<String, Object> paths = Yamls.map(rule.get("paths"));
            List<String> languages = textos(rule.get("languages"));
            List<String> includes = textos(paths == null ? null : paths.get("include"));
            if (RuleSide.infer(languages, includes) == RuleSide.COMMON && !RuleSide.isInfrastructure(languages, includes)) {
                suspeitas.add(origem + ": " + Yamls.str(rule, "id"));
            }
        }
        return suspeitas;
    }

    private static List<String> textos(Object value) {
        if (value instanceof List<?> l) {
            return l.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        return value == null ? List.of() : List.of(String.valueOf(value));
    }

    @Test
    void regraDeCodigoQueADeducaoDeixaComumSemLadoEscritoFalhaComOId() {
        Map<String, Object> mista = Map.of("id", "sentinel-mista", "languages", List.of("java", "typescript"));
        Map<String, Object> textoJavaETs = Map.of("id", "sentinel-regex", "languages", List.of("regex"),
                "paths", Map.of("include", List.of("*.java", "*.ts")));

        List<String> suspeitas = regrasComumPorDuvida(List.of(mista, textoJavaETs), "pacote");

        assertEquals(List.of("pacote: sentinel-mista", "pacote: sentinel-regex"), suspeitas);
    }

    @Test
    void ladoEscritoOuInfraestruturaNaoPrecisamSerDeclarados() {
        Map<String, Object> declarada = Map.of("id", "a", "languages", List.of("java", "typescript"), "metadata", Map.of("side", "common"));
        Map<String, Object> docker = Map.of("id", "b", "languages", List.of("dockerfile"));
        Map<String, Object> k8s = Map.of("id", "c", "languages", List.of("regex"), "paths", Map.of("include", List.of("**/k8s/**/*.yaml")));
        Map<String, Object> back = Map.of("id", "d", "languages", List.of("java"));

        assertTrue(regrasComumPorDuvida(List.of(declarada, docker, k8s, back), "pacote").isEmpty());
        assertTrue(RuleSide.isInfrastructure(List.of("dockerfile"), null));
        assertTrue(RuleSide.isInfrastructure(List.of("regex"), List.of("docker-compose.yml")));
        assertFalse(RuleSide.isInfrastructure(List.of("java", "typescript"), List.of("*.java")));
        assertFalse(RuleSide.isInfrastructure(null, null));
    }

    @Test
    void nenhumaRegraDosPacotesEmbutidosFicaComumPorDuvida() throws IOException {
        List<String> suspeitas = new ArrayList<>();
        for (RulePack pack : TestPacks.registry().snapshot().packs()) {
            Path dir = pack.rulesDir();
            if (dir == null) {
                continue;
            }
            for (Path file : RuleYaml.yamlFiles(dir)) {
                suspeitas.addAll(regrasComumPorDuvida(RuleYaml.rules(file), pack.id() + "/" + file.getFileName()));
            }
        }
        assertTrue(suspeitas.isEmpty(),
                "regra de código sem metadata.side que a dedução deixou como comum (declare back, front ou common): " + suspeitas);
    }
}
