package com.sentinel.audit.analyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Utilidades para analisadores que delegam o trabalho a uma ferramenta externa (Trivy, Gitleaks...). */
final class ExternalTools {

    /** Quanto da saída de erro de uma ferramenta entra na mensagem. */
    private static final int ERROR_TAIL_CHARS = 300;


    private ExternalTools() {}

    /**
     * Procura o executável no PATH informado <b>sem executá-lo</b> (executar só para checar versão pode
     * demorar minutos em rede restrita). Devolve o caminho absoluto ou {@code null}.
     */
    static Path find(String name, String pathEnv) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String candidate : List.of(name, name + ".exe", name + ".cmd", name + ".bat")) {
                Path p = Paths.get(dir, candidate);
                if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * Executa o comando descartando stdout e stderr (a saída útil vai para arquivos pedidos à própria ferramenta).
     *
     * @return o código de saída, ou {@code -1} se estourou o tempo (o processo é encerrado)
     */
    static int run(List<String> command, Map<String, String> env, long timeoutSeconds)
            throws IOException, InterruptedException {
        return run(command, null, env, timeoutSeconds);
    }

    /** Como {@link #run(List, Map, long)}, mas com diretório de trabalho (ESLint usa-o como raiz dos arquivos). */
    static int run(List<String> command, Path workingDir, Map<String, String> env, long timeoutSeconds)
            throws IOException, InterruptedException {
        return run(command, workingDir, env, timeoutSeconds, null);
    }

    /** Como o anterior, mas grava a saída padrão em {@code stdoutFile} (ferramentas que só imprimem o SARIF). */
    static int run(List<String> command, Path workingDir, Map<String, String> env, long timeoutSeconds, Path stdoutFile)
            throws IOException, InterruptedException {
        return runTo(command, workingDir, env, timeoutSeconds, new ExternalTools.Output(stdoutFile, null));
    }

    /** Para onde vão a saída padrão e a de erro do processo (nulo descarta). */
    record Output(Path stdout, Path stderr) { }

    /**
     * Como o anterior, mas grava a saída de erro em {@code stderrFile} (quando não nulo), para que uma falha da
     * ferramenta possa ser explicada ao usuário em vez de ficar só no log.
     */
    static int runTo(List<String> command, Path workingDir, Map<String, String> env, long timeoutSeconds, Output output)
            throws IOException, InterruptedException {
        Path stdoutFile = output.stdout();
        Path stderrFile = output.stderr();
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        pb.environment().putAll(env);
        pb.redirectOutput(stdoutFile == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(stdoutFile.toFile()));
        pb.redirectError(stderrFile == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(stderrFile.toFile()));
        Process process = pb.start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return -1;
        }
        return process.exitValue();
    }

    static void deleteQuietly(Path p) {
        try {
            if (p != null) {
                Files.deleteIfExists(p);
            }
        } catch (Exception ignored) {
            // arquivo temporário; não vale falhar por isso
        }
    }

    /** Últimos caracteres do arquivo (espaços colapsados); vazio se não der para ler. Nunca lança. */
    static String tail(Path file, int maxChars) {
        try {
            if (file == null || !Files.isRegularFile(file)) {
                return "";
            }
            String t = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
            return t.length() <= maxChars ? t : "…" + t.substring(t.length() - maxChars);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Texto da nota de resultado PARCIAL quando a ferramenta não terminou direito: {@code code == -1} é tempo
     * esgotado; qualquer outro valor, saída anormal. Junta o fim do stderr (a causa real) quando houver.
     */
    static String falha(int code, long timeoutSeconds, Path stderrFile) {
        String base = code == -1 ? "excedeu o tempo limite de " + timeoutSeconds + "s e foi encerrado"
                : "terminou com código " + code;
        String err = tail(stderrFile, ERROR_TAIL_CHARS);
        return err.isEmpty() ? base : base + " — " + err;
    }

    /** Caminho relativo à raiz do projeto quando a ferramenta devolve caminho absoluto. */
    static String relativize(Path root, String path) {
        if (path == null || path.isBlank()) {
            return path == null ? "" : path;
        }
        try {
            Path p = Paths.get(path);
            return p.isAbsolute() && p.startsWith(root) ? root.relativize(p).toString() : path;
        } catch (RuntimeException e) {
            return path;
        }
    }

    /** {@code src/it} é onde o Maven guarda os testes de integração; "it" sozinho pode ser o idioma italiano. */
    static boolean isMavenItDir(String[] parts, int i) {
        return "it".equals(parts[i]) && i > 0 && "src".equals(parts[i - 1]) && i < parts.length - 1;
    }

    /** Arquivo em pasta de build, dependências ou testes, ou biblioteca embutida: lint e métricas não valem a pena aí. */
    static boolean inIgnoredDir(String relPath) {
        if (relPath == null) {
            return false;
        }
        String[] parts = relPath.replace('\\', '/').toLowerCase(java.util.Locale.ROOT).split("/");
        for (int i = 0; i < parts.length - 1; i++) {      // o último segmento é o arquivo
            if (CleanCodeAnalyzer.IGNORED_DIRS.contains(parts[i]) || isMavenItDir(parts, i)) {
                return true;
            }
        }
        return isVendoredLibrary(relPath);
    }

    private static final java.util.regex.Pattern VENDORED_NAME = java.util.regex.Pattern.compile(
            "^(jquery[\\w.-]*|font-?awesome[\\w.-]*|patternfly[\\w.-]*|[\\w.-]+\\.min)\\.(js|css|map)$");
    /** "bootstrap.js" também é o nome do arquivo de partida de aplicações Angular/Webpack: só vale como biblioteca em pasta de ativos. */
    private static final java.util.regex.Pattern VENDORED_ASSET_NAME = java.util.regex.Pattern.compile(
            "^bootstrap[\\w.-]*\\.(js|css)$");
    private static final java.util.Set<String> ASSET_DIRS = java.util.Set.of("js", "css", "lib", "assets", "static", "public", "webapp");
    /** Pastas onde se guardam bibliotecas de terceiros dentro dos ativos web (ex.: static/plugins, static/js/libs). */
    private static final java.util.Set<String> THIRD_PARTY_DIRS = java.util.Set.of("plugins", "libs", "lib", "vendor", "vendors", "third_party", "third-party", "thirdparty");
    private static final java.util.Set<String> WEB_ROOT_DIRS = java.util.Set.of("static", "public", "webapp", "assets", "resources", "www");
    /** Saída de build com hash no nome (chunk-0226s3f2.57e3ed6f.js, main.3f2a9c1b.css): arquivo gerado, não escrito à mão. */
    private static final java.util.regex.Pattern HASHED_BUILD_NAME = java.util.regex.Pattern.compile(
            "^[\\w-]+\\.[0-9a-f]{8,}\\.(js|mjs|css)$");
    private static final java.util.regex.Pattern WEB_EXT = java.util.regex.Pattern.compile("\\.(js|mjs|css|map)$");

    /**
     * Biblioteca de terceiros copiada para dentro do projeto (jQuery, Bootstrap, Font Awesome, PatternFly, {@code *.min.*}).
     * Na rodada de 2026-10-22 elas responderam por 71% dos achados de um repositório e não são código do projeto.
     */
    static boolean isVendoredLibrary(String relPath) {
        if (relPath == null || relPath.isBlank()) {
            return false;
        }
        String[] parts = relPath.replace('\\', '/').toLowerCase(java.util.Locale.ROOT).split("/");
        String name = parts[parts.length - 1];
        if (VENDORED_NAME.matcher(name).matches()) {
            return true;
        }
        if (VENDORED_ASSET_NAME.matcher(name).matches()) {
            for (int i = 0; i < parts.length - 1; i++) {
                if (ASSET_DIRS.contains(parts[i])) {
                    return true;
                }
            }
        }
        if (HASHED_BUILD_NAME.matcher(name).matches() && underWebRoot(parts)) {
            return true;
        }
        return inThirdPartyAssetDir(parts, name);
    }

    private static boolean underWebRoot(String[] parts) {
        for (int i = 0; i < parts.length - 1; i++) {
            if (WEB_ROOT_DIRS.contains(parts[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * JS/CSS dentro de {@code plugins}, {@code libs} ou {@code vendor} que esteja sob uma pasta de ativos web
     * ({@code static}, {@code public}, {@code webapp}, {@code assets}, {@code resources}). Fora dos ativos (build, src/plugins)
     * ou de outro tipo de arquivo (Java etc.) não vale: o nome sozinho não prova que é de terceiros.
     */
    private static boolean inThirdPartyAssetDir(String[] parts, String name) {
        if (!WEB_EXT.matcher(name).find()) {
            return false;
        }
        int web = -1;
        for (int i = 0; i < parts.length - 1; i++) {
            if (web < 0 && WEB_ROOT_DIRS.contains(parts[i])) {
                web = i;
            } else if (web >= 0 && THIRD_PARTY_DIRS.contains(parts[i])) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.regex.Pattern LICENSE_WORD = java.util.regex.Pattern.compile(
            "(?i)@license|\\blicen[sc]ed? under\\b|\\blicensed to the apache\\b|\\bapache software foundation\\b|\\b(mit|bsd|apache|gpl|lgpl|mpl|isc)\\b[\\w .-]{0,12}licen[sc]e|\\bmit\\b");
    private static final java.util.regex.Pattern OUTSIDE_SIGN = java.util.regex.Pattern.compile(
            "(?i)https?://|@license|^/\\*!|\\bv?\\d+\\.\\d+\\.\\d+\\b");
    /** Endereços do texto de licenças padrão; não indicam de onde veio o arquivo. */
    private static final java.util.regex.Pattern LICENSE_TEXT_URL = java.util.regex.Pattern.compile(
            "(?i)https?://(www\\.)?(apache\\.org/licenses|opensource\\.org/licenses?|gnu\\.org/licenses|creativecommons\\.org/licenses|mozilla\\.org/(MPL|en-US/MPL))[^\\s)\\]>\"']*");
    private static final java.util.regex.Pattern ASF_NAME = java.util.regex.Pattern.compile("(?i)\\bapache software foundation\\b");
    private static final int BANNER_CHARS = 1200;

    /**
     * O arquivo começa com o comentário de uma biblioteca: licença (MIT, Apache, BSD, GPL...) e um sinal de origem
     * externa (endereço, versão, {@code /*!} ou {@code @license}). Só olha o começo do arquivo (depois de um
     * {@code @charset}); licença citada no meio, ou aviso de licença sem origem externa, não conta.
     */
    static boolean hasThirdPartyBanner(String head) {
        if (head == null) {
            return false;
        }
        String h = head.stripLeading();
        if (h.startsWith("@charset")) {
            int nl = h.indexOf('\n');
            h = nl < 0 ? "" : h.substring(nl + 1).stripLeading();
        }
        String comment;
        if (h.startsWith("//")) {                       // bloco de comentários de linha no topo (ex.: "// CodeMirror, copyright ... MIT license: https://...")
            StringBuilder sb = new StringBuilder();
            for (String line : h.substring(0, Math.min(h.length(), BANNER_CHARS)).split("\\R")) {
                if (!line.stripLeading().startsWith("//")) {
                    break;
                }
                sb.append(line).append('\n');
            }
            comment = sb.toString();
        } else if (h.startsWith("/*")) {
            h = h.substring(0, Math.min(h.length(), BANNER_CHARS));
            int end = h.indexOf("*/");
            comment = end < 0 ? h : h.substring(0, end);
        } else if (preservedBannerAfterPrelude(h) != null) {   // pacote otimizado: uma ou duas linhas de código e só então o /*! da licença
            comment = preservedBannerAfterPrelude(h);
        } else {
            return false;
        }
        if (!LICENSE_WORD.matcher(comment).find()) {
            return false;
        }
        // O endereço do texto da licença (Apache 2.0, GPL...) vem em todo arquivo licenciado, inclusive o do próprio projeto:
        // sozinho não prova origem externa. A Fundação Apache (ASF) continua valendo como sinal próprio.
        String semUrlDeLicenca = LICENSE_TEXT_URL.matcher(comment).replaceAll(" ");
        return OUTSIDE_SIGN.matcher(semUrlDeLicenca).find() || ASF_NAME.matcher(comment).find();
    }

    /** Quantos caracteres de código podem vir antes do comentário {@code /*!} de licença (ex.: {@code window["x"]=true; try {}). */
    private static final int PRELUDE_CHARS = 200;

    /** O comentário {@code /*!} (convenção para licença preservada) logo no começo, depois de um prefácio curto; nulo se não houver. */
    private static String preservedBannerAfterPrelude(String h) {
        int start = h.indexOf("/*!");
        if (start < 0 || start > PRELUDE_CHARS) {
            return null;
        }
        int end = h.indexOf("*/", start);
        return end < 0 ? h.substring(start) : h.substring(start, end);
    }

    /** Lê o começo de um JS/CSS e aplica {@link #hasThirdPartyBanner(String)}; erro de leitura vale "não". */
    static boolean hasThirdPartyBanner(Path file) {
        if (file == null || !BUNDLE_EXT.matcher(file.getFileName().toString()).find()) {
            return false;
        }
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
            return hasThirdPartyBanner(new String(in.readNBytes(BANNER_CHARS + 40), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }

    /** JS/CSS acima deste tamanho é pacote de terceiros (three.js, bundles); código escrito à mão não chega perto. */
    static final long OVERSIZED_BUNDLE_BYTES = 400_000;
    private static final java.util.regex.Pattern BUNDLE_EXT = java.util.regex.Pattern.compile("(?i)\\.(js|mjs|cjs|css)$");

    /**
     * Arquivo JS/CSS grande demais para ser código do projeto (ex.: three.js com 850 KB deu 2.117 achados numa só
     * auditoria). Vale pelo tamanho, não pelo nome, então pega bibliotecas copiadas sem banner nem padrão de nome.
     */
    static boolean isOversizedBundle(String relPath, long bytes) {
        return relPath != null && bytes >= OVERSIZED_BUNDLE_BYTES && BUNDLE_EXT.matcher(relPath).find();
    }

    /**
     * Máximo de padrões {@code --exclude} passados ao motor SAST. O Opengrep/Semgrep compila os padrões com o wcmatch, que
     * aborta com "Pattern limit exceeded the limit of 1000" (cada padrão vira mais de um): com ~500 a varredura já morre
     * sem nenhum achado. No DVHMA (OpenUI5 embutido, 1.173 arquivos) o SAST inteiro sumiu por isso.
     */
    static final int MAX_VENDORED_EXCLUDES = 400;
    /** Acima disto, arquivos de terceiros viram exclusão por pasta (quando a pasta só tem JS/CSS de terceiros). */
    static final int COLLAPSE_ABOVE = 300;

    /**
     * Pacotes grandes, bibliotecas embutidas e arquivos com cabeçalho de terceiros, para o motor SAST excluir.
     * O motor só excluía por nome; as mesmas regras de {@link #isVendoredLibrary} e {@link #hasThirdPartyBanner}
     * já valiam para lint e métricas, mas o Opengrep seguia lendo layui, codemirror e afins. Com muitos arquivos a lista
     * vira pastas inteiras ({@link #collapseToDirs}), para não estourar o limite de padrões do motor.
     */
    static java.util.List<String> vendoredFiles(Path root) {
        java.util.List<String> found = new java.util.ArrayList<>();
        java.util.Map<String, Integer> totalPorPasta = new java.util.HashMap<>();
        try {
            java.nio.file.Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes a) {
                    boolean skip = !dir.equals(root)
                            && CleanCodeAnalyzer.IGNORED_DIRS.contains(dir.getFileName().toString().toLowerCase(java.util.Locale.ROOT));
                    return skip ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes a) {
                    String rel = root.relativize(file).toString().replace('\\', '/');
                    if (a.isRegularFile() && BUNDLE_EXT.matcher(rel).find()) {
                        totalPorPasta.merge(parentOf(rel), 1, Integer::sum);
                        if (found.size() < MAX_COLLECTED
                                && (isOversizedBundle(rel, a.size()) || isVendoredLibrary(rel) || hasThirdPartyBanner(file))) {
                            found.add(rel);
                        }
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // sem a lista o motor apenas analisa mais arquivos
        }
        return collapseToDirs(found, totalPorPasta);
    }

    /** Teto de arquivos de terceiros lembrados durante a varredura (antes de colapsar em pastas). */
    private static final int MAX_COLLECTED = 50_000;

    private static String parentOf(String rel) {
        int i = rel.lastIndexOf('/');
        return i < 0 ? "" : rel.substring(0, i);
    }

    /**
     * Até {@link #COLLAPSE_ABOVE} arquivos a lista fica como está. Acima disso, toda pasta cujos JS/CSS são todos de
     * terceiros entra uma vez só (a pasta), e os arquivos soltos de pastas mistas continuam individuais. O resultado
     * respeita {@link #MAX_VENDORED_EXCLUDES}: pastas primeiro, depois arquivos, o excedente fica de fora.
     */
    static java.util.List<String> collapseToDirs(java.util.List<String> files, java.util.Map<String, Integer> totalPorPasta) {
        if (files.size() <= COLLAPSE_ABOVE) {
            return files;
        }
        java.util.Map<String, Integer> vendoradosPorPasta = new java.util.HashMap<>();
        for (String f : files) {
            vendoradosPorPasta.merge(parentOf(f), 1, Integer::sum);
        }
        java.util.Set<String> pastas = new java.util.TreeSet<>();
        for (java.util.Map.Entry<String, Integer> e : vendoradosPorPasta.entrySet()) {
            if (!e.getKey().isEmpty() && e.getValue().equals(totalPorPasta.get(e.getKey()))) {
                pastas.add(e.getKey());
            }
        }
        java.util.List<String> out = new java.util.ArrayList<>(pastas);
        for (String f : files) {
            if (!pastas.contains(parentOf(f))) {
                out.add(f);
            }
        }
        return out.size() > MAX_VENDORED_EXCLUDES ? new java.util.ArrayList<>(out.subList(0, MAX_VENDORED_EXCLUDES)) : out;
    }

    /** Caminhos relativos (com "/") dos pacotes grandes sob a raiz, para o motor SAST excluir. Pastas ignoradas são puladas. */
    static java.util.List<String> oversizedBundles(Path root) {
        java.util.List<String> found = new java.util.ArrayList<>();
        try {
            java.nio.file.Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes a) {
                    boolean skip = !dir.equals(root)
                            && CleanCodeAnalyzer.IGNORED_DIRS.contains(dir.getFileName().toString().toLowerCase(java.util.Locale.ROOT));
                    return skip ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes a) {
                    String rel = root.relativize(file).toString().replace('\\', '/');
                    if (a.isRegularFile() && isOversizedBundle(rel, a.size())) {
                        found.add(rel);
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // sem a lista o motor apenas analisa mais arquivos
        }
        return found;
    }
}
