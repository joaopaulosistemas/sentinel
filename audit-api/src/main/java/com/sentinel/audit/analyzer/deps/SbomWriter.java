package com.sentinel.audit.analyzer.deps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Gera um SBOM (lista de materiais de software) no formato CycloneDX 1.5 em JSON
 * a partir das dependências extraídas dos manifestos/lockfiles do projeto.
 */
public final class SbomWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SbomWriter() {}

    /**
     * @param systemName nome do sistema auditado (componente principal do SBOM)
     * @param version    versão/branch do componente principal (pode ser nulo)
     * @param deps       dependências extraídas; duplicadas (mesmo pacote e versão) viram um componente só
     * @param now        data de geração
     * @param serial     identificador único do documento
     */
    public static String toCycloneDx(String systemName, String version, List<Dependency> deps, Instant now, UUID serial) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("bomFormat", "CycloneDX");
        root.put("specVersion", "1.5");
        root.put("serialNumber", "urn:uuid:" + serial);
        root.put("version", 1);

        ObjectNode metadata = root.putObject("metadata");
        metadata.put("timestamp", now.toString());
        ObjectNode tool = metadata.putArray("tools").addObject();
        tool.put("vendor", "Sentinel");
        tool.put("name", "sentinel-audit-tool");
        ObjectNode main = metadata.putObject("component");
        main.put("type", "application");
        main.put("name", systemName == null ? "desconhecido" : systemName);
        if (version != null && !version.isBlank()) {
            main.put("version", version);
        }

        // Um componente por pacote@versão; guarda todos os arquivos onde apareceu.
        Map<String, ObjectNode> byPurl = new LinkedHashMap<>();
        if (deps != null) {
            for (Dependency d : deps) {
                String purl = purl(d);
                if (purl == null) {
                    continue;
                }
                ObjectNode c = byPurl.get(purl);
                if (c == null) {
                    c = MAPPER.createObjectNode();
                    c.put("type", "library");
                    c.put("bom-ref", purl);
                    if ("Maven".equals(d.ecosystem()) && d.name().contains(":")) {
                        c.put("group", d.name().substring(0, d.name().indexOf(':')));
                        c.put("name", d.shortName());
                    } else {
                        c.put("name", d.name());
                    }
                    c.put("version", d.version());
                    c.put("purl", purl);
                    c.putArray("properties");
                    byPurl.put(purl, c);
                }
                ArrayNode props = (ArrayNode) c.get("properties");
                if (d.filePath() != null && !contains(props, d.filePath())) {
                    ObjectNode p = props.addObject();
                    p.put("name", "sentinel:source-file");
                    p.put("value", d.filePath());
                }
            }
        }
        ArrayNode components = root.putArray("components");
        byPurl.values().forEach(components::add);
        return root.toPrettyString();
    }

    /** Package URL (purl) do pacote; {@code null} se o ecossistema/nome/versão não permitem montar um. */
    public static String purl(Dependency d) {
        if (d == null || d.name() == null || d.name().isBlank() || d.version() == null || d.version().isBlank()) {
            return null;
        }
        String version = enc(d.version());
        switch (d.ecosystem() == null ? "" : d.ecosystem()) {
            case "Maven": {
                int i = d.name().indexOf(':');
                if (i <= 0 || i == d.name().length() - 1) {
                    return null;
                }
                return "pkg:maven/" + enc(d.name().substring(0, i)) + "/" + enc(d.name().substring(i + 1)) + "@" + version;
            }
            case "npm": {
                String n = d.name();
                String name = n.startsWith("@") ? "%40" + encPath(n.substring(1)) : encPath(n);
                return "pkg:npm/" + name + "@" + version;
            }
            case "PyPI":
                return "pkg:pypi/" + enc(d.name().toLowerCase(Locale.ROOT).replace('_', '-')) + "@" + version;
            case "NuGet":
                return "pkg:nuget/" + enc(d.name()) + "@" + version;
            default:
                return null;
        }
    }

    private static boolean contains(ArrayNode props, String file) {
        for (var p : props) {
            if (file.equals(p.path("value").asText())) {
                return true;
            }
        }
        return false;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Codifica cada segmento mas preserva a barra (escopo/nome do npm). */
    private static String encPath(String s) {
        String[] parts = s.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(enc(parts[i]));
        }
        return sb.toString();
    }
}
