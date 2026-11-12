package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O lado de cada ferramenta externa embutida em {@code market-tools}: o que a dedução por {@code applies-to} não acerta é declarado. */
class MarketToolsSideTest {

    private static Map<String, RuleSide> sidesByTool() {
        RulePack market = TestPacks.registry().snapshot().packs().stream()
                .filter(p -> "market-tools".equals(p.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("pacote market-tools ausente"));
        Map<String, RuleSide> sides = new HashMap<>();
        market.tools().forEach(t -> sides.put(t.id(), t.side()));
        return sides;
    }

    @Test
    void todaFerramentaEmbutidaTemLado() {
        Map<String, RuleSide> sides = sidesByTool();
        assertTrue(sides.size() >= 13, "esperava as 13 ferramentas do market-tools, vieram " + sides.keySet());
        sides.forEach((id, side) -> assertNotNull(side, "ferramenta sem lado: " + id));
    }

    @Test
    void osLadosBatemComAAnaliseDeCadaFerramenta() {
        Map<String, RuleSide> sides = sidesByTool();
        // analisam só um lado
        assertEquals(RuleSide.FRONT, sides.get("stylelint"));
        assertEquals(RuleSide.FRONT, sides.get("dependency-cruiser"));
        assertEquals(RuleSide.BACK, sides.get("checkstyle"));
        assertEquals(RuleSide.BACK, sides.get("joern-taint"));
        // Node.js/Express é back-end, mesmo com arquivos .js e .ts (a dedução diria front)
        assertEquals(RuleSide.BACK, sides.get("njsscan"));
        // SQL fica sempre ligado (a dedução pela extensão .sql diria back)
        assertEquals(RuleSide.COMMON, sides.get("squawk"));
        assertEquals(RuleSide.COMMON, sides.get("sqlfluff"));
        // olham os dois lados ou a infraestrutura
        assertEquals(RuleSide.COMMON, sides.get("jscpd"));
        assertEquals(RuleSide.COMMON, sides.get("checkov"));
        assertEquals(RuleSide.COMMON, sides.get("hadolint"));
        assertEquals(RuleSide.COMMON, sides.get("kube-linter"));
        assertEquals(RuleSide.COMMON, sides.get("kube-score"));
        assertEquals(RuleSide.COMMON, sides.get("spectral"));
    }
}
