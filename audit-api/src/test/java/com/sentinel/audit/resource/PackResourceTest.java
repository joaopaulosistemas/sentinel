package com.sentinel.audit.resource;

import com.sentinel.audit.pack.TestPacks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackResourceTest {

    @Test
    void listaPacotesEmbutidosSemExporCaminhos() {
        PackResource.PacksResponse r = PackResource.toResponse(TestPacks.registry().snapshot());

        assertEquals(java.util.List.of("core-architecture", "core-corporativo", "core-frontend", "core-iac", "core-persistencia", "core-quality", "core-security", "core-spring", "market-tools", "ts-clean-code"),
                r.packs().stream().map(PackResource.PackInfo::id).toList());
        PackResource.PackInfo q = r.packs().stream().filter(p -> p.id().equals("core-quality")).findFirst().orElseThrow();
        assertEquals(41, q.semgrepRules());
        assertTrue(q.remediations() > 40);
        assertNotNull(r.loadedAt());
        assertEquals(0, r.rejected().size());
        assertFalse(r.toString().contains("/tmp/") || r.toString().contains("audit-rules"), "nenhum caminho do servidor na resposta");
    }
}
