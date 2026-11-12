package com.sentinel.audit.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.util.function.Predicate;

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

    /** O que o {@code .sentinel.yml} exclui: erro do PMD nesses arquivos não torna a auditoria parcial. */
    @Inject
    ProjectExclusions exclusions = ProjectExclusions.NONE;

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
                return execute(new Target(exe, projectPath, auditRun), ruleset, report, stderr);
            } finally {
                deleteQuietly(tempRuleset);
            }
        });
    }

    /** O que o PMD analisa: o executável, a pasta do projeto e a auditoria a que os achados pertencem. */
    private record Target(Path exe, Path projectPath, AuditRun auditRun) { }

    private ToolRun.Outcome execute(Target target, String ruleset, Path report, Path stderr)
            throws IOException, InterruptedException {
        Path projectPath = target.projectPath();
        AuditRun auditRun = target.auditRun();
        int code = ExternalTools.runTo(command(target.exe(), projectPath, ruleset, report), null, Map.of(), timeoutSeconds,
                new ExternalTools.Output(null, stderr));
        if (code != 0 && code != EXIT_RECOVERABLE_ERRORS) {
            String partial = ExternalTools.falha(code, timeoutSeconds, stderr);
            LOG.warnf("PMD %s", partial);
            // Falha do PMD (ex.: exceção numa regra) depois de gravar o relatório: aproveita o que foi achado e
            // avisa que o resultado é parcial, em vez de mostrar "0 achados".
            List<Finding> salvos = readableFindings(report, projectPath, auditRun);
            if (!salvos.isEmpty()) {
                LOG.infof("PMD: %d problemas aproveitados do relatório parcial", salvos.size());
                return new ToolRun.Outcome(salvos, partial + " (os achados já gravados foram aproveitados)");
            }
            return new ToolRun.Outcome(new ArrayList<>(), partial);
        }
        String json = Files.readString(report, StandardCharsets.UTF_8);
        List<Finding> found = parse(json, projectPath, auditRun);
        LOG.infof("PMD encontrou %d problemas", found.size());
        String partial = null;
        if (code == EXIT_RECOVERABLE_ERRORS) {
            // Código 5: o PMD não conseguiu ler/analisar alguns arquivos (sintaxe nova, arquivo gerado etc.),
            // mas o relatório com o resto está completo. Aproveita os achados e avisa em vez de descartar tudo.
            partial = recoverableNote(json, projectPath, exclusions.forProject(projectPath));
            if (partial != null) {
                LOG.warnf("PMD %s", partial);
            }
        }
        return new ToolRun.Outcome(found, partial);
    }

    private List<Finding> readableFindings(Path report, Path projectPath, AuditRun auditRun) {
        try {
            if (!Files.isRegularFile(report)) {
                return new ArrayList<>();
            }
            String json = Files.readString(report, StandardCharsets.UTF_8);
            return hasReadableReport(json) ? parse(json, projectPath, auditRun) : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** O JSON do PMD está inteiro e traz a lista {@code files}: dá para aproveitar mesmo se o PMD terminou com erro. */
    static boolean hasReadableReport(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            return MAPPER.readTree(json).path("files").isArray();
        } catch (Exception e) {
            return false;
        }
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

    /** Quantos arquivos o aviso cita pelo nome; o resto vira "e mais N". */
    private static final int NOTE_FILES = 3;

    /**
     * Aviso de cobertura parcial: cita os arquivos que o PMD não leu. Erros em arquivos que o projeto exclui
     * ({@code .sentinel.yml}) não contam; se todos forem desses, devolve {@code null} (nada ficou sem análise).
     */
    static String recoverableNote(String json, Path projectPath, Predicate<String> excluded) {
        java.util.Optional<List<String>> listed = unreadFiles(json, projectPath, excluded);
        if (listed.isEmpty()) {
            return "o PMD não conseguiu analisar alguns arquivos; os demais foram analisados";
        }
        List<String> unread = listed.get();
        if (unread.isEmpty()) {
            return null;
        }
        String shown = String.join(", ", unread.subList(0, Math.min(NOTE_FILES, unread.size())));
        String more = unread.size() > NOTE_FILES ? " e mais " + (unread.size() - NOTE_FILES) : "";
        return "o PMD não conseguiu analisar " + unread.size() + " arquivo(s): " + shown + more
                + "; os demais foram analisados";
    }

    /** "caminho (TipoDoErro)" de cada erro de processamento fora dos excluídos; vazio se o JSON não lista nenhum (ou não lê). */
    private static java.util.Optional<List<String>> unreadFiles(String json, Path projectPath, Predicate<String> excluded) {
        try {
            JsonNode errors = MAPPER.readTree(json).path("processingErrors");
            if (errors.isEmpty()) {
                return java.util.Optional.empty();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode e : errors) {
                String file = ExternalTools.relativize(projectPath, e.path("filename").asText(""));
                if (!excluded.test(file)) {
                    out.add(file + " (" + errorType(e.path("message").asText("")) + ")");
                }
            }
            return java.util.Optional.of(out);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    /** "ParseException: ..." vira "ParseException"; mensagem sem esse formato vira "erro de leitura". */
    private static String errorType(String message) {
        int colon = message.indexOf(':');
        String type = colon > 0 ? message.substring(0, colon).trim() : "";
        return type.isEmpty() || type.contains(" ") ? "erro de leitura" : type;
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

    /**
     * Prioridade do PMD (1 = mais grave). Regras de propensão a erro e de segurança sobem um nível, mas HIGH é reservado
     * ao ruleset Security: a prioridade 1 do PMD vale também para regras de estilo e design (ex.:
     * ClassWithOnlyPrivateConstructorsShouldBeFinal, AvoidThrowingRawExceptionTypes), e como HIGH elas pesavam na nota
     * como falha grave (revisão em {@code docs/evolucao/precisao.md}). Fora de Security o teto é MEDIUM.
     */
    static Severity severity(int priority, String ruleset) {
        Severity base = switch (priority) {
            case PRIORITY_HIGH -> Severity.HIGH;
            case PRIORITY_MEDIUM -> Severity.MEDIUM;
            case PRIORITY_LOW -> Severity.LOW;
            default -> Severity.INFO;
        };
        boolean security = "Security".equalsIgnoreCase(ruleset);
        boolean risky = security || "Error Prone".equalsIgnoreCase(ruleset);
        Severity bumped = risky && base.ordinal() > Severity.HIGH.ordinal() ? Severity.values()[base.ordinal() - 1] : base;
        return !security && bumped.ordinal() < Severity.MEDIUM.ordinal() ? Severity.MEDIUM : bumped;
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
