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

/**
 * Lint de código Java com o PMD (boas práticas, propensão a erro e design).
 * <p>
 * Só atua quando o executável {@code pmd} (PMD 7+) está no PATH. Usa o conjunto de regras embutido
 * ({@link #DEFAULT_RULESET}), que deixa de fora o que já é coberto por outro analisador
 * (complexidade cognitiva pelo {@link CleanCodeAnalyzer} e pelo ESLint, classe grande pelo detector de God Object)
 * e as regras de estilo que mais geram ruído (literal primeiro em {@code equals}, literal em {@code if},
 * método com o mesmo nome do campo, varargs; na auditoria #68 somavam 190 achados) e o
 * {@code ReturnEmptyCollectionRatherThanNull}, que também vale para {@code byte[]} (19 de 20 achados em RuoYi-Vue,
 * onde {@code null} é o sinal de "entrada inválida" de decodificadores) e é coberto para coleções pela regra Semgrep
 * {@code sentinel-java-retorna-null-colecao}; para usar outro, aponte
 * {@code sentinel.audit.pmd.ruleset} para um XML de ruleset.
 */
@ApplicationScoped
public class PmdAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(PmdAnalyzer.class);
    private static final String ANALYZER_NAME = "PMD";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String DEFAULT_RULESET = """
            <?xml version="1.0"?>
            <ruleset name="sentinel" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
              <description>Boas praticas Java (Sentinel Audit)</description>
              <rule ref="category/java/bestpractices.xml">
                <exclude name="GuardLogStatement"/>
                <exclude name="UnitTestShouldIncludeAssert"/>
                <exclude name="LiteralsFirstInComparisons"/>
                <exclude name="UseVarargs"/>
              </rule>
              <rule ref="category/java/errorprone.xml">
                <exclude name="AvoidDuplicateLiterals"/>
                <exclude name="MissingSerialVersionUID"/>
                <exclude name="AvoidLiteralsInIfCondition"/>
                <exclude name="AvoidFieldNameMatchingMethodName"/>
                <exclude name="ReturnEmptyCollectionRatherThanNull"/>
              </rule>
              <rule ref="category/java/design.xml">
                <exclude name="LawOfDemeter"/>
                <exclude name="LoosePackageCoupling"/>
                <exclude name="AvoidCatchingGenericException"/>
                <exclude name="CyclomaticComplexity"/>
                <exclude name="NPathComplexity"/>
                <exclude name="ExcessiveParameterList"/>
                <exclude name="NcssCount"/>
                <exclude name="TooManyMethods"/>
                <exclude name="ExcessiveImports"/>
                <exclude name="DataClass"/>
                <exclude name="UseUtilityClass"/>
                <exclude name="SignatureDeclareThrowsException"/>
                <exclude name="CognitiveComplexity"/>
                <exclude name="GodClass"/>
                <exclude name="UseObjectForClearerAPI"/>
              </rule>
              <rule ref="category/java/security.xml"/>
            </ruleset>
            """;

    @ConfigProperty(name = "sentinel.audit.pmd.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "sentinel.audit.pmd.timeout-seconds", defaultValue = "600")
    long timeoutSeconds;

    @ConfigProperty(name = "sentinel.audit.pmd.ruleset")
    java.util.Optional<String> rulesetPath = java.util.Optional.empty();

    @ConfigProperty(name = "sentinel.audit.pmd.max-findings", defaultValue = "500")
    int maxFindings;

    /** Substituível em testes. */
    String pathEnv = System.getenv("PATH");

    @Override
    public boolean isAvailable() {
        return enabled && ExternalTools.find("pmd", pathEnv) != null;
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        Path exe = enabled ? ExternalTools.find("pmd", pathEnv) : null;
        if (exe == null) {
            LOG.warn("PMD indisponível; lint Java por esta ferramenta ignorado");
            return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
        }
        Path report = null;
        Path tempRuleset = null;
        Path stderr = null;
        String partial = null;
        try {
            stderr = Files.createTempFile("pmd-err-", ".txt");
            report = Files.createTempFile("pmd-", ".json");
            String ruleset = rulesetPath.orElse(null);
            if (ruleset == null || ruleset.isBlank() || !Files.isRegularFile(Path.of(ruleset))) {
                tempRuleset = Files.createTempFile("pmd-ruleset-", ".xml");
                Files.writeString(tempRuleset, DEFAULT_RULESET, StandardCharsets.UTF_8);
                ruleset = tempRuleset.toString();
            }
            List<String> cmd = List.of(exe.toString(), "check",
                    "--dir", projectPath.toString(), "--rulesets", ruleset,
                    "--format", "json", "--report-file", report.toString(),
                    "--no-cache", "--no-progress", "--no-fail-on-violation");
            int code = ExternalTools.run(cmd, null, Map.of(), timeoutSeconds, null, stderr);
            if (code != 0) {
                partial = ExternalTools.falha(code, timeoutSeconds, stderr);
                LOG.warnf("PMD %s", partial);
            } else {
                findings = parse(Files.readString(report, StandardCharsets.UTF_8), projectPath, auditRun);
                LOG.infof("PMD encontrou %d problemas", findings.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            partial = "execução interrompida";
        } catch (Exception e) {
            LOG.errorf("Erro ao executar o PMD: %s", e.getMessage());
            partial = "erro ao executar: " + e.getMessage();
        } finally {
            ExternalTools.deleteQuietly(stderr);
            deleteQuietly(report);
            deleteQuietly(tempRuleset);
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()), partial);
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception e) {
                LOG.warnf("Não foi possível apagar arquivo temporário do PMD: %s", e.getMessage());
            }
        }
    }

    List<Finding> parse(String json, Path projectPath, AuditRun auditRun) {
        List<Finding> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode files = MAPPER.readTree(json).path("files");
            for (JsonNode file : files) {
                String rel = ExternalTools.relativize(projectPath, file.path("filename").asText(""));
                if (ExternalTools.inIgnoredDir(rel)) {
                    continue;
                }
                for (JsonNode v : file.path("violations")) {
                    String rule = v.path("rule").asText("");
                    if (rule.isBlank()) {
                        continue;
                    }
                    String ruleset = v.path("ruleset").asText("");
                    String text = v.path("description").asText(rule);
                    Finding f = new Finding();
                    f.setAuditRun(auditRun);
                    f.setToolSource(ANALYZER_NAME);
                    f.setCategory(category(ruleset));
                    f.setSeverity(severity(v.path("priority").asInt(3), ruleset));
                    f.setRuleId("pmd-" + rule);
                    f.setFilePath(rel);
                    f.setLineNumber(v.path("beginline").asInt(0));
                    f.setTitle("PMD " + rule + ": " + text);
                    String url = v.path("externalInfoUrl").asText("");
                    f.setDescription(text + (url.isBlank() ? "" : " Detalhes: " + url));
                    out.add(f);
                }
            }
        } catch (Exception e) {
            LOG.warnf("Não foi possível interpretar o relatório do PMD: %s", e.getMessage());
            return new ArrayList<>();
        }
        if (maxFindings > 0 && out.size() > maxFindings) {
            out.sort(Comparator.comparing(Finding::getSeverity));      // mais graves primeiro
            return new ArrayList<>(out.subList(0, maxFindings));
        }
        return out;
    }

    /** Prioridade do PMD (1 = mais grave); regras de propensão a erro e de segurança sobem um nível. */
    static Severity severity(int priority, String ruleset) {
        Severity base = switch (priority) {
            case 1 -> Severity.HIGH;
            case 2 -> Severity.MEDIUM;
            case 3 -> Severity.LOW;
            default -> Severity.INFO;
        };
        boolean risky = "Error Prone".equalsIgnoreCase(ruleset) || "Security".equalsIgnoreCase(ruleset);
        if (risky && base.ordinal() > Severity.HIGH.ordinal()) {
            return Severity.values()[base.ordinal() - 1];
        }
        return base;
    }

    static Category category(String ruleset) {
        if ("Security".equalsIgnoreCase(ruleset)) {
            return Category.SECURITY;
        }
        if ("Design".equalsIgnoreCase(ruleset)) {
            return Category.ARCHITECTURE;
        }
        return Category.QUALITY;
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
