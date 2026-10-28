package com.sentinel.audit.analyzer.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinel.audit.model.Severity;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente da API pública do <a href="https://osv.dev">OSV.dev</a> (base de vulnerabilidades de
 * Maven, npm, PyPI, NuGet etc.).
 * <p>
 * Só nome do pacote e versão são enviados (nunca código-fonte). Em redes fechadas, desligue com
 * {@code OSV_ENABLED=false} ou aponte {@code OSV_URL} para um espelho interno; se a consulta falhar, o
 * analisador volta para a base interna reduzida.
 */
@ApplicationScoped
public class OsvClient {

    private static final Logger LOG = Logger.getLogger(OsvClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BATCH_SIZE = 500;
    private static final int MAX_DEPENDENCIES = 5000;
    private static final int MAX_VULN_DETAILS = 1000;

    @ConfigProperty(name = "sentinel.audit.osv.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "sentinel.audit.osv.url", defaultValue = "https://api.osv.dev")
    String baseUrl;

    @ConfigProperty(name = "sentinel.audit.osv.timeout-seconds", defaultValue = "20")
    long timeoutSeconds;

    private volatile HttpClient http;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Consulta as vulnerabilidades de cada dependência.
     *
     * @throws IOException em falha de rede/HTTP/JSON (o chamador decide o fallback)
     */
    public Map<Dependency, List<OsvVulnerability>> lookup(Collection<Dependency> deps)
            throws IOException, InterruptedException {
        List<Dependency> list = new ArrayList<>(new LinkedHashSet<>(deps));
        if (list.size() > MAX_DEPENDENCIES) {
            LOG.warnf("%d dependências; consultando apenas as primeiras %d", list.size(), MAX_DEPENDENCIES);
            list = list.subList(0, MAX_DEPENDENCIES);
        }
        // 1) ids por dependência (querybatch)
        Map<Dependency, List<String>> idsByDep = new LinkedHashMap<>();
        for (int from = 0; from < list.size(); from += BATCH_SIZE) {
            List<Dependency> chunk = list.subList(from, Math.min(from + BATCH_SIZE, list.size()));
            ObjectNode body = MAPPER.createObjectNode();
            ArrayNode queries = body.putArray("queries");
            for (Dependency d : chunk) {
                ObjectNode q = queries.addObject();
                ObjectNode pkg = q.putObject("package");
                pkg.put("name", d.name());
                pkg.put("ecosystem", d.ecosystem());
                q.put("version", d.version());
            }
            JsonNode response = MAPPER.readTree(post("/v1/querybatch", MAPPER.writeValueAsString(body)));
            JsonNode results = response.path("results");
            if (!results.isArray() || results.size() != chunk.size()) {
                throw new IOException("Resposta inesperada do OSV (querybatch)");
            }
            for (int i = 0; i < chunk.size(); i++) {
                List<String> ids = new ArrayList<>();
                for (JsonNode v : results.get(i).path("vulns")) {
                    ids.add(v.path("id").asText());
                }
                if (!ids.isEmpty()) {
                    idsByDep.put(chunk.get(i), ids);
                }
            }
        }
        // 2) detalhes de cada vulnerabilidade distinta
        Set<String> distinct = new LinkedHashSet<>();
        idsByDep.values().forEach(distinct::addAll);
        if (distinct.size() > MAX_VULN_DETAILS) {
            LOG.warnf("%d vulnerabilidades distintas; detalhando as primeiras %d", distinct.size(), MAX_VULN_DETAILS);
        }
        Map<String, JsonNode> details = new ConcurrentHashMap<>();
        int n = 0;
        for (String id : distinct) {
            if (n >= MAX_VULN_DETAILS) {
                break;
            }
            n++;
            details.put(id, MAPPER.readTree(get("/v1/vulns/" + java.net.URLEncoder.encode(id,
                    java.nio.charset.StandardCharsets.UTF_8))));
        }
        // 3) resultado normalizado
        Map<Dependency, List<OsvVulnerability>> out = new LinkedHashMap<>();
        for (Map.Entry<Dependency, List<String>> e : idsByDep.entrySet()) {
            List<OsvVulnerability> vulns = new ArrayList<>();
            for (String id : e.getValue()) {
                JsonNode d = details.get(id);
                vulns.add(d == null ? new OsvVulnerability(id, id.startsWith("CVE-") ? id : null, id, Severity.MEDIUM, null)
                        : parse(d, e.getKey()));
            }
            out.put(e.getKey(), vulns);
        }
        return out;
    }

    // ------------------------------------------------------------------ parsing

    static OsvVulnerability parse(JsonNode v, Dependency dep) {
        String id = v.path("id").asText();
        String cve = id.startsWith("CVE-") ? id : null;
        for (JsonNode alias : v.path("aliases")) {
            if (cve == null && alias.asText().startsWith("CVE-")) {
                cve = alias.asText();
            }
        }
        String summary = v.path("summary").asText("");
        if (summary.isBlank()) {
            String details = v.path("details").asText("").strip();
            summary = details.length() > 200 ? details.substring(0, 197) + "..." : details;
        }
        return new OsvVulnerability(id, cve, summary.isBlank() ? id : summary, severityOf(v), fixedIn(v, dep));
    }

    static Severity severityOf(JsonNode v) {
        String label = v.path("database_specific").path("severity").asText("").toUpperCase(Locale.ROOT);
        switch (label) {
            case "CRITICAL": return Severity.CRITICAL;
            case "HIGH": return Severity.HIGH;
            case "MODERATE", "MEDIUM": return Severity.MEDIUM;
            case "LOW": return Severity.LOW;
            default: break;
        }
        double best = -1;
        for (JsonNode s : v.path("severity")) {
            best = Math.max(best, Cvss.baseScore(s.path("score").asText("")));
        }
        if (best < 0) {
            return Severity.MEDIUM;
        }
        return best >= 9.0 ? Severity.CRITICAL : best >= 7.0 ? Severity.HIGH : best >= 4.0 ? Severity.MEDIUM : Severity.LOW;
    }

    static String fixedIn(JsonNode v, Dependency dep) {
        for (JsonNode affected : v.path("affected")) {
            JsonNode pkg = affected.path("package");
            if (!dep.name().equalsIgnoreCase(pkg.path("name").asText())) {
                continue;
            }
            for (JsonNode range : affected.path("ranges")) {
                for (JsonNode event : range.path("events")) {
                    if (event.has("fixed")) {
                        return event.path("fixed").asText();
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ HTTP (sobrescrevível em testes)

    protected String post(String path, String json) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(base() + path))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    protected String get(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(base() + path))
                .timeout(Duration.ofSeconds(timeoutSeconds)).GET().build());
    }

    private String base() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        if (http == null) {
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
        }
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("OSV respondeu HTTP " + response.statusCode());
        }
        return response.body();
    }
}
