package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.structure.SourceFiles.Src;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Descobre se o projeto autentica todas as rotas num ponto só (filtro JAX-RS, Spring Security, política HTTP do
 * Quarkus). Quando existe, a regra "endpoint sem anotação de autorização" perde sentido: ela só enxerga o recurso.
 * Heurística por texto: não prova que TODA rota está coberta, só que há uma barreira global.
 */
public final class GlobalAuth {

    private GlobalAuth() {}

    private static final Pattern REJECTS = Pattern.compile("abortWith|UNAUTHORIZED|FORBIDDEN|\\b40[13]\\b");
    private static final Pattern PROVIDER = Pattern.compile("@Provider|@PreMatching|@Priority|@ServerRequestFilter");
    private static final Pattern SPRING_ALL = Pattern.compile("anyRequest\\(\\)\\s*\\.\\s*(authenticated|fullyAuthenticated)\\s*\\(");
    private static final Pattern SPRING_CHAIN = Pattern.compile("SecurityFilterChain|@EnableWebSecurity|WebSecurityConfigurerAdapter");
    private static final Pattern QUARKUS_PROPS = Pattern.compile("quarkus\\.http\\.auth\\.permission\\.[\\w-]+\\.policy\\s*=\\s*authenticated");
    private static final Pattern QUARKUS_YAML = Pattern.compile("(?m)^\\s*policy:\\s*authenticated\\b");

    /** Descrição curta da barreira global, ou {@code null} se não houver. */
    public static String detect(Path root) {
        try {
            List<Src> java = SourceFiles.read(root, SourceFiles.JAVA, 20_000, false);
            String filter = fromJava(java);
            if (filter != null) {
                return filter;
            }
            return fromConfig(root);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static String fromJava(List<Src> java) {
        for (Src f : java) {
            String t = f.text();
            String cls = f.rel().substring(f.rel().lastIndexOf('/') + 1).replace(".java", "");
            if (t.contains("ContainerRequestFilter") && PROVIDER.matcher(t).find() && REJECTS.matcher(t).find()) {
                return "filtro " + cls + " (ContainerRequestFilter)";
            }
            if (SPRING_CHAIN.matcher(t).find() && SPRING_ALL.matcher(t).find()) {
                return "Spring Security em " + cls + " (anyRequest().authenticated())";
            }
        }
        return null;
    }

    static String fromConfig(Path root) throws IOException {
        for (String rel : new String[]{"src/main/resources/application.properties", "src/main/resources/application.yml",
                "src/main/resources/application.yaml"}) {
            for (Path candidate : candidates(root, rel)) {
                String text = Files.readString(candidate);
                if (QUARKUS_PROPS.matcher(text).find() || QUARKUS_YAML.matcher(text).find()) {
                    return "política HTTP do Quarkus (" + candidate.getFileName() + ")";
                }
            }
        }
        return null;
    }

    /** O arquivo na raiz e em módulos de primeiro nível (ex.: audit-api/src/main/resources/...). */
    private static List<Path> candidates(Path root, String rel) throws IOException {
        List<Path> out = new java.util.ArrayList<>();
        Path direct = root.resolve(rel);
        if (Files.isRegularFile(direct) && !Files.isSymbolicLink(direct)) {
            out.add(direct);
        }
        try (var dirs = Files.list(root)) {
            for (Path d : (Iterable<Path>) dirs::iterator) {
                Path p = d.resolve(rel);
                if (Files.isDirectory(d) && !Files.isSymbolicLink(d) && Files.isRegularFile(p) && !Files.isSymbolicLink(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }
}
