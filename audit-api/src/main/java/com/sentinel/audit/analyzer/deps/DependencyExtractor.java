package com.sentinel.audit.analyzer.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lê os manifestos e lockfiles de um projeto e devolve as dependências com versão conhecida.
 * <p>
 * Suporta: {@code pom.xml}, {@code package-lock.json} (v1/v2/v3), {@code package.json} (só quando não há
 * lockfile ao lado), {@code build.gradle[.kts]}, {@code requirements*.txt} e {@code *.csproj}.
 * Versões que dependem de BOM/parent/variável não resolvida são ignoradas (não há como saber a versão real).
 */
public final class DependencyExtractor {

    private static final Logger LOG = Logger.getLogger(DependencyExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> IGNORED_DIRS =
            Set.of("node_modules", ".git", ".sentinel", "target", "build", "dist", ".gradle", "venv", ".venv", "__pycache__");
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern GRADLE_COORD =
            Pattern.compile("[\"']([A-Za-z0-9_.\\-]+):([A-Za-z0-9_.\\-]+):([A-Za-z0-9_.\\-]+)[\"']");
    private static final Pattern PIP_PIN =
            Pattern.compile("^\\s*([A-Za-z0-9][A-Za-z0-9_.\\-]*)(?:\\[[^]]*])?\\s*==\\s*([A-Za-z0-9_.\\-+!]+)");
    private static final Pattern CSPROJ_REF = Pattern.compile(
            "<PackageReference\\s+[^>]*?Include\\s*=\\s*\"([^\"]+)\"[^>]*?Version\\s*=\\s*\"([0-9][^\"]*)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NPM_RANGE = Pattern.compile("^[\\^~>=<\\s]*v?(\\d+(?:\\.\\d+){0,3}(?:-[\\w.]+)?)");

    private DependencyExtractor() {}

    public static List<Dependency> extract(Path root) {
        Set<Dependency> result = new LinkedHashSet<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> !Files.isSymbolicLink(p))
                  .filter(p -> !inIgnoredDir(root, p))
                  .forEach(files::add);
        } catch (IOException e) {
            LOG.warnf("Erro ao procurar manifestos de dependências: %s", e.getMessage());
            return List.of();
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);
            try {
                if (Files.size(file) > MAX_FILE_BYTES) {
                    continue;
                }
                String rel = root.relativize(file).toString();
                if (name.equals("pom.xml")) {
                    result.addAll(fromPom(file, rel));
                } else if (name.equals("package-lock.json")) {
                    result.addAll(fromPackageLock(file, rel));
                } else if (name.equals("package.json")) {
                    if (!Files.exists(file.resolveSibling("package-lock.json"))) {
                        result.addAll(fromPackageJson(file, rel));
                    }
                } else if (lower.equals("build.gradle") || lower.equals("build.gradle.kts")) {
                    result.addAll(fromGradle(file, rel));
                } else if (lower.startsWith("requirements") && lower.endsWith(".txt")) {
                    result.addAll(fromRequirements(file, rel));
                } else if (lower.endsWith(".csproj")) {
                    result.addAll(fromCsproj(file, rel));
                }
            } catch (Exception e) {
                LOG.warnf("Não foi possível ler %s: %s", name, e.getMessage());
            }
        }
        return new ArrayList<>(result);
    }

    private static boolean inIgnoredDir(Path root, Path file) {
        Path rel = root.relativize(file);
        for (int i = 0; i < rel.getNameCount() - 1; i++) {
            if (IGNORED_DIRS.contains(rel.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Maven

    static List<Dependency> fromPom(Path pom, String rel) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Document doc;
        try (var in = Files.newInputStream(pom)) {
            doc = factory.newDocumentBuilder().parse(in);
        }
        Map<String, String> props = new HashMap<>();
        NodeList propNodes = doc.getElementsByTagName("properties");
        for (int i = 0; i < propNodes.getLength(); i++) {
            NodeList kids = propNodes.item(i).getChildNodes();
            for (int j = 0; j < kids.getLength(); j++) {
                if (kids.item(j).getNodeType() == Node.ELEMENT_NODE) {
                    props.put(kids.item(j).getNodeName(), kids.item(j).getTextContent().trim());
                }
            }
        }
        List<Dependency> out = new ArrayList<>();
        NodeList deps = doc.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            Element dep = (Element) deps.item(i);
            String g = resolve(childText(dep, "groupId"), props);
            String a = childText(dep, "artifactId");
            String v = resolve(childText(dep, "version"), props);
            if (a == null || v == null || g == null) {
                continue;
            }
            out.add(new Dependency("Maven", g + ":" + a, v, rel));
        }
        return out;
    }

    private static String childText(Element parent, String tag) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(n.getNodeName())) {
                String t = n.getTextContent().trim();
                return t.isEmpty() ? null : t;
            }
        }
        return null;
    }

    private static String resolve(String value, Map<String, String> props) {
        if (value == null) {
            return null;
        }
        String cur = value;
        for (int d = 0; d < 3 && cur.contains("${"); d++) {
            Matcher m = PROPERTY_REF.matcher(cur);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String r = props.get(m.group(1));
                m.appendReplacement(sb, Matcher.quoteReplacement(r != null ? r : m.group()));
            }
            m.appendTail(sb);
            cur = sb.toString();
        }
        return cur.contains("${") ? null : cur;
    }

    // ------------------------------------------------------------------ npm

    static List<Dependency> fromPackageLock(Path file, String rel) throws IOException {
        JsonNode json = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        List<Dependency> out = new ArrayList<>();
        JsonNode packages = json.path("packages");
        if (packages.isObject()) { // lockfileVersion 2 e 3
            for (Iterator<Map.Entry<String, JsonNode>> it = packages.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                int idx = key.lastIndexOf("node_modules/");
                if (idx < 0 || e.getValue().path("link").asBoolean(false)) {
                    continue; // "" = o próprio projeto
                }
                String name = key.substring(idx + "node_modules/".length());
                String version = e.getValue().path("version").asText("");
                if (!version.isBlank()) {
                    out.add(new Dependency("npm", name, version, rel));
                }
            }
        } else { // lockfileVersion 1
            collectLockV1(json.path("dependencies"), rel, out);
        }
        return out;
    }

    private static void collectLockV1(JsonNode deps, String rel, List<Dependency> out) {
        for (Iterator<Map.Entry<String, JsonNode>> it = deps.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String version = e.getValue().path("version").asText("");
            if (!version.isBlank() && !version.contains(":") && !version.contains("/")) {
                out.add(new Dependency("npm", e.getKey(), version, rel));
            }
            collectLockV1(e.getValue().path("dependencies"), rel, out);
        }
    }

    static List<Dependency> fromPackageJson(Path file, String rel) throws IOException {
        JsonNode json = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        List<Dependency> out = new ArrayList<>();
        for (String section : List.of("dependencies", "devDependencies")) {
            for (Iterator<Map.Entry<String, JsonNode>> it = json.path(section).fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                Matcher m = NPM_RANGE.matcher(e.getValue().asText("").trim());
                if (m.find()) {
                    out.add(new Dependency("npm", e.getKey(), m.group(1), rel));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Gradle / pip / NuGet

    static List<Dependency> fromGradle(Path file, String rel) throws IOException {
        List<Dependency> out = new ArrayList<>();
        Matcher m = GRADLE_COORD.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (m.find()) {
            out.add(new Dependency("Maven", m.group(1) + ":" + m.group(2), m.group(3), rel));
        }
        return out;
    }

    static List<Dependency> fromRequirements(Path file, String rel) throws IOException {
        List<Dependency> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            Matcher m = PIP_PIN.matcher(line);
            if (m.find()) {
                out.add(new Dependency("PyPI", m.group(1).toLowerCase(Locale.ROOT).replace('_', '-'), m.group(2), rel));
            }
        }
        return out;
    }

    static List<Dependency> fromCsproj(Path file, String rel) throws IOException {
        List<Dependency> out = new ArrayList<>();
        Matcher m = CSPROJ_REF.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (m.find()) {
            out.add(new Dependency("NuGet", m.group(1), m.group(2), rel));
        }
        return out;
    }
}
