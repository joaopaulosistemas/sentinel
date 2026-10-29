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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** Prioridade do PMD assumida quando o relatório não informa. */
    private static final int DEFAULT_PRIORITY = 3;
    private static final int PRIORITY_HIGH = 1;
    private static final int PRIORITY_MEDIUM = 2;
    private static final int PRIORITY_LOW = 3;


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
        Path exe = enabled ? ExternalTools.find("pmd", pathEnv) : null;
        if (exe == null) {
            return ToolRun.unavailable(ANALYZER_NAME, "PMD indisponível; lint Java por esta ferramenta ignorado", LOG);
        }
        return ToolRun.run(ANALYZER_NAME, "pmd", LOG, (report, stderr) -> {
            Path tempRuleset = null;
            try {
                String ruleset = rulesetPath.orElse(null);
                if (!isUsableFile(ruleset)) {
                    tempRuleset = Files.createTempFile("pmd-ruleset-", ".xml");
                    Files.writeString(tempRuleset, DEFAULT_RULESET, StandardCharsets.UTF_8);
                    ruleset = tempRuleset.toString();
                }
                return execute(exe, projectPath, auditRun, ruleset, report, stderr);
            } finally {
                deleteQuietly(tempRuleset);
            }
        });
    }

    private ToolRun.Outcome execute(Path exe, Path projectPath, AuditRun auditRun, String ruleset, Path report,
                                    Path stderr) throws IOException, InterruptedException {
        int code = ExternalTools.runTo(command(exe, projectPath, ruleset, report), null, Map.of(), timeoutSeconds,
                new ExternalTools.Output(null, stderr));
        if (code != 0 && code != EXIT_RECOVERABLE_ERRORS) {
            String partial = ExternalTools.falha(code, timeoutSeconds, stderr);
            LOG.warnf("PMD %s", partial);
            return new ToolRun.Outcome(new ArrayList<>(), partial);
        }
        String json = Files.readString(report, StandardCharsets.UTF_8);
        List<Finding> found = parse(json, projectPath, auditRun);
        LOG.infof("PMD encontrou %d problemas", found.size());
        String partial = null;
        if (code == EXIT_RECOVERABLE_ERRORS) {
            // Código 5: o PMD não conseguiu ler/analisar alguns arquivos (sintaxe nova, arquivo gerado etc.),
            // mas o relatório com o resto está completo. Aproveita os achados e avisa em vez de descartar tudo.
            partial = recoverableNote(json);
            LOG.warnf("PMD %s", partial);
        }
        return new ToolRun.Outcome(found, partial);
    }

    private static boolean isUsableFile(String path) {
        return path != null && !path.isBlank() && Files.isRegularFile(Path.of(path));
    }

    private static List<String> command(Path exe, Path projectPath, String ruleset, Path report) {
        return List.of(exe.toString(), "check",
                "--dir", projectPath.toString(), "--rulesets", ruleset,
                "--format", "json", "--report-file", report.toString(),
                "--no-cache", "--no-progress", "--no-fail-on-violation");
    }

    private static String recoverableNote(String json) {
        int n = processingErrors(json);
        return "o PMD não conseguiu analisar " + (n > 0 ? n + " arquivo(s)" : "alguns arquivos")
                + "; os demais foram analisados";
    }

    /** Código de saída 5 do PMD 7: houve erros recuperáveis (arquivo não analisável), mas o relatório foi gravado. */
    static final int EXIT_RECOVERABLE_ERRORS = 5;

    /** Quantidade de arquivos com erro de processamento no relatório JSON do PMD (0 se ilegível). */
    static int processingErrors(String json) {
        try {
            return MAPPER.readTree(json).path("processingErrors").size();
        } catch (Exception e) {
            return 0;
        }
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
                collectFile(file, projectPath, auditRun, out);
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

    private static void collectFile(JsonNode file, Path projectPath, AuditRun auditRun, List<Finding> out) {
        String rel = ExternalTools.relativize(projectPath, file.path("filename").asText(""));
        if (ExternalTools.inIgnoredDir(rel)) {
            return;
        }
        for (JsonNode v : file.path("violations")) {
            Finding f = toFinding(v, rel, auditRun);
            if (f != null) {
                out.add(f);
            }
        }
    }

    /** Nulo para violação sem nome de regra. */
    private static Finding toFinding(JsonNode v, String rel, AuditRun auditRun) {
        String rule = v.path("rule").asText("");
        if (rule.isBlank()) {
            return null;
        }
        String ruleset = v.path("ruleset").asText("");
        String text = v.path("description").asText(rule);
        Finding f = new Finding();
        f.setAuditRun(auditRun);
        f.setToolSource(ANALYZER_NAME);
        f.setCategory(category(ruleset));
        f.setSeverity(severity(v.path("priority").asInt(DEFAULT_PRIORITY), ruleset));
        f.setRuleId("pmd-" + rule);
        f.setFilePath(rel);
        f.setLineNumber(v.path("beginline").asInt(0));
        f.setTitle("PMD " + rule + ": " + text);
        String url = v.path("externalInfoUrl").asText("");
        f.setDescription(text + (url.isBlank() ? "" : " Detalhes: " + url));
        return f;
    }

    /** Prioridade do PMD (1 = mais grave); regras de propensão a erro e de segurança sobem um nível. */
    static Severity severity(int priority, String ruleset) {
        Severity base = switch (priority) {
            case PRIORITY_HIGH -> Severity.HIGH;
            case PRIORITY_MEDIUM -> Severity.MEDIUM;
            case PRIORITY_LOW -> Severity.LOW;
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
