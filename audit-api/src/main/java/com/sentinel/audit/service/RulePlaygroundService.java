package com.sentinel.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.pack.PackLoader;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Laboratório de regras: valida uma regra Semgrep (as mesmas checagens do carregador de pacotes) e a executa de
 * verdade sobre um trecho de código, para o autor ver o que casa antes de publicar o pacote.
 * Entradas pequenas, sem acesso ao projeto de ninguém, com limite de tempo e de execuções simultâneas.
 */
@ApplicationScoped
public class RulePlaygroundService {

    private static final Logger LOG = Logger.getLogger(RulePlaygroundService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final int MAX_RULE_CHARS = 20_000;
    public static final int MAX_CODE_CHARS = 100_000;
    private static final int MAX_MATCHES = 200;

    /** extensão informada -> nome do arquivo analisado */
    static final Map<String, String> FILES = Map.ofEntries(
            Map.entry("java", "Code.java"), Map.entry("ts", "code.ts"), Map.entry("tsx", "code.tsx"),
            Map.entry("js", "code.js"), Map.entry("jsx", "code.jsx"), Map.entry("py", "code.py"),
            Map.entry("go", "code.go"), Map.entry("rb", "code.rb"), Map.entry("php", "code.php"),
            Map.entry("cs", "Code.cs"), Map.entry("kt", "Code.kt"), Map.entry("sh", "code.sh"),
            Map.entry("yaml", "code.yaml"), Map.entry("json", "code.json"), Map.entry("tf", "main.tf"),
            Map.entry("dockerfile", "Dockerfile"));

    public record Match(String ruleId, int line, int endLine, String severity, String message, String snippet) {}

    public record Result(boolean valid, List<String> errors, List<String> warnings, List<Match> matches,
                         List<String> engineErrors, long millis) {}

    /** Já há execuções demais em andamento. */
    public static final class BusyException extends RuntimeException {
        public BusyException() {
            super("O laboratório está ocupado; tente novamente em instantes");
        }
    }

    /** Semgrep não está instalado ou o recurso está desligado. */
    public static final class UnavailableException extends RuntimeException {
        public UnavailableException(String message) {
            super(message);
        }
    }

    @ConfigProperty(name = "sentinel.audit.playground.enabled", defaultValue = "true")
    boolean enabled = true;

    @ConfigProperty(name = "sentinel.audit.playground.timeout-seconds", defaultValue = "30")
    int timeoutSeconds = 30;

    /** Substituível em testes. */
    String pathEnv = System.getenv("PATH");

    final Semaphore slots = new Semaphore(2);

    public Result run(String ruleYaml, String code, String extension) {
        if (!enabled) {
            throw new UnavailableException("O laboratório de regras está desligado (sentinel.audit.playground.enabled)");
        }
        if (ruleYaml == null || ruleYaml.isBlank()) {
            throw new IllegalArgumentException("Informe a regra");
        }
        if (code == null) {
            throw new IllegalArgumentException("Informe o código");
        }
        if (ruleYaml.length() > MAX_RULE_CHARS) {
            throw new IllegalArgumentException("Regra grande demais (máximo " + MAX_RULE_CHARS + " caracteres)");
        }
        if (code.length() > MAX_CODE_CHARS) {
            throw new IllegalArgumentException("Código grande demais (máximo " + MAX_CODE_CHARS + " caracteres)");
        }
        String fileName = FILES.get(extension == null ? "" : extension.toLowerCase(java.util.Locale.ROOT));
        if (fileName == null) {
            throw new IllegalArgumentException("Extensão não suportada: " + extension + " (use " + String.join(", ", new java.util.TreeSet<>(FILES.keySet())) + ")");
        }
        Path semgrep = ExternalToolsAccess.find("semgrep", pathEnv);
        if (semgrep == null) {
            throw new UnavailableException("Semgrep não está instalado neste servidor");
        }
        if (!slots.tryAcquire()) {
            throw new BusyException();
        }
        Path dir = null;
        long t0 = System.nanoTime();
        try {
            dir = Files.createTempDirectory("pg-");
            Path pack = dir.resolve("pack");
            Files.createDirectories(pack.resolve("rules"));
            Files.writeString(pack.resolve("pack.yml"), "id: playground\nname: Playground\nversion: 1.0.0\n", StandardCharsets.UTF_8);
            Files.writeString(pack.resolve("rules/rule.yml"), ruleYaml, StandardCharsets.UTF_8);

            PackLoader.Report report = PackLoader.inspect(pack);
            List<String> warnings = report.warnings().stream().filter(w -> !w.contains("sem caso de teste")).toList();
            if (!report.errors().isEmpty()) {
                return new Result(false, report.errors(), warnings, List.of(), List.of(), millis(t0));
            }

            Path target = dir.resolve("src");
            Files.createDirectories(target);
            Files.writeString(target.resolve(fileName), code, StandardCharsets.UTF_8);
            JsonNode out = execute(semgrep, pack.resolve("rules/rule.yml"), target.resolve(fileName), dir);
            return parse(out, code, warnings, millis(t0));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao preparar a execução: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Execução interrompida", e);
        } finally {
            slots.release();
            deleteQuietly(dir);
        }
    }

    private JsonNode execute(Path semgrep, Path rule, Path file, Path work) throws IOException, InterruptedException {
        List<String> cmd = List.of(semgrep.toString(), "--config", rule.toString(), "--json", "--quiet", "--metrics=off",
                "--disable-version-check", "--no-git-ignore", "--timeout", "5", "--max-memory", "1000", file.toString());
        Path outFile = work.resolve("out.json");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(work.toFile());
        pb.environment().put("SEMGREP_SEND_METRICS", "off");
        pb.environment().put("SEMGREP_ENABLE_VERSION_CHECK", "0");
        pb.redirectOutput(outFile.toFile());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            throw new IllegalStateException("A execução passou de " + timeoutSeconds + "s e foi encerrada; simplifique a regra");
        }
        String json = Files.readString(outFile, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            throw new IllegalStateException("O Semgrep não devolveu resultado (código " + p.exitValue() + ")");
        }
        return MAPPER.readTree(json);
    }

    static Result parse(JsonNode out, String code, List<String> warnings, long millis) {
        String[] lines = code.split("\n", -1);
        List<Match> matches = new ArrayList<>();
        for (JsonNode r : out.path("results")) {
            if (matches.size() >= MAX_MATCHES) {
                break;
            }
            String id = r.path("check_id").asText("");
            id = id.substring(id.lastIndexOf('.') + 1);
            int line = r.path("start").path("line").asInt(0);
            int end = r.path("end").path("line").asInt(line);
            StringBuilder snippet = new StringBuilder();
            for (int i = Math.max(1, line); i <= Math.min(end, lines.length) && i < line + 10; i++) {
                snippet.append(lines[i - 1]).append('\n');
            }
            matches.add(new Match(id, line, end, r.path("extra").path("severity").asText(""),
                    r.path("extra").path("message").asText(""), snippet.toString().stripTrailing()));
        }
        List<String> engineErrors = new ArrayList<>();
        for (JsonNode e : out.path("errors")) {
            String msg = e.path("message").asText(e.path("type").asText("erro"));
            engineErrors.add(msg.length() > 300 ? msg.substring(0, 297) + "..." : msg);
        }
        return new Result(true, List.of(), warnings, matches, engineErrors, millis);
    }

    private static long millis(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // limpeza best-effort
                }
            });
        } catch (IOException e) {
            LOG.debugf("Não foi possível limpar %s: %s", dir, e.getMessage());
        }
    }

    /** {@code ExternalTools} é interno ao pacote do analisador; este ponto de acesso evita abrir a visibilidade dele. */
    static final class ExternalToolsAccess {
        static Path find(String name, String pathEnv) {
            if (pathEnv == null || pathEnv.isBlank()) {
                return null;
            }
            for (String d : pathEnv.split(java.io.File.pathSeparator)) {
                if (d.isBlank()) {
                    continue;
                }
                Path c = Paths.get(d, name);
                if (Files.isRegularFile(c) && Files.isExecutable(c)) {
                    return c;
                }
            }
            return null;
        }
    }
}
