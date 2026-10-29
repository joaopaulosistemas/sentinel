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

    private static final int MAX_FILES_FOR_KEY_SCAN = 20_000;
    private static final String EXPORT_PREFIX = "export ";
    private static final long MAX_READ_BYTES = 2_000_000;


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
    private static final Pattern ENV_SECRET_KEY = Pattern.compile(
            "(?i)(SECRET|PASSWORD|PASSWD|TOKEN|PRIVATE_?KEY|API_?KEY|ACCESS_?KEY|CREDENTIAL|ENCRYPTION_?KEY|SIGNING_?KEY)");
    private static final Pattern ENV_URL_CREDENTIALS = Pattern.compile("[a-z][a-z0-9+.-]*://[^\\s/:@]+:[^\\s/@]+@");
    private static final Set<String> PY_LOCKS =Set.of("poetry.lock", "Pipfile.lock", "uv.lock");
    private static final Set<String> PROJECT_DOCS = Set.of("changelog.md", "contributing.md", "architecture.md", "security.md");
    private static final Set<String> ENTRY_POINTS = Set.of("main.java", "application.java", "main.py", "app.py", "main.ts",
            "main.tsx", "index.tsx", "server.js", "program.cs");
    /** Valores de .env que são marcador, não segredo (inteiros). */
    private static final Set<String> PLACEHOLDER_VALUES = Set.of("", "true", "false", "null", "secret", "password", "token");
    /** Valores de .env que começam como marcador ({@code ${VAR}}, {@code <valor>}, {@code your-...}). */
    private static final java.util.List<String> PLACEHOLDER_PREFIXES = java.util.List.of("${", "<", "your", "change", "xxx", "todo");

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
                s.count++;
                if (s.count > MAX_FILES) {
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

            projectDocs(file, lower, rel, dir);
            build(lower, rel);
            ci(lower, rel);
            lock(lower, rel, dir);
            secrets(name, lower, rel, attrs);
            boolean inTestDir = isTest(rel, name);
            if (!inTestDir) {
                docker(name, lower, rel, dir);          // Dockerfiles de fixtures de teste não são do projeto
                infrastructure(lower, rel);
            }
            countSource(lower, rel, name);
        }

        /** README, licença, .gitignore e documentos (os da raiz marcam o que o projeto tem). */
        private void projectDocs(Path file, String lower, String rel, Path dir) {
            boolean atRoot = dir.equals(root);
            if (lower.startsWith("readme")) {
                if (atRoot) {
                    readme = true;
                    readmeLines = countLines(file);
                }
                docs.add(rel);
            } else if (lower.startsWith("license") || lower.startsWith("licence") || lower.equals("copying")) {
                license |= atRoot;
            } else if (isProjectDoc(lower, rel)) {
                docs.add(rel);
            }
            if (lower.equals(".gitignore") && atRoot) {
                gitignore = true;
            }
        }

        private static boolean isProjectDoc(String lower, String rel) {
            return PROJECT_DOCS.contains(lower) || rel.startsWith("docs/") && lower.endsWith(".md");
        }

        /** Terraform e manifestos de Kubernetes/Helm; fixtures de teste não contam como infraestrutura. */
        private void infrastructure(String lower, String rel) {
            if (lower.endsWith(".tf")) {
                iac.add(rel);
            } else if ((lower.endsWith(".yaml") || lower.endsWith(".yml")) && (rel.contains("k8s/") || rel.contains("helm/")
                    || rel.contains("deploy/") || rel.contains("manifests/"))) {
                iac.add(rel);
            }
        }

        /** Conta o arquivo como código-fonte ou teste da sua linguagem (declarações .d.ts não contam). */
        private void countSource(String lower, String rel, String name) {
            int dot = lower.lastIndexOf('.');
            if (dot <= 0) {
                return;
            }
            String lang = LANG_BY_EXT.get(lower.substring(dot + 1));
            if (lang == null || lower.endsWith(".d.ts")) {
                return;
            }
            langs.merge(lang, 1, Integer::sum);
            if (isTest(rel, name)) {
                tests++;
            } else {
                source++;
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
            if (lower.equals("package.json") && !hasLockUpTree(dir)) {
                pkgNoLock.add(rel);
            }
        }

        /**
         * Monorepo: o lock fica na pasta do pacote ou em qualquer pasta acima dela, até a raiz do repositório
         * (workspace em subpasta, como "frontend/pnpm-lock.yaml", cobre "frontend/packages/ui/package.json").
         */
        private boolean hasLockUpTree(Path dir) {
            boolean hasLock = false;
            for (Path d = dir; d != null && d.startsWith(root) && !hasLock; d = d.getParent()) {
                for (String l : LOCKS) {
                    hasLock |= Files.exists(d.resolve(l));
                }
            }
            return hasLock;
        }

        void secrets(String name, String lower, String rel, BasicFileAttributes attrs) {
            if (isEnvFileName(lower) && attrs.size() > 0 && envHasSecret(read(root.resolve(rel)))) {
                envFiles.add(rel);
            }
            if (isPrivateKeyName(lower) && attrs.size() < MAX_FILES_FOR_KEY_SCAN && read(root.resolve(rel)).contains("PRIVATE KEY-----")) {
                keyFiles.add(rel);
            } else if (isKeystoreName(lower)) {
                keyFiles.add(rel);
            }
        }

        /** {@code .env} e {@code .env.*}, menos os modelos ({@code .example}, {@code .sample}, {@code .template}, {@code .dist}). */
        private static boolean isEnvFileName(String lower) {
            return lower.equals(".env") || lower.startsWith(".env.") && !(lower.endsWith(".example")
                    || lower.endsWith(".sample") || lower.endsWith(".template") || lower.endsWith(".dist"));
        }

        private static boolean isPrivateKeyName(String lower) {
            return lower.equals("id_rsa") || lower.equals("id_ed25519") || lower.endsWith(".pem") || lower.endsWith(".key");
        }

        private static boolean isKeystoreName(String lower) {
            return lower.endsWith(".p12") || lower.endsWith(".pfx") || lower.endsWith(".jks") || lower.endsWith(".keystore");
        }

        void docker(String name, String lower, String rel, Path dir) {
            if (isDockerfileName(lower)) {
                dockerfiles.add(rel);
                // o contexto de build costuma ser a pasta do Dockerfile ou a raiz
                if (!Files.exists(dir.resolve(".dockerignore")) && !Files.exists(root.resolve(".dockerignore"))) {
                    dockerNoIgnore.add(rel);
                }
            }
            if (ENTRY_POINTS.contains(lower)) {
                addEntry(rel);
            }
        }

        private static boolean isDockerfileName(String lower) {
            return lower.equals("dockerfile") || lower.startsWith("dockerfile.") || lower.endsWith(".dockerfile");
        }

        void addEntry(String rel) {
            if (entry.size() < MAX_LIST && !entry.contains(rel)) {
                entry.add(rel);
            }
        }

        /**
         * Um .env versionado só é achado quando guarda um segredo: chave de nome sensível com valor que não é
         * marcador, ou URL com usuário e senha. Arquivos só com configuração pública (VITE_*, URL de API, modo,
         * flags) são convenção de Vite/CRA e não vazam nada.
         */
        static boolean envHasSecret(String text) {
            for (String line : text.split("\\R")) {
                String l = line.strip();
                if (l.startsWith(EXPORT_PREFIX)) {
                    l = l.substring(EXPORT_PREFIX.length()).strip();
                }
                int eq = l.indexOf('=');
                if (l.startsWith("#") || eq <= 0) {
                    continue;
                }
                String key = l.substring(0, eq).strip();
                String value = unquote(l.substring(eq + 1).strip());
                if (ENV_URL_CREDENTIALS.matcher(value).find()
                        || ENV_SECRET_KEY.matcher(key).find() && !isEnvPlaceholder(value)) {
                    return true;
                }
            }
            return false;
        }

        private static String unquote(String v) {
            if (v.length() >= 2 && (v.charAt(0) == '"' || v.charAt(0) == '\'') && v.charAt(v.length() - 1) == v.charAt(0)) {
                return v.substring(1, v.length() - 1).strip();
            }
            int hash = v.indexOf(" #");
            return hash >= 0 ? v.substring(0, hash).strip() : v;
        }

        private static boolean isEnvPlaceholder(String v) {
            String s = v.toLowerCase(Locale.ROOT);
            return PLACEHOLDER_VALUES.contains(s) || PLACEHOLDER_PREFIXES.stream().anyMatch(s::startsWith);
        }

        static String read(Path p) {
            try {
                if (Files.size(p) > MAX_READ_BYTES) {
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
