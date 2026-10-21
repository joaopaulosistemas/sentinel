package com.sentinel.audit.analyzer.deps;

import com.sentinel.audit.analyzer.OWASPDependencyAnalyzer;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** OSV.dev simulado: nenhuma chamada de rede é feita. */
class OsvClientTest {

    @TempDir
    Path dir;

    /** Servidor OSV falso: log4j-core tem 2 vulnerabilidades; lodash nenhuma. */
    static class FakeOsv extends OsvClient {
        final List<String> calls = new ArrayList<>();
        boolean down;

        FakeOsv() {
            this.enabled = true;
        }

        @Override
        protected String post(String path, String json) throws IOException {
            calls.add("POST " + path);
            if (down) {
                throw new IOException("sem rede");
            }
            // ordem das queries = ordem das dependências: [log4j-core, lodash]
            assertTrue(json.contains("\"ecosystem\":\"Maven\"") && json.contains("org.apache.logging.log4j:log4j-core"));
            return "{\"results\":[{\"vulns\":[{\"id\":\"GHSA-jfh8-c2jp-5v3q\"},{\"id\":\"GHSA-7rjr-3q55-vv33\"}]},{}]}";
        }

        @Override
        protected String get(String path) {
            calls.add("GET " + path);
            if (path.endsWith("GHSA-jfh8-c2jp-5v3q")) {
                return "{\"id\":\"GHSA-jfh8-c2jp-5v3q\",\"aliases\":[\"CVE-2021-44228\"],\"summary\":\"Log4Shell RCE\","
                        + "\"database_specific\":{\"severity\":\"CRITICAL\"},\"affected\":[{\"package\":{\"name\":"
                        + "\"org.apache.logging.log4j:log4j-core\",\"ecosystem\":\"Maven\"},\"ranges\":[{\"type\":\"ECOSYSTEM\","
                        + "\"events\":[{\"introduced\":\"2.0\"},{\"fixed\":\"2.15.0\"}]}]}]}";
            }
            // sem database_specific: severidade vem do vetor CVSS
            return "{\"id\":\"GHSA-7rjr-3q55-vv33\",\"aliases\":[\"CVE-2021-45046\"],\"summary\":\"Incomplete fix\","
                    + "\"severity\":[{\"type\":\"CVSS_V3\",\"score\":\"CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:H\"}],"
                    + "\"affected\":[{\"package\":{\"name\":\"org.apache.logging.log4j:log4j-core\"},\"ranges\":[{\"events\":"
                    + "[{\"introduced\":\"2.0\"},{\"fixed\":\"2.16.0\"}]}]}]}";
        }
    }

    @Test
    void cvssCalculaNotaBase() {
        assertEquals(10.0, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H"));
        assertEquals(9.8, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"));
        assertEquals(8.1, Cvss.baseScore("CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:H"));
        assertEquals(7.5, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N"));
        assertEquals(0.0, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N"));
        assertEquals(-1.0, Cvss.baseScore("AV:N/AC:L"));
    }

    @Test
    void consultaEmLoteENormalizaResultado() throws Exception {
        FakeOsv osv = new FakeOsv();
        Dependency log4j = new Dependency("Maven", "org.apache.logging.log4j:log4j-core", "2.14.1", "pom.xml");
        Dependency lodash = new Dependency("Maven", "x:lodash", "4.17.21", "pom.xml");
        Map<Dependency, List<OsvVulnerability>> r = osv.lookup(List.of(log4j, lodash));

        assertEquals(1, r.size());                       // lodash sem vulns não aparece
        List<OsvVulnerability> v = r.get(log4j);
        assertEquals(2, v.size());
        assertEquals(Severity.CRITICAL, v.get(0).severity());
        assertEquals("CVE-2021-44228", v.get(0).cve());
        assertEquals("2.15.0", v.get(0).fixedVersion());
        assertEquals(Severity.HIGH, v.get(1).severity());  // CVSS 8.1
        assertEquals(1, osv.calls.stream().filter(c -> c.startsWith("POST")).count());
        assertEquals(2, osv.calls.stream().filter(c -> c.startsWith("GET")).count());
    }

    @Test
    void analisadorAgrupaUmFindingPorDependenciaComAMaiorSeveridade() throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project><dependencies><dependency><groupId>org.apache.logging.log4j</groupId>"
                + "<artifactId>log4j-core</artifactId><version>2.14.1</version></dependency><dependency><groupId>x</groupId>"
                + "<artifactId>lodash</artifactId><version>4.17.21</version></dependency></dependencies></project>");
        OWASPDependencyAnalyzer analyzer = new OWASPDependencyAnalyzer();
        setOsv(analyzer, new FakeOsv());

        List<Finding> findings = analyzer.analyze(dir, new AuditRun()).findings();
        assertEquals(1, findings.size());
        Finding f = findings.get(0);
        assertEquals(Severity.CRITICAL, f.getSeverity());
        assertEquals("CVE-2021-44228", f.getCveId());
        assertTrue(f.getTitle().contains("2 vulnerabilidades"));
        assertTrue(f.getDescription().contains("2.16.0"), f.getDescription());  // maior correção
        assertEquals("osv-org.apache.logging.log4j:log4j-core", f.getRuleId());
        assertEquals("pom.xml", f.getFilePath());
    }

    @Test
    void semRedeVoltaParaABaseInterna() throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project><dependencies><dependency><groupId>org.apache.logging.log4j</groupId>"
                + "<artifactId>log4j-core</artifactId><version>2.14.1</version></dependency></dependencies></project>");
        OWASPDependencyAnalyzer analyzer = new OWASPDependencyAnalyzer();
        FakeOsv osv = new FakeOsv();
        osv.down = true;
        setOsv(analyzer, osv);

        List<Finding> findings = analyzer.analyze(dir, new AuditRun()).findings();
        assertEquals(1, findings.size());
        assertEquals("owasp-dependency-CVE-2021-44228-log4j-core", findings.get(0).getRuleId());
    }

    @Test
    void osvDesligadoNaoFazChamadas() throws Exception {
        Files.writeString(dir.resolve("package-lock.json"),
                "{\"lockfileVersion\":3,\"packages\":{\"node_modules/lodash\":{\"version\":\"4.17.15\"}}}");
        OWASPDependencyAnalyzer analyzer = new OWASPDependencyAnalyzer();
        FakeOsv osv = new FakeOsv();
        osv.enabled = false;
        setOsv(analyzer, osv);

        List<Finding> findings = analyzer.analyze(dir, new AuditRun()).findings();
        assertTrue(osv.calls.isEmpty());
        assertEquals(1, findings.size());        // lodash 4.17.15 < 4.17.21 na base interna
    }

    @Test
    void clienteHttpRealFalaComServidorLocalETrataErroHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/querybatch", ex -> {
            byte[] body = "{\"results\":[{\"vulns\":[{\"id\":\"PYSEC-1\"}]}]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/v1/vulns/", ex -> {
            byte[] body = "{\"id\":\"PYSEC-1\",\"summary\":\"x\",\"database_specific\":{\"severity\":\"MODERATE\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            OsvClient client = new OsvClient();
            client.enabled = true;
            client.timeoutSeconds = 5;
            client.baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            Dependency d = new Dependency("PyPI", "django", "3.0.1", "requirements.txt");
            Map<Dependency, List<OsvVulnerability>> r = client.lookup(List.of(d));
            assertEquals(Severity.MEDIUM, r.get(d).get(0).severity());
            assertEquals("PYSEC-1", r.get(d).get(0).id());

            client.baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/inexistente";
            assertThrows(IOException.class, () -> client.lookup(List.of(d)));   // 404 => IOException => fallback
        } finally {
            server.stop(0);
        }
    }

    private static void setOsv(OWASPDependencyAnalyzer analyzer, OsvClient osv) throws Exception {
        var f = OWASPDependencyAnalyzer.class.getDeclaredField("osv");
        f.setAccessible(true);
        f.set(analyzer, osv);
    }
}
