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
        return run(command, workingDir, env, timeoutSeconds, stdoutFile, null);
    }

    /**
     * Como o anterior, mas grava a saída de erro em {@code stderrFile} (quando não nulo), para que uma falha da
     * ferramenta possa ser explicada ao usuário em vez de ficar só no log.
     */
    static int run(List<String> command, Path workingDir, Map<String, String> env, long timeoutSeconds, Path stdoutFile,
                   Path stderrFile) throws IOException, InterruptedException {
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

    /** Arquivo em pasta de build, dependências ou testes, ou biblioteca embutida: lint e métricas não valem a pena aí. */
    static boolean inIgnoredDir(String relPath) {
        if (relPath == null) {
            return false;
        }
        String[] parts = relPath.replace('\\', '/').toLowerCase(java.util.Locale.ROOT).split("/");
        for (int i = 0; i < parts.length - 1; i++) {      // o último segmento é o arquivo
            if (CleanCodeAnalyzer.IGNORED_DIRS.contains(parts[i])) {
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
        return false;
    }
}
