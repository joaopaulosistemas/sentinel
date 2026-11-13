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
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                ProcessTree.destroy(process);
                return -1;
            }
        } catch (InterruptedException e) {
            // auditoria cancelada: a ferramenta (e o que ela iniciou) não pode continuar rodando sem ninguém esperando
            ProcessTree.destroy(process);
            throw e;
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
        if (err.startsWith("…")) {
            // stderr longo: o fim costuma ser só o aviso da ferramenta ("report this issue"); a causa está antes.
            String causa = exceptionLines(stderrFile);
            if (!causa.isEmpty()) {
                err = causa + " … " + err.substring(1).trim();
            }
        }
        return err.isEmpty() ? base : base + " — " + err;
    }

    private static final int CAUSE_LINES = 2;
    private static final int CAUSE_LINE_CHARS = 200;

    /** As primeiras linhas de exceção do stderr ({@code ...Exception: msg} e {@code Caused by: ...}); vazio se não houver. */
    static String exceptionLines(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) {
                return "";
            }
            java.util.List<String> found = new java.util.ArrayList<>();
            for (String line : Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                String t = line.trim();
                boolean isException = t.startsWith("Caused by:") || t.matches("^(Exception in thread \"[^\"]*\" )?[\\w.$]+(Exception|Error)(:.*)?$");
                if (isException) {
                    found.add(t.length() <= CAUSE_LINE_CHARS ? t : t.substring(0, CAUSE_LINE_CHARS) + "…");
                    if (found.size() == CAUSE_LINES) {
                        break;
                    }
                }
            }
            return String.join(" ", found);
        } catch (Exception e) {
            return "";
        }
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
        return VendoredCode.isVendoredLibrary(relPath);
    }
}
