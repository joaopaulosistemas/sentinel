package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;

import static com.sentinel.audit.pack.PathRole.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PathRoleTest {

    @Test
    void reconheceTestes() {
        assertEquals(TEST, PathRole.of("audit-api/src/test/java/com/x/FooTest.java"));
        assertEquals(TEST, PathRole.of("src/components/Foo.test.tsx"));
        assertEquals(TEST, PathRole.of("e2e/login.spec.ts"));
        assertEquals(TEST, PathRole.of("app/tests/helpers.py"));
        assertEquals(TEST, PathRole.of("pkg/foo_test.go"));
    }

    @Test
    void reconheceDocumentacaoExemplosEGerado() {
        assertEquals(DOCS, PathRole.of("docs/evolucao/LOG.md"));
        assertEquals(DOCS, PathRole.of("README.md"));
        assertEquals(FIXTURE, PathRole.of("src/main/resources/fixtures/data.json"));
        assertEquals(FIXTURE, PathRole.of("examples/app.yaml"));
        assertEquals(FIXTURE, PathRole.of(".env.example"));
        assertEquals(FIXTURE, PathRole.of("config/app.properties.sample"));
        assertEquals(PRODUCTION, PathRole.of(".env"));
        assertEquals(GENERATED, PathRole.of("target/classes/Foo.class"));
        assertEquals(GENERATED, PathRole.of("web/node_modules/x/index.js"));
        assertEquals(GENERATED, PathRole.of("static/app.min.js"));
        assertEquals(GENERATED, PathRole.of("api/opensource/ntlm/ntlm.js"));
        assertEquals(GENERATED, PathRole.of("src/third_party/lib/util.js"));
        assertEquals(PRODUCTION, PathRole.of("src/opensource.ts"));
    }

    @Test
    void codigoDeProducaoNaoViraTesteSoPeloNomeParecido() {
        assertEquals(PRODUCTION, PathRole.of("audit-api/src/main/java/com/x/AuditCli.java"));
        assertEquals(PRODUCTION, PathRole.of("src/main/java/com/x/Audit.java"));
        assertEquals(PRODUCTION, PathRole.of("src/main/java/com/x/LatestVersion.java"));
        assertEquals(PRODUCTION, PathRole.of("bin/start.sh"));
        assertEquals(PRODUCTION, PathRole.of(null));
        assertEquals(PRODUCTION, PathRole.of(""));
        assertEquals(PRODUCTION, PathRole.of("src\\main\\App.java"));
    }

    @Test
    void windowsEMaiusculasSaoTratadosIgual() {
        assertEquals(TEST, PathRole.of("src\\Test\\Java\\FooTest.java"));
        assertEquals(DOCS, PathRole.of("Docs/Guia.html"));
    }
}
