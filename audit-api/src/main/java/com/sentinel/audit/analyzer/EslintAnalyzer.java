package com.sentinel.audit.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lint de TypeScript/JavaScript com o ESLint.
 * <p>
 * Só atua quando o executável {@code eslint} está no PATH <b>e</b> existe o arquivo de configuração indicado em
 * {@code sentinel.audit.eslint.config} (ele precisa estar ao lado de um {@code node_modules} com {@code eslint} e
 * {@code typescript-eslint}). Roda com {@code --no-config-lookup}: a configuração do repositório auditado
 * <b>nunca é carregada</b>, porque um {@code eslint.config.js} é código e seria executado no servidor de auditoria.
 */
@ApplicationScoped
public class EslintAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(EslintAnalyzer.class);
    private static final String ANALYZER_NAME = "ESLint";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Regras que apontam falha de segurança (as demais são qualidade). */
    private static final Set<String> SECURITY_RULES = Set.of("no-eval", "no-implied-eval", "no-new-func", "no-script-url", "react/jsx-no-script-url", "react/jsx-no-target-blank");

    @ConfigProperty(name = "sentinel.audit.eslint.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "sentinel.audit.eslint.config", defaultValue = "/opt/eslint/eslint.config.mjs")
    String configPath;

    @ConfigProperty(name = "sentinel.audit.eslint.timeout-seconds", defaultValue = "600")
    long timeoutSeconds;

    @ConfigProperty(name = "sentinel.audit.eslint.max-findings", defaultValue = "500")
    int maxFindings;

    /** Substituível em testes. */
    String pathEnv = System.getenv("PATH");

    @Override
    public boolean isAvailable() {
        return enabled && ExternalTools.find("eslint", pathEnv) != null && Files.isRegularFile(Path.of(configPath));
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        Path exe = enabled ? ExternalTools.find("eslint", pathEnv) : null;
        if (exe == null || !Files.isRegularFile(Path.of(configPath))) {
            LOG.warn("ESLint (ou sua configuração) indisponível; lint TypeScript por esta ferramenta ignorado");
            return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
        }
        Path report = null;
        Path stderr = null;
        String partial = null;
        try {
            stderr = Files.createTempFile("eslint-err-", ".txt");
            report = Files.createTempFile("eslint-", ".json");
            List<String> cmd = new ArrayList<>(List.of(exe.toString(), "--no-config-lookup", "-c", configPath,
                    "-f", "json", "-o", report.toString(), "--no-warn-ignored"));
            for (String dir : CleanCodeAnalyzer.IGNORED_DIRS) {
                cmd.add("--ignore-pattern");
                cmd.add("**/" + dir + "/**");
            }
            cmd.addAll(List.of("--ignore-pattern", "**/*.d.ts", "--ignore-pattern", "**/*.min.js", "."));
            Map<String, String> env = usaAngular(projectPath) ? Map.of() : Map.of("SENTINEL_ESLINT_ANGULAR", "0");
            int code = ExternalTools.run(cmd, projectPath, env, timeoutSeconds, null, stderr);
            if (code == -1 || code > 1) {                // 1 = há problemas (normal); 2 = erro de execução
                partial = ExternalTools.falha(code, timeoutSeconds, stderr);
                LOG.warnf("ESLint %s", partial);
            } else {
                findings = parse(Files.readString(report, StandardCharsets.UTF_8), projectPath, auditRun);
                LOG.infof("ESLint encontrou %d problemas", findings.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            partial = "execução interrompida";
        } catch (Exception e) {
            LOG.errorf("Erro ao executar o ESLint: %s", e.getMessage());
            partial = "erro ao executar: " + e.getMessage();
        } finally {
            ExternalTools.deleteQuietly(stderr);
            if (report != null) {
                try {
                    Files.deleteIfExists(report);
                } catch (Exception e) {
                    LOG.warnf("Não foi possível apagar o relatório temporário do ESLint: %s", e.getMessage());
                }
            }
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()), partial);
    }

    /** Algum package.json (fora de pastas ignoradas) menciona {@code @angular/}: só então valem as regras do Angular. */
    static boolean usaAngular(Path projectPath) {
        try (var paths = Files.walk(projectPath, 6)) {
            return paths.filter(p -> p.getFileName() != null && p.getFileName().toString().equals("package.json"))
                    .filter(p -> !ExternalTools.inIgnoredDir(ExternalTools.relativize(projectPath, p.toString())))
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8).contains("\"@angular/");
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (Exception e) {
            return true;                                 // na dúvida, mantém o comportamento anterior
        }
    }

    /** Regras de segurança: as do núcleo listadas e todas dos plugins security/ e no-unsanitized/. */
    static boolean isSecurity(String rule) {
        return SECURITY_RULES.contains(rule) || rule.startsWith("security/") || rule.startsWith("no-unsanitized/");
    }

    List<Finding> parse(String json, Path projectPath, AuditRun auditRun) {
        List<Finding> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                return out;
            }
            for (JsonNode file : root) {
                String rel = ExternalTools.relativize(projectPath, file.path("filePath").asText(""));
                if (ExternalTools.inIgnoredDir(rel)) {
                    continue;
                }
                for (JsonNode m : file.path("messages")) {
                    JsonNode ruleNode = m.path("ruleId");
                    if (ruleNode.isNull() || ruleNode.isMissingNode() || ruleNode.asText("").isBlank()) {
                        continue;                        // erro de sintaxe do arquivo, não é regra de lint
                    }
                    String rule = ruleNode.asText();
                    String text = m.path("message").asText(rule);
                    Finding f = new Finding();
                    f.setAuditRun(auditRun);
                    f.setToolSource(ANALYZER_NAME);
                    f.setCategory(isSecurity(rule) ? Category.SECURITY : Category.QUALITY);
                    f.setSeverity(m.path("severity").asInt(1) >= 2 ? Severity.MEDIUM : Severity.LOW);
                    f.setRuleId("eslint-" + rule);
                    f.setFilePath(rel);
                    f.setLineNumber(m.path("line").asInt(0));
                    f.setTitle("ESLint " + rule + ": " + text);
                    f.setDescription(text + " (regra " + rule + ")");
                    out.add(f);
                }
            }
        } catch (Exception e) {
            LOG.warnf("Não foi possível interpretar o relatório do ESLint: %s", e.getMessage());
            return new ArrayList<>();
        }
        if (maxFindings > 0 && out.size() > maxFindings) {
            out.sort(Comparator.comparing(Finding::getSeverity));
            return new ArrayList<>(out.subList(0, maxFindings));
        }
        return out;
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
