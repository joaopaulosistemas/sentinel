package com.sentinel.audit.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PackTrustTest {

    private static PackRegistry registry(Path extra) {
        PackRegistry r = new PackRegistry();
        r.trustedToolPacks = Optional.of("market-tools");
        if (extra != null) {
            r.extraDirs = Optional.of(extra.toString());
        }
        return r;
    }

    @Test
    void pacoteEmbutidoListadoEConfiavel() throws Exception {
        PackRegistry r = registry(null);
        assertEquals(Set.of("market-tools"), r.trustedBuiltinPackIds(r.snapshot()));
        assertTrue(r.policyFor(null).isToolPackTrusted("market-tools"));
        assertFalse(r.policyFor(null).isToolPackTrusted("core-quality"), "só os listados");
    }

    @Test
    void semConfiguracaoNenhumPacoteEConfiavel() {
        PackRegistry r = registry(null);
        r.trustedToolPacks = Optional.empty();
        assertTrue(r.trustedBuiltinPackIds(r.snapshot()).isEmpty());
    }

    @Test
    void pacoteExternoComMesmoIdENaoConfiavelMesmoSubstituindoOEmbutido() throws Exception {
        Path root = PackFixtures.tempRoot();
        Path dir = PackFixtures.pack(root, "market-tools", "9.9.9", true);
        PackFixtures.write(dir.resolve("tools/t.yml"), "- id: mal\n  command: [\"/bin/echo\", \"{output}\"]\n");
        PackRegistry r = registry(root);

        PackRegistry.Snapshot s = r.snapshot();
        RulePack ativo = s.packs().stream().filter(p -> p.id().equals("market-tools")).findFirst().orElseThrow();
        assertEquals("9.9.9", ativo.manifest().version(), "a versão externa, mais nova, substituiu a embutida");
        assertTrue(r.trustedBuiltinPackIds(s).isEmpty(), "mas não herda a confiança da pasta do produto");
        assertFalse(r.policyFor(null).isToolPackTrusted("market-tools"));
    }
}
