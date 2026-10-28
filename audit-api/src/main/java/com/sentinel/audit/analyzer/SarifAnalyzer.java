package com.sentinel.audit.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.RulePack;
import com.sentinel.audit.pack.ToolSpec;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Importa findings de <b>qualquer ferramenta que exporte SARIF 2.1.0</b> (SpotBugs, Checkstyle, CodeQL, Sonar,
 * dependency-cruiser...) a partir das fontes declaradas nos pacotes ({@code tools/*.yml}), sem código Java novo.
 * <p>
 * Dois modos, com riscos diferentes:
 * <ul>
 *   <li>{@code input}: lê arquivos SARIF já presentes no projeto auditado (ex.: gerados pelo CI do time).
 *       Só leitura; sempre permitido.</li>
 *   <li>{@code command}: executa a ferramenta. Pacote é código de confiança, então isso só roda se o administrador
 *       ligou {@code sentinel.audit.packs.allow-tools}; o comando é executado sem shell, com o projeto como diretório de trabalho.</li>
 * </ul>
 */
@ApplicationScoped
public class SarifAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(SarifAnalyzer.class);
    private static final String ANALYZER_NAME = "SARIF";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MAX_SARIF_BYTES = 20L * 1024 * 1024;
    private static final int MAX_FINDINGS_PER_TOOL = 1000;
    private static final int MAX_INPUT_FILES = 50;

    @ConfigProperty(name = "sentinel.audit.packs.allow-tools", defaultValue = "false")
    boolean allowTools = false;

    /** Substituível em testes. */
    String pathEnv = System.getenv("PATH");

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        if (policy != null) {
            outer:
            for (RulePack pack : policy.packs()) {
                boolean trusted = policy.isToolPackTrusted(pack.id());
                for (ToolSpec spec : pack.tools()) {
                    try {
                        findings.addAll(spec.executes() ? runCommand(spec, projectPath, auditRun, trusted)
                                : readInputs(spec, projectPath, auditRun));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break outer;
                    } catch (Exception e) {
                        LOG.warnf("Fonte SARIF '%s' falhou e foi ignorada: %s", spec.id(), e.getMessage());
                    }
                }
            }
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    // ------------------------------------------------------------------ modo input

    List<Finding> readInputs(ToolSpec spec, Path projectPath, AuditRun run) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + spec.input());
        List<Finding> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(projectPath)) {
            List<Path> files = walk.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p)
                    && !ExternalTools.inIgnoredDir(projectPath.relativize(p).toString())
                    && matcher.matches(projectPath.relativize(p))).limit(MAX_INPUT_FILES).toList();
            for (Path f : files) {
                if (Files.size(f) > MAX_SARIF_BYTES) {
                    LOG.warnf("SARIF %s ignorado: maior que %d MB", f.getFileName(), MAX_SARIF_BYTES / 1024 / 1024);
                    continue;
                }
                out.addAll(parse(Files.readString(f, StandardCharsets.UTF_8), spec, projectPath, run));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ modo command

    List<Finding> runCommand(ToolSpec spec, Path projectPath, AuditRun run) throws IOException, InterruptedException {
        return runCommand(spec, projectPath, run, false);
    }

    List<Finding> runCommand(ToolSpec spec, Path projectPath, AuditRun run, boolean trustedPack)
            throws IOException, InterruptedException {
        if (!allowTools && !trustedPack) {
            LOG.infof("Fonte SARIF '%s' executa um comando e foi ignorada: ative sentinel.audit.packs.allow-tools para permitir", spec.id());
            return List.of();
        }
        Path exe = resolveExecutable(spec.command().get(0));
        if (exe == null) {
            LOG.infof("Fonte SARIF '%s': executável '%s' não encontrado; ignorada", spec.id(), spec.command().get(0));
            return List.of();
        }
        List<String> files = List.of();
        if (!spec.appliesTo().isEmpty()) {
            files = matchingFiles(projectPath, spec.appliesTo());
            if (files.isEmpty()) {
                LOG.debugf("Fonte SARIF '%s': nenhum arquivo do tipo esperado no projeto; ignorada", spec.id());
                return List.of();
            }
        }
        Path output = Files.createTempFile("sarif-", ".json");
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(exe.toString());
            for (String arg : spec.command().subList(1, spec.command().size())) {
                if (arg.equals("{files}")) {
                    cmd.addAll(files);
                } else {
                    cmd.add(arg.replace("{project}", projectPath.toString()).replace("{output}", output.toString()));
                }
            }
            int code = ExternalTools.run(cmd, projectPath, Map.of(), spec.timeoutSeconds(), spec.stdout() ? output : null);
            if (code == -1) {
                LOG.warnf("Fonte SARIF '%s' excedeu %ds e foi encerrada", spec.id(), spec.timeoutSeconds());
                return List.of();
            }
            if (Files.size(output) == 0) {
                LOG.warnf("Fonte SARIF '%s' terminou (código %d) sem gerar o arquivo SARIF", spec.id(), code);
                return List.of();
            }
            if (Files.size(output) > MAX_SARIF_BYTES) {
                LOG.warnf("SARIF de '%s' ignorado: maior que %d MB", spec.id(), MAX_SARIF_BYTES / 1024 / 1024);
                return List.of();
            }
            return parse(Files.readString(output, StandardCharsets.UTF_8), spec, projectPath, run);
        } finally {
            Files.deleteIfExists(output);
        }
    }

    static final int MAX_MATCHED_FILES = 500;

    /** Arquivos do projeto (caminho relativo) que casam com algum glob; ignora pastas de build e links simbólicos. */
    static List<String> matchingFiles(Path projectPath, List<String> globs) throws IOException {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String g : globs) {
            // no glob do Java "/**/" exige ao menos uma pasta; gera também a variante com zero pastas
            java.util.Set<String> variants = new java.util.LinkedHashSet<>();
            variants.add(g);
            variants.add(g.replace("/**/", "/"));
            if (g.startsWith("**/")) {
                variants.add(g.substring(3));
                variants.add(g.substring(3).replace("/**/", "/"));
            }
            for (String v : variants) {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + v));
            }
        }
        try (Stream<Path> walk = Files.walk(projectPath)) {
            return walk.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p))
                    .map(projectPath::relativize)
                    .filter(rel -> !ExternalTools.inIgnoredDir(rel.toString()))
                    .filter(rel -> !rel.toString().startsWith("-"))          // nome de arquivo não pode virar opção da ferramenta
                    .filter(rel -> matchers.stream().anyMatch(m -> m.matches(rel)))
                    .map(rel -> rel.toString().replace('\\', '/'))
                    .sorted().limit(MAX_MATCHED_FILES).toList();
        }
    }

    private Path resolveExecutable(String name) {
        if (name.contains("/") || name.contains("\\")) {
            Path p = Path.of(name);
            return Files.isRegularFile(p) && Files.isExecutable(p) ? p : null;
        }
        return ExternalTools.find(name, pathEnv);
    }

    // ------------------------------------------------------------------ leitura do SARIF

    List<Finding> parse(String sarif, ToolSpec spec, Path projectPath, AuditRun run) {
        List<Finding> out = new ArrayList<>();
        if (sarif == null || sarif.isBlank()) {
            return out;
        }
        try {
            JsonNode runs = MAPPER.readTree(sarif).path("runs");
            for (JsonNode r : runs) {
                Map<String, JsonNode> rules = new HashMap<>();
                for (JsonNode rule : r.path("tool").path("driver").path("rules")) {
                    rules.put(rule.path("id").asText(""), rule);
                }
                for (JsonNode result : r.path("results")) {
                    if (out.size() >= MAX_FINDINGS_PER_TOOL) {
                        LOG.warnf("SARIF de '%s': mais de %d resultados, o restante foi ignorado", spec.id(), MAX_FINDINGS_PER_TOOL);
                        return out;
                    }
                    if (result.path("suppressions").isArray() && !result.path("suppressions").isEmpty()) {
                        continue;                                  // já suprimido na própria ferramenta
                    }
                    Finding f = toFinding(result, rules, spec, projectPath, run);
                    if (f != null) {
                        out.add(f);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Não foi possível interpretar o SARIF de '%s': %s", spec.id(), e.getMessage());
            return new ArrayList<>();
        }
        return out;
    }

    private Finding toFinding(JsonNode result, Map<String, JsonNode> rules, ToolSpec spec, Path projectPath, AuditRun run) {
        String ruleId = result.path("ruleId").asText(result.path("rule").path("id").asText(""));
        if (ruleId.isBlank()) {
            return null;
        }
        JsonNode rule = rules.get(ruleId);
        String message = result.path("message").path("text").asText(ruleId);
        JsonNode phys = result.path("locations").path(0).path("physicalLocation");

        Finding f = new Finding();
        f.setAuditRun(run);
        f.setToolSource(spec.name());
        f.setCategory(spec.category());
        f.setSeverity(severity(result, rule, spec));
        f.setRuleId(spec.id() + "-" + ruleId);
        f.setFilePath(relativeUri(projectPath, phys.path("artifactLocation").path("uri").asText("")));
        f.setLineNumber(phys.path("region").path("startLine").asInt(0));
        String title = message.lines().findFirst().orElse(ruleId);
        f.setTitle(title.length() > 500 ? title.substring(0, 497) + "..." : title);
        String help = rule == null ? "" : rule.path("helpUri").asText("");
        f.setDescription(message + (help.isBlank() ? "" : " Detalhes: " + help));
        String snippet = phys.path("region").path("snippet").path("text").asText("");
        if (!snippet.isBlank()) {
            f.setCodeSnippet(snippet);
        }
        return f;
    }

    /** Severidade: mapa do pacote por nível > {@code security-severity} (convenção do GitHub) > padrão por nível. */
    static Severity severity(JsonNode result, JsonNode rule, ToolSpec spec) {
        String level = result.path("level").asText(rule == null ? "warning"
                : rule.path("defaultConfiguration").path("level").asText("warning")).toLowerCase(java.util.Locale.ROOT);
        Severity mapped = spec.levelSeverity().get(level);
        if (mapped != null) {
            return mapped;
        }
        JsonNode sec = result.path("properties").path("security-severity");
        if (sec.isMissingNode() && rule != null) {
            sec = rule.path("properties").path("security-severity");
        }
        if (!sec.isMissingNode()) {
            double score = sec.isNumber() ? sec.asDouble() : parseDouble(sec.asText(""));
            if (score >= 9.0) {
                return Severity.CRITICAL;
            }
            if (score >= 7.0) {
                return Severity.HIGH;
            }
            if (score >= 4.0) {
                return Severity.MEDIUM;
            }
            if (score > 0.0) {
                return Severity.LOW;
            }
        }
        return switch (level) {
            case "error" -> Severity.HIGH;
            case "warning" -> Severity.MEDIUM;
            case "note" -> Severity.LOW;
            default -> Severity.INFO;
        };
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String relativeUri(Path root, String uri) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        String path = uri;
        if (path.startsWith("file://")) {                       // file:///abs/x  ou  file://host/abs/x
            path = path.substring("file://".length());
            if (!path.startsWith("/")) {
                path = path.substring(Math.max(path.indexOf('/'), 0));
            }
        } else if (path.startsWith("file:")) {                  // file:/abs/x (Checkstyle)
            path = path.substring("file:".length());
        }
        return ExternalTools.relativize(root, path);
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return true;
    }
}
