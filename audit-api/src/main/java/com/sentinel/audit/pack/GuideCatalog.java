package com.sentinel.audit.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

/**
 * Regras dos pacotes no formato do guia de código: título e ação em português, severidade, CWE, linguagens e o
 * antes/depois da orientação de correção. Só leitura; arquivo ilegível é ignorado (o carregamento do pacote já o registrou).
 */
public final class GuideCatalog {

    /** Uma regra do guia. {@code languages} junta a linguagem do Semgrep e as extensões de {@code paths.include}. */
    public record GuideRule(String id, String pack, String title, String action, String severity, String cwe,
                            List<String> languages, String before, String after) {}

    private static final Logger LOG = Logger.getLogger(GuideCatalog.class);
    private static final String REGEX_LANGUAGE = "regex";
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern CWE_ID = Pattern.compile("CWE-\\d+");

    private GuideCatalog() {}

    public static List<GuideRule> of(List<RulePack> packs) {
        List<GuideRule> out = new ArrayList<>();
        for (RulePack pack : packs) {
            Path dir = pack.rulesDir();
            if (dir == null) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                List<Path> yamls = files
                        .filter(p -> p.getFileName().toString().endsWith(".yml") || p.getFileName().toString().endsWith(".yaml"))
                        .sorted()
                        .toList();
                for (Path file : yamls) {
                    read(pack, file, out);
                }
            } catch (IOException e) {
                LOG.debugf("Pasta de regras ilegível, o pacote %s fica fora do guia (%s)", pack.id(), e.getMessage());
            }
        }
        return out;
    }

    private static void read(RulePack pack, Path file, List<GuideRule> out) {
        try {
            Map<String, Object> root = Yamls.map(Yamls.parse(Files.readString(file)));
            List<Object> rules = root == null ? null : Yamls.list(root.get("rules"));
            if (rules == null) {
                return;
            }
            for (Object item : rules) {
                Map<String, Object> rule = Yamls.map(item);
                String id = rule == null ? null : Yamls.str(rule, "id");
                if (id != null) {
                    out.add(toRule(pack, rule, id));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.debugf("Arquivo de regras fora do guia: %s (%s)", file.getFileName(), e.getMessage());
        }
    }

    private static GuideRule toRule(RulePack pack, Map<String, Object> rule, String id) {
        String message = clean(Yamls.str(rule, "message"));
        RemediationEntry rem = remediationFor(pack, id);
        Map<String, Object> metadata = Yamls.map(rule.get("metadata"));
        String cwe = metadata == null ? null : Yamls.str(metadata, "cwe");
        var matcher = cwe == null ? null : CWE_ID.matcher(cwe);
        String action = rem != null && rem.summary() != null ? stripDot(rem.summary()) : secondSentence(message);
        return new GuideRule(id, pack.id(), title(message.isEmpty() ? id : message), action, severity(Yamls.str(rule, "severity")),
                matcher != null && matcher.find() ? matcher.group() : "", languages(rule),
                rem == null || rem.before() == null ? "" : rem.before(), rem == null || rem.after() == null ? "" : rem.after().strip());
    }

    private static RemediationEntry remediationFor(RulePack pack, String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        RemediationEntry best = null;
        for (RemediationEntry e : pack.remediation()) {
            if (lower.contains(e.key()) && (best == null || e.key().length() > best.key().length())) {
                best = e;
            }
        }
        return best;
    }

    /** Linguagens do Semgrep (sem {@code regex}) mais as que as extensões de {@code paths.include} indicam. */
    static List<String> languages(Map<String, Object> rule) {
        Set<String> out = new LinkedHashSet<>();
        List<Object> declared = Yamls.list(rule.get("languages"));
        if (declared != null) {
            for (Object l : declared) {
                String name = String.valueOf(l).toLowerCase(Locale.ROOT);
                if (!REGEX_LANGUAGE.equals(name) && !"generic".equals(name)) {
                    out.add(name);
                }
            }
        }
        Map<String, Object> paths = Yamls.map(rule.get("paths"));
        List<Object> include = paths == null ? null : Yamls.list(paths.get("include"));
        if (include != null) {
            for (Object glob : include) {
                String language = languageOfGlob(String.valueOf(glob));
                if (language != null) {
                    out.add(language);
                }
            }
        }
        return List.copyOf(out);
    }

    private static String languageOfGlob(String glob) {
        String g = glob.toLowerCase(Locale.ROOT);
        if (g.endsWith(".ts") || g.endsWith(".tsx")) {
            return "typescript";
        }
        if (g.endsWith(".js") || g.endsWith(".jsx")) {
            return "javascript";
        }
        if (g.endsWith(".java")) {
            return "java";
        }
        if (g.endsWith(".vue")) {
            return "vue";
        }
        if (g.endsWith(".properties")) {
            return "properties";
        }
        return g.contains("tsconfig") ? "typescript" : null;
    }

    private static String severity(String semgrep) {
        if (semgrep == null) {
            return "media";
        }
        return switch (semgrep.toUpperCase(Locale.ROOT)) {
            case "ERROR" -> "alta";
            case "INFO" -> "baixa";
            default -> "media";
        };
    }

    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private static String stripDot(String text) {
        String t = text.strip();
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }

    private static String firstSentence(String message) {
        String[] parts = SENTENCE_END.split(message);
        return parts.length == 0 ? message : parts[0];
    }

    /** Título: o trecho antes do primeiro ': ' da primeira frase, ou a primeira frase inteira. */
    static String title(String message) {
        String first = firstSentence(message);
        int colon = first.indexOf(": ");
        return stripDot(colon > 0 ? first.substring(0, colon) : first);
    }

    private static String secondSentence(String message) {
        String[] parts = SENTENCE_END.split(message);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].startsWith("(")) {
                return stripDot(parts[i]);
            }
        }
        return "";
    }

    /** Lê um recurso do classpath como texto; {@code null} se não existir. */
    public static String resource(String name) {
        try (InputStream in = GuideCatalog.class.getResourceAsStream(name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warnf("Recurso do guia ilegível: %s (%s)", name, e.getMessage());
            return null;
        }
    }
}
