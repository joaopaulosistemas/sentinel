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

    private static final String ELLIPSIS = "...";
    private static final int SUMMARY_MAX_CHARS = 200;
    /** Faixas de severidade da nota CVSS (FIRST): crítico a partir de 9,0, alto de 7,0 e médio de 4,0. */
    private static final double CVSS_CRITICAL_FROM = 9.0;
    private static final double CVSS_HIGH_FROM = 7.0;
    private static final double CVSS_MEDIUM_FROM = 4.0;


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
        Map<Dependency, List<String>> idsByDep = queryIds(list);
        return normalize(idsByDep, fetchDetails(idsByDep));
    }

    /** 1) ids de vulnerabilidade por dependência (querybatch, em lotes). */
    private Map<Dependency, List<String>> queryIds(List<Dependency> list) throws IOException, InterruptedException {
        Map<Dependency, List<String>> idsByDep = new LinkedHashMap<>();
        for (int from = 0; from < list.size(); from += BATCH_SIZE) {
            List<Dependency> chunk = list.subList(from, Math.min(from + BATCH_SIZE, list.size()));
            JsonNode response = MAPPER.readTree(post("/v1/querybatch", MAPPER.writeValueAsString(batchBody(chunk))));
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
        return idsByDep;
    }

    private static ObjectNode batchBody(List<Dependency> chunk) {
        ObjectNode body = MAPPER.createObjectNode();
        ArrayNode queries = body.putArray("queries");
        for (Dependency d : chunk) {
            ObjectNode q = queries.addObject();
            ObjectNode pkg = q.putObject("package");
            pkg.put("name", d.name());
            pkg.put("ecosystem", d.ecosystem());
            q.put("version", d.version());
        }
        return body;
    }

    /** 2) detalhes de cada vulnerabilidade distinta (até o limite). */
    private Map<String, JsonNode> fetchDetails(Map<Dependency, List<String>> idsByDep) throws IOException, InterruptedException {
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
        return details;
    }

    /** 3) resultado normalizado; sem detalhe (acima do limite), vale o que o id já diz. */
    private static Map<Dependency, List<OsvVulnerability>> normalize(Map<Dependency, List<String>> idsByDep, Map<String, JsonNode> details) {
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
            summary = details.length() > SUMMARY_MAX_CHARS ? details.substring(0, SUMMARY_MAX_CHARS - ELLIPSIS.length()) + ELLIPSIS : details;
        }
        return new OsvVulnerability(id, cve, summary.isBlank() ? id : summary, severityOf(v), fixedIn(v, dep));
    }

    static Severity severityOf(JsonNode v) {
        String label = v.path("database_specific").path("severity").asText("").toUpperCase(Locale.ROOT);
        Severity byLabel = switch (label) {
            case "CRITICAL" -> Severity.CRITICAL;
            case "HIGH" -> Severity.HIGH;
            case "MODERATE", "MEDIUM" -> Severity.MEDIUM;
            case "LOW" -> Severity.LOW;
            default -> null;
        };
        return byLabel != null ? byLabel : severityByScore(v);
    }

    /** Sem rótulo conhecido, vale a maior nota CVSS; sem nota nenhuma, MEDIUM. */
    private static Severity severityByScore(JsonNode v) {
        double best = -1;
        for (JsonNode s : v.path("severity")) {
            best = Math.max(best, Cvss.baseScore(s.path("score").asText("")));
        }
        if (best < 0) {
            return Severity.MEDIUM;
        }
        return best >= CVSS_CRITICAL_FROM ? Severity.CRITICAL : best >= CVSS_HIGH_FROM ? Severity.HIGH : best >= CVSS_MEDIUM_FROM ? Severity.MEDIUM : Severity.LOW;
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
