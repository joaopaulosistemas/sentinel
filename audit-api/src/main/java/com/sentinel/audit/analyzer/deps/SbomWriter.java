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
        addMetadata(root.putObject("metadata"), systemName, version, now);
        ArrayNode components = root.putArray("components");
        componentsByPurl(deps).values().forEach(components::add);
        return root.toPrettyString();
    }

    private static void addMetadata(ObjectNode metadata, String systemName, String version, Instant now) {
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
    }

    /** Um componente por pacote@versão; guarda todos os arquivos onde apareceu. */
    private static Map<String, ObjectNode> componentsByPurl(List<Dependency> deps) {
        Map<String, ObjectNode> byPurl = new LinkedHashMap<>();
        if (deps == null) {
            return byPurl;
        }
        for (Dependency d : deps) {
            String purl = purl(d);
            if (purl == null) {
                continue;
            }
            ObjectNode c = byPurl.computeIfAbsent(purl, k -> newComponent(d, k));
            addSourceFile((ArrayNode) c.get("properties"), d.filePath());
        }
        return byPurl;
    }

    private static ObjectNode newComponent(Dependency d, String purl) {
        ObjectNode c = MAPPER.createObjectNode();
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
        return c;
    }

    private static void addSourceFile(ArrayNode props, String filePath) {
        if (filePath != null && !contains(props, filePath)) {
            ObjectNode p = props.addObject();
            p.put("name", "sentinel:source-file");
            p.put("value", filePath);
        }
    }

    /** Package URL (purl) do pacote; {@code null} se o ecossistema/nome/versão não permitem montar um. */
    public static String purl(Dependency d) {
        if (!hasCoordinates(d)) {
            return null;
        }
        String version = enc(d.version());
        return switch (d.ecosystem() == null ? "" : d.ecosystem()) {
            case "Maven" -> mavenPurl(d.name(), version);
            case "npm" -> npmPurl(d.name(), version);
            case "PyPI" -> "pkg:pypi/" + enc(d.name().toLowerCase(Locale.ROOT).replace('_', '-')) + "@" + version;
            case "NuGet" -> "pkg:nuget/" + enc(d.name()) + "@" + version;
            default -> null;
        };
    }

    private static boolean hasCoordinates(Dependency d) {
        return d != null && isFilled(d.name()) && isFilled(d.version());
    }

    private static boolean isFilled(String s) {
        return s != null && !s.isBlank();
    }

    private static String mavenPurl(String name, String version) {
        int i = name.indexOf(':');
        if (i <= 0 || i == name.length() - 1) {
            return null;
        }
        return "pkg:maven/" + enc(name.substring(0, i)) + "/" + enc(name.substring(i + 1)) + "@" + version;
    }

    private static String npmPurl(String n, String version) {
        String name = n.startsWith("@") ? "%40" + encPath(n.substring(1)) : encPath(n);
        return "pkg:npm/" + name + "@" + version;
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
