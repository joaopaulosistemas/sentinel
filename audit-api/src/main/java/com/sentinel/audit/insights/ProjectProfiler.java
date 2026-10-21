package com.sentinel.audit.insights;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Lê a árvore do projeto (sem executar nada) e monta o {@link ProjectMap}.
 * Limites: {@value #MAX_FILES} arquivos; passado disso o mapa vem marcado como truncado.
 */
public final class ProjectProfiler {

    static final int MAX_FILES = 50_000;
    private static final int MAX_LIST = 25;
    private static final Set<String> SKIP_DIRS = Set.of(".git", ".idea", ".vscode", ".gradle", "vendor", "coverage",
            ".next", ".angular", ".venv", "venv", "__pycache__", ".mvn");
    private static final Set<String> ARTIFACT_DIRS = Set.of("node_modules", "target", "dist", "build", "__pycache__");
    private static final Set<String> TEST_DIRS = Set.of("test", "tests", "__tests__", "e2e", "spec", "specs");
    private static final Map<String, String> LANG_BY_EXT = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"), Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"),
            Map.entry("mjs", "JavaScript"), Map.entry("py", "Python"), Map.entry("cs", "C#"),
            Map.entry("go", "Go"), Map.entry("rb", "Ruby"), Map.entry("php", "PHP"), Map.entry("cbl", "COBOL"),
            Map.entry("cob", "COBOL"));
    private static final Pattern TEST_NAME = Pattern.compile(
            "(?i)(.*(Test|Tests|IT|Spec)\\.(java|kt|cs)|.*\\.(test|spec)\\.(ts|tsx|js|jsx|mjs)|test_.*\\.py|.*_test\\.(py|go))");
    private static final Set<String> LOCKS = Set.of("package-lock.json", "yarn.lock", "pnpm-lock.yaml", "npm-shrinkwrap.json");
    private static final Set<String> PY_LOCKS = Set.of("poetry.lock", "Pipfile.lock", "uv.lock");

    private ProjectProfiler() {
    }

    public static ProjectMap profile(Path root) throws IOException {
        State s = new State(root);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = dir.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (SKIP_DIRS.contains(lower)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (ARTIFACT_DIRS.contains(lower)) {
                    if (s.isCommittedArtifact(dir, lower)) {
                        s.artifactDirs.add(s.rel(dir));
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (lower.equals(".circleci")) {
                    s.ci.add(s.rel(dir));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (++s.count > MAX_FILES) {
                    s.truncated = true;
                    return FileVisitResult.TERMINATE;
                }
                if (attrs.isRegularFile()) {
                    s.visit(file, attrs);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return s.build();
    }

    private static final class State {
        final Path root;
        int count;
        boolean truncated;
        final Map<String, Integer> langs = new TreeMap<>();
        final Set<String> build = new LinkedHashSet<>();
        final Set<String> frameworks = new LinkedHashSet<>();
        final List<String> entry = new ArrayList<>();
        final List<String> ci = new ArrayList<>();
        final List<String> dockerfiles = new ArrayList<>();
        final List<String> iac = new ArrayList<>();
        final List<String> docs = new ArrayList<>();
        final List<String> lockfiles = new ArrayList<>();
        final List<String> scripts = new ArrayList<>();
        final List<String> envFiles = new ArrayList<>();
        final List<String> keyFiles = new ArrayList<>();
        final List<String> artifactDirs = new ArrayList<>();
        final List<String> pkgNoLock = new ArrayList<>();
        final List<String> dockerNoIgnore = new ArrayList<>();
        int source;
        int tests;
        boolean readme;
        int readmeLines;
        boolean gitignore;
        boolean license;

        State(Path root) {
            this.root = root;
        }

        String rel(Path p) {
            return root.relativize(p).toString().replace('\\', '/');
        }

        boolean isCommittedArtifact(Path dir, String lower) {
            Path parent = dir.getParent();
            return switch (lower) {
                case "node_modules" -> true;
                case "target" -> Files.exists(parent.resolve("pom.xml")) && Files.isDirectory(dir.resolve("classes"));
                case "__pycache__" -> true;
                default -> false;       // dist/build também são comuns como código-fonte: sem evidência, não reporta
            };
        }

        void visit(Path file, BasicFileAttributes attrs) {
            String name = file.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);
            String rel = rel(file);
            Path dir = file.getParent();

            if (lower.startsWith("readme")) {
                if (dir.equals(root)) {
                    readme = true;
                    readmeLines = countLines(file);
                }
                docs.add(rel);
            } else if (lower.startsWith("license") || lower.startsWith("licence") || lower.equals("copying")) {
                if (dir.equals(root)) {
                    license = true;
                }
            } else if (lower.equals("changelog.md") || lower.equals("contributing.md") || lower.equals("architecture.md")
                    || lower.equals("security.md") || rel.startsWith("docs/") && lower.endsWith(".md")) {
                docs.add(rel);
            }
            if (lower.equals(".gitignore") && dir.equals(root)) {
                gitignore = true;
            }

            build(lower, rel);
            ci(lower, rel);
            lock(lower, rel, dir);
            secrets(name, lower, rel, attrs);
            boolean inTestDir = isTest(rel, name);
            if (!inTestDir) {
                docker(name, lower, rel, dir);          // Dockerfiles de fixtures de teste não são do projeto
            }

            if (inTestDir) {
                // fixtures de teste não contam como infraestrutura
            } else if (lower.endsWith(".tf")) {
                iac.add(rel);
            } else if ((lower.endsWith(".yaml") || lower.endsWith(".yml")) && (rel.contains("k8s/") || rel.contains("helm/")
                    || rel.contains("deploy/") || rel.contains("manifests/"))) {
                iac.add(rel);
            }

            int dot = lower.lastIndexOf('.');
            if (dot > 0) {
                String lang = LANG_BY_EXT.get(lower.substring(dot + 1));
                if (lang != null && !lower.endsWith(".d.ts")) {
                    langs.merge(lang, 1, Integer::sum);
                    if (isTest(rel, name)) {
                        tests++;
                    } else {
                        source++;
                    }
                }
            }
        }

        boolean isTest(String rel, String name) {
            if (TEST_NAME.matcher(name).matches()) {
                return true;
            }
            for (String seg : rel.split("/")) {
                if (TEST_DIRS.contains(seg.toLowerCase(Locale.ROOT)) && !seg.equals(name)) {
                    return true;
                }
            }
            return false;
        }

        void build(String lower, String rel) {
            switch (lower) {
                case "pom.xml" -> {
                    build.add("Maven");
                    detectJavaFrameworks(rel);
                }
                case "build.gradle", "build.gradle.kts" -> build.add("Gradle");
                case "package.json" -> {
                    build.add("npm");
                    detectNode(rel);
                }
                case "requirements.txt", "pyproject.toml", "pipfile" -> build.add("Python (pip/poetry)");
                case "go.mod" -> build.add("Go modules");
                case "makefile" -> build.add("Make");
                default -> {
                    if (lower.endsWith(".csproj") || lower.endsWith(".sln")) {
                        build.add(".NET");
                    }
                }
            }
        }

        void detectJavaFrameworks(String rel) {
            String t = read(root.resolve(rel));
            if (t.contains("quarkus")) {
                frameworks.add("Quarkus");
            }
            if (t.contains("spring-boot")) {
                frameworks.add("Spring Boot");
            }
            if (t.contains("micronaut")) {
                frameworks.add("Micronaut");
            }
        }

        void detectNode(String rel) {
            String t = read(root.resolve(rel));
            for (String[] fw : new String[][]{{"\"react\"", "React"}, {"\"@angular/core\"", "Angular"}, {"\"vue\"", "Vue"},
                    {"\"next\"", "Next.js"}, {"\"express\"", "Express"}, {"\"@nestjs/core\"", "NestJS"},
                    {"\"@carbon/react\"", "Carbon"}, {"\"vite\"", "Vite"}}) {
                if (t.contains(fw[0])) {
                    frameworks.add(fw[1]);
                }
            }
            java.util.regex.Matcher m = Pattern.compile("\"scripts\"\\s*:\\s*\\{([^}]*)}", Pattern.DOTALL).matcher(t);
            if (m.find()) {
                java.util.regex.Matcher k = Pattern.compile("\"([\\w:.-]+)\"\\s*:").matcher(m.group(1));
                while (k.find() && scripts.size() < MAX_LIST) {
                    scripts.add(rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) + ": " + k.group(1) : k.group(1));
                }
            }
            if (t.contains("\"main\"") || t.contains("\"bin\"")) {
                addEntry(rel);
            }
        }

        void ci(String lower, String rel) {
            if (rel.startsWith(".github/workflows/") && (lower.endsWith(".yml") || lower.endsWith(".yaml"))
                    || lower.equals(".gitlab-ci.yml") || lower.equals("jenkinsfile")
                    || lower.equals("azure-pipelines.yml") || lower.equals("bitbucket-pipelines.yml")
                    || lower.equals("cloudbuild.yaml") || lower.equals(".travis.yml")) {
                ci.add(rel);
            }
        }

        void lock(String lower, String rel, Path dir) {
            if (LOCKS.contains(lower) || PY_LOCKS.contains(lower) || lower.equals("pom.xml.lock") || lower.equals("gradle.lockfile")) {
                lockfiles.add(rel);
            }
            if (lower.equals("package.json")) {
                boolean hasLock = false;
                for (String l : LOCKS) {
                    hasLock |= Files.exists(dir.resolve(l));
                }
                // monorepo: o lock pode estar na raiz
                for (String l : LOCKS) {
                    hasLock |= Files.exists(root.resolve(l));
                }
                if (!hasLock) {
                    pkgNoLock.add(rel);
                }
            }
        }

        void secrets(String name, String lower, String rel, BasicFileAttributes attrs) {
            boolean envFile = lower.equals(".env") || lower.startsWith(".env.") && !(lower.endsWith(".example")
                    || lower.endsWith(".sample") || lower.endsWith(".template") || lower.endsWith(".dist"));
            if (envFile && attrs.size() > 0) {
                envFiles.add(rel);
            }
            boolean keyName = lower.equals("id_rsa") || lower.equals("id_ed25519") || lower.endsWith(".pem")
                    || lower.endsWith(".key");
            if (keyName && attrs.size() < 20_000 && read(root.resolve(rel)).contains("PRIVATE KEY-----")) {
                keyFiles.add(rel);
            } else if (lower.endsWith(".p12") || lower.endsWith(".pfx") || lower.endsWith(".jks") || lower.endsWith(".keystore")) {
                keyFiles.add(rel);
            }
        }

        void docker(String name, String lower, String rel, Path dir) {
            if (lower.equals("dockerfile") || lower.startsWith("dockerfile.") || lower.endsWith(".dockerfile")) {
                dockerfiles.add(rel);
                // o contexto de build costuma ser a pasta do Dockerfile ou a raiz
                if (!Files.exists(dir.resolve(".dockerignore")) && !Files.exists(root.resolve(".dockerignore"))) {
                    dockerNoIgnore.add(rel);
                }
            }
            if (lower.equals("main.java") || lower.equals("application.java") || lower.equals("main.py") || lower.equals("app.py")
                    || lower.equals("main.ts") || lower.equals("main.tsx") || lower.equals("index.tsx") || lower.equals("server.js")
                    || lower.equals("program.cs")) {
                addEntry(rel);
            }
        }

        void addEntry(String rel) {
            if (entry.size() < MAX_LIST && !entry.contains(rel)) {
                entry.add(rel);
            }
        }

        static String read(Path p) {
            try {
                if (Files.size(p) > 2_000_000) {
                    return "";
                }
                return Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return "";
            }
        }

        static int countLines(Path p) {
            int n = 0;
            for (String l : read(p).split("\\R")) {
                if (!l.isBlank()) {
                    n++;
                }
            }
            return n;
        }

        static List<String> cap(List<String> l) {
            return l.size() > MAX_LIST ? new ArrayList<>(l.subList(0, MAX_LIST)) : l;
        }

        ProjectMap build() {
            Map<String, Integer> sorted = new LinkedHashMap<>();
            langs.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> sorted.put(e.getKey(), e.getValue()));
            return new ProjectMap(sorted, new ArrayList<>(build), new ArrayList<>(frameworks), cap(entry), cap(ci),
                    cap(dockerfiles), cap(iac), cap(docs), cap(lockfiles), source, tests, readme, readmeLines, gitignore,
                    license, cap(scripts), cap(envFiles), cap(keyFiles), cap(artifactDirs), cap(pkgNoLock),
                    cap(dockerNoIgnore), truncated);
        }
    }
}
