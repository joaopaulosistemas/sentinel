package com.sentinel.audit.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** O CLI contra uma API simulada (HttpServer local). */
class AuditCliTest {

    @TempDir
    Path tmp;

    private HttpServer server;
    private final List<String> authHeaders = new ArrayList<>();
    private volatile String auditJson;
    private volatile String findingsJson;
    private volatile String diffJson;
    private volatile int createStatus;

    @BeforeEach
    void start() throws IOException {
        createStatus = 202;
        auditJson = "{\"id\":5,\"systemName\":\"Portal\",\"branch\":\"main\",\"status\":\"COMPLETED\","
                + "\"riskScore\":{\"overall\":80.0,\"security\":70.0,\"quality\":90.0,\"architecture\":90.0,\"dependencies\":90.0,\"observability\":90.0},"
                + "\"findingsSummary\":{\"CRITICAL\":0,\"HIGH\":1,\"MEDIUM\":0,\"LOW\":0,\"INFO\":0}}";
        findingsJson = "[{\"id\":1,\"severity\":\"HIGH\",\"category\":\"SECURITY\",\"title\":\"X\",\"ruleId\":\"r\",\"filePath\":\"A.java\",\"lineNumber\":3}]";
        diffJson = "{\"hasBaseline\":true,\"baseAuditId\":4,\"newFindings\":[],\"resolvedFindings\":[],\"persistingCount\":1}";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/audits", ex -> {
            authHeaders.add(ex.getRequestHeaders().getFirst("X-API-Key") + "|" + ex.getRequestHeaders().getFirst("Authorization"));
            String path = ex.getRequestURI().getPath();
            if (ex.getRequestMethod().equals("POST")) {
                reply(ex, createStatus, createStatus == 202 ? "{\"id\":5}" : "{\"message\":\"nao\"}");
            } else if (path.endsWith("/findings")) {
                reply(ex, 200, findingsJson);
            } else if (path.endsWith("/diff")) {
                reply(ex, 200, diffJson);
            } else {
                reply(ex, 200, auditJson);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private int run(String... extra) {
        List<String> args = new ArrayList<>(List.of("--system-id", "1", "--api-url", "http://127.0.0.1:" + server.getAddress().getPort(),
                "--api-key", "segredo"));
        args.addAll(List.of(extra));
        AuditCli.CliArgs parsed = AuditCli.parse(args.toArray(new String[0]));
        AuditCli cli = new AuditCli();
        cli.pollSeconds = 0;
        return cli.run(parsed);
    }

    @Test
    void sucessoSemLimitesRetornaZeroEEnviaAChaveNosDoisHeaders() {
        assertEquals(0, run());
        assertTrue(authHeaders.stream().allMatch(h -> h.equals("segredo|Bearer segredo")), authHeaders.toString());
    }

    @Test
    void scoreAbaixoDoLimiteRetornaUm() {
        assertEquals(1, run("--fail-below", "90"));
        assertEquals(0, run("--fail-below", "70"));
    }

    @Test
    void failOnFalhaSeHouverFindingNaSeveridadeOuAcima() {
        assertEquals(1, run("--fail-on", "HIGH"));
        assertEquals(1, run("--fail-on", "MEDIUM"));
        findingsJson = "[{\"severity\":\"LOW\",\"title\":\"x\"}]";
        assertEquals(0, run("--fail-on", "HIGH"));
        assertEquals(1, run("--fail-on", "LOW"));
    }

    @Test
    void failOnNewSoConsideraOsNovosEIgnoraPrimeiraAuditoria() {
        assertEquals(0, run("--fail-on-new", "HIGH"));          // há um HIGH, mas ele já existia

        diffJson = "{\"hasBaseline\":true,\"baseAuditId\":4,\"newFindings\":[{\"severity\":\"CRITICAL\"}],\"persistingCount\":1}";
        assertEquals(1, run("--fail-on-new", "HIGH"));

        diffJson = "{\"hasBaseline\":true,\"baseAuditId\":4,\"newFindings\":[{\"severity\":\"LOW\"}],\"persistingCount\":1}";
        assertEquals(0, run("--fail-on-new", "HIGH"));

        diffJson = "{\"hasBaseline\":false,\"newFindings\":[],\"persistingCount\":0}";
        assertEquals(0, run("--fail-on-new", "LOW"));            // sem base: não dá para dizer o que é novo
    }

    @Test
    void gravaSarifJunitEJson() throws Exception {
        Path sarif = tmp.resolve("out/r.sarif");
        Path junit = tmp.resolve("out/r.xml");
        Path json = tmp.resolve("r.json");
        assertEquals(0, run("--sarif", sarif.toString(), "--junit", junit.toString(), "--json", json.toString()));

        assertEquals("2.1.0", new ObjectMapper().readTree(Files.readString(sarif)).path("version").asText());
        assertTrue(Files.readString(junit).contains("<testcase"));
        assertEquals(1, new ObjectMapper().readTree(Files.readString(json)).size());
    }

    @Test
    void auditoriaFailedRetornaDois() {
        auditJson = "{\"id\":5,\"status\":\"FAILED\",\"errorMessage\":\"IllegalStateException: git clone falhou\"}";
        assertEquals(2, run());
    }

    @Test
    void erroNaCriacaoRetornaDois() {
        createStatus = 401;
        assertEquals(2, run());
    }

    @Test
    void argumentosInvalidosSaoRejeitados() {
        assertThrows(IllegalArgumentException.class, () -> AuditCli.parse(new String[] {}));                                  // sem --system-id
        assertThrows(IllegalArgumentException.class, () -> AuditCli.parse(new String[] {"--system-id", "abc"}));
        assertThrows(IllegalArgumentException.class, () -> AuditCli.parse(new String[] {"--system-id", "1", "--fail-on", "INFO"}));
        assertThrows(IllegalArgumentException.class, () -> AuditCli.parse(new String[] {"--system-id", "1", "--fail-below", "x"}));
        assertThrows(IllegalArgumentException.class, () -> AuditCli.parse(new String[] {"--system-id", "1", "--desconhecido", "x"}));
        assertThrows(IllegalArgumentException.class, () -> AuditCli.parse(new String[] {"--system-id"}));                      // valor faltando
        assertEquals("http://x:8080", AuditCli.parse(new String[] {"--system-id", "1", "--api-url", "http://x:8080/"}).apiUrl);
    }
}
