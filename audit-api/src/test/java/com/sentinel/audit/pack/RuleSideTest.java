package com.sentinel.audit.pack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
}
