package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.quality.SourceSanitizer;
import com.sentinel.audit.analyzer.structure.SourceFiles.Src;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exportações de TypeScript/JavaScript que nenhum outro arquivo do projeto menciona (código morto). A comparação é
 * por nome, de propósito: se o nome aparece em qualquer outro arquivo, inclusive de teste, o item conta como usado.
 * Isso erra para o lado de não apontar. Classes de framework (Angular), exportações padrão e arquivos de entrada
 * ou configuração ficam de fora.
 */
public final class UnusedExports {

    /** Quantas linhas acima da declaração são olhadas atrás de decorador de framework. */
    private static final int DECORATOR_LOOKBACK_LINES = 60;


    private UnusedExports() {}

    /** @param usedInside {@code true} = só o próprio arquivo usa (basta tirar o {@code export}); {@code false} = ninguém usa (código morto) */
    public record Unused(String file, int line, String name, String kind, boolean usedInside) {}

    private static final Pattern DECL = Pattern.compile(
            "(?m)^export\\s+(?:declare\\s+)?(?:abstract\\s+)?(const|let|var|function\\*?|async\\s+function|class|interface|type|enum)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final Pattern FRAMEWORK = Pattern.compile("@(Component|Injectable|NgModule|Directive|Pipe|Entity|Controller)\\b");

    private static final Set<String> CONVENTION_NAMES = Set.of("getServerSideProps", "getStaticProps", "getStaticPaths",
            "generateMetadata", "generateStaticParams", "metadata", "revalidate", "dynamic", "loader", "action", "meta",
            "links", "handle", "config", "default");

    /**
     * @param production arquivos de produção (de onde saem as exportações)
     * @param everything todos os arquivos, testes inclusive (onde se procura o uso)
     */
    public static List<Unused> find(List<Src> production, List<Src> everything, int max) {
        Map<String, Set<String>> filesUsing = new HashMap<>();
        for (Src f : everything) {
            Matcher m = IDENT.matcher(SourceSanitizer.strip(f.text()));
            Set<String> seen = new HashSet<>();
            while (m.find()) {
                if (seen.add(m.group())) {
                    filesUsing.computeIfAbsent(m.group(), k -> new HashSet<>()).add(f.rel());
                }
            }
        }
        List<Unused> out = new ArrayList<>();
        for (Src f : production) {
            if (skipFile(f.rel())) {
                continue;
            }
            String text = f.text();
            String stripped = SourceSanitizer.strip(text);
            Matcher m = DECL.matcher(text);
            while (m.find() && out.size() < max) {
                String kind = m.group(1).replaceAll("\\s+", " ");
                String name = m.group(2);
                if (CONVENTION_NAMES.contains(name) || (kind.equals("class") && hasFrameworkDecorator(text, m.start()))) {
                    continue;
                }
                Set<String> users = filesUsing.getOrDefault(name, Set.of());
                if (users.stream().allMatch(f.rel()::equals)) {
                    boolean inside = Pattern.compile("(?<![\\w$])" + Pattern.quote(name) + "(?![\\w$])").matcher(stripped).results().count() > 1;
                    out.add(new Unused(f.rel(), lineOf(text, m.start()), name, kind, inside));
                }
            }
        }
        return out;
    }

    static boolean skipFile(String rel) {
        String name = rel.substring(rel.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return name.startsWith("index.") || name.startsWith("main.") || name.startsWith("setup") || name.contains(".config.")
                || name.contains(".stories.") || name.startsWith("vite-env") || name.endsWith(".module.ts")
                || name.startsWith("polyfills") || name.startsWith("environment");
    }

    /** Decorador de framework colado à declaração: vale o trecho depois do último comando de nível de arquivo que terminou. */
    static boolean hasFrameworkDecorator(String text, int declStart) {
        String[] above = text.substring(0, declStart).split("\n", -1);
        StringBuilder region = new StringBuilder();
        for (int i = above.length - 1, seen = 0; i >= 0 && seen < DECORATOR_LOOKBACK_LINES; i--, seen++) {
            String line = above[i];
            boolean topLevelEnd = !line.isEmpty() && !Character.isWhitespace(line.charAt(0))
                    && (line.stripTrailing().endsWith("}") || line.stripTrailing().endsWith("};") || line.stripTrailing().endsWith(";"));
            if (topLevelEnd) {
                break;
            }
            region.insert(0, line + "\n");
        }
        return FRAMEWORK.matcher(region).find();
    }

    static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Pacote publicado como biblioteca: o que ele exporta é a API pública, não código morto. */
    public static boolean looksLikeLibrary(String packageJson) {
        if (packageJson == null) {
            return false;
        }
        boolean isPrivate = Pattern.compile("\"private\"\\s*:\\s*true").matcher(packageJson).find();
        boolean hasEntry = Pattern.compile("\"(main|module|exports|types|typings)\"\\s*:").matcher(packageJson).find();
        return !isPrivate && hasEntry;
    }
}
