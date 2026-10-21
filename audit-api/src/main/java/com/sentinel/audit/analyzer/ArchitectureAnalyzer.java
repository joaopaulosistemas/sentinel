package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyzer que detecta violações arquiteturais e de observabilidade em projetos Java.
 * <p>
 * Regras implementadas:
 * <ul>
 *   <li>Acesso direto a repositórios em controllers (bypass da camada service);</li>
 *   <li>God Object: classe com mais de 500 linhas ou mais de 20 métodos públicos
 *       (getters, setters e {@code equals/hashCode/toString} não contam);</li>
 *   <li>Magic numbers: literais numéricos maiores que 100 fora de constantes (um finding por arquivo);</li>
 *   <li>Observabilidade: {@code System.out/err.print*} e {@code printStackTrace()} no lugar de um
 *       logger (um finding por arquivo e regra).</li>
 * </ul>
 */
@ApplicationScoped
public class ArchitectureAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(ArchitectureAnalyzer.class);
    private static final String ANALYZER_NAME = "Architecture";

    private static final int GOD_OBJECT_LINE_THRESHOLD   = 500;
    private static final int GOD_OBJECT_METHOD_THRESHOLD = 20;
    private static final int MAGIC_NUMBER_THRESHOLD      = 100;
    private static final int MAX_LINES_LISTED            = 5;
    private static final long MAX_FILE_BYTES             = 2L * 1024 * 1024;

    private static final Set<String> IGNORED_DIRS = Set.of(
            "target", "build", "dist", "node_modules", ".git", "test", "tests", "generated");

    /** Códigos HTTP e valores comuns que não são "mágicos". */
    private static final Set<Integer> COMMON_NUMBERS = Set.of(
            101, 200, 201, 202, 204, 301, 302, 304, 400, 401, 403, 404, 405, 409, 410, 422, 429,
            500, 501, 502, 503, 504, 1000, 1024);

    private static final Pattern CONTROLLER_ANNOTATION = Pattern.compile("@(RestController|Controller)\\b|@Path\\s*\\(");
    private static final Pattern REPOSITORY_IMPORT     = Pattern.compile("import\\s+[\\w.]+[Rr]epository;");
    private static final Pattern PUBLIC_METHOD         = Pattern.compile(
            "\\bpublic\\b[^;{=()]*?\\b(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\{");
    private static final Pattern ACCESSOR_NAME         = Pattern.compile("^(get|set|is)[A-Z].*|^(equals|hashCode|toString)$");
    private static final Pattern MAGIC_NUMBER          = Pattern.compile("(?<![\\w.])([1-9]\\d{2,})(?![\\w.])");
    private static final Pattern CONSTANT_DECLARATION  = Pattern.compile("\\bstatic\\b.+\\bfinal\\b|\\bfinal\\b.+\\bstatic\\b");
    private static final Pattern STRING_LITERAL        = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern SYSTEM_PRINT          = Pattern.compile("\\bSystem\\s*\\.\\s*(out|err)\\s*\\.\\s*print");
    private static final Pattern PRINT_STACK_TRACE     = Pattern.compile("\\.printStackTrace\\s*\\(\\s*\\)");

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();

        try {
            for (Path javaFile : findJavaFiles(projectPath)) {
                List<String> lines = readLines(javaFile);
                if (lines.isEmpty()) {
                    continue;
                }
                String content = String.join("\n", lines);
                String relativePath = projectPath.relativize(javaFile).toString();

                findings.addAll(detectControllerRepositoryAccess(relativePath, content, auditRun));
                findings.addAll(detectGodObject(relativePath, content, lines.size(), auditRun));
                findings.addAll(detectMagicNumbers(relativePath, lines, auditRun));
                findings.addAll(detectObservabilityIssues(relativePath, lines, auditRun));
            }
        } catch (IOException e) {
            LOG.warnf("Erro ao analisar arquitetura: %s", e.getMessage());
        }

        LOG.infof("Architecture Analyzer encontrou %d findings", findings.size());
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return stack == Stack.JAVA || stack == Stack.MIXED;
    }

    private List<Finding> detectControllerRepositoryAccess(String file, String content, AuditRun auditRun) {
        List<Finding> findings = new ArrayList<>();
        boolean isController = CONTROLLER_ANNOTATION.matcher(content).find();
        boolean importsRepository = REPOSITORY_IMPORT.matcher(content).find();

        if (isController && importsRepository) {
            findings.add(newFinding(auditRun, Category.ARCHITECTURE, Severity.HIGH,
                    "Controller acessando repositório diretamente",
                    "A classe Controller importa ou injeta um Repository diretamente, " +
                            "violando a separação entre camadas. Controllers devem acessar apenas Services.",
                    file, null, null, "arch-controller-bypass-service"));
        }
        return findings;
    }

    private List<Finding> detectGodObject(String file, String content, int lineCount, AuditRun auditRun) {
        List<Finding> findings = new ArrayList<>();

        Matcher methodMatcher = PUBLIC_METHOD.matcher(content);
        int publicMethodCount = 0;
        while (methodMatcher.find()) {
            if (!ACCESSOR_NAME.matcher(methodMatcher.group(1)).matches()) {
                publicMethodCount++;
            }
        }

        if (lineCount > GOD_OBJECT_LINE_THRESHOLD || publicMethodCount > GOD_OBJECT_METHOD_THRESHOLD) {
            findings.add(newFinding(auditRun, Category.ARCHITECTURE, Severity.MEDIUM,
                    "God Object detectado",
                    String.format(
                            "Classe com %d linhas e %d métodos públicos (sem contar getters/setters). " +
                                    "Limite recomendado: %d linhas / %d métodos. " +
                                    "Considere dividir em classes menores com responsabilidades únicas (SRP).",
                            lineCount, publicMethodCount, GOD_OBJECT_LINE_THRESHOLD, GOD_OBJECT_METHOD_THRESHOLD),
                    file, null, null, "arch-god-object"));
        }
        return findings;
    }

    /** Um finding por arquivo, listando as primeiras linhas, para não inundar o relatório. */
    private List<Finding> detectMagicNumbers(String file, List<String> lines, AuditRun auditRun) {
        List<Integer> hitLines = new ArrayList<>();
        List<Integer> hitValues = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String stripped = line.strip();
            if (stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*")
                    || stripped.startsWith("@") || CONSTANT_DECLARATION.matcher(line).find()) {
                continue;
            }
            String code = stripCommentAndStrings(line);
            Matcher m = MAGIC_NUMBER.matcher(code);
            while (m.find()) {
                int value;
                try {
                    value = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    continue; // literal grande demais para int: ignora
                }
                if (value > MAGIC_NUMBER_THRESHOLD && !COMMON_NUMBERS.contains(value)) {
                    hitLines.add(i + 1);
                    hitValues.add(value);
                    break; // uma ocorrência por linha basta
                }
            }
        }

        if (hitLines.isEmpty()) {
            return List.of();
        }
        int first = hitLines.get(0);
        return List.of(newFinding(auditRun, Category.ARCHITECTURE, Severity.LOW,
                "Magic numbers (" + hitLines.size() + ")",
                "Literais numéricos fora de constantes: " + describeHits(hitLines, hitValues) +
                        ". Extraia para constantes nomeadas para melhorar a legibilidade.",
                file, first, lines.get(first - 1).strip(), "arch-magic-number"));
    }

    private List<Finding> detectObservabilityIssues(String file, List<String> lines, AuditRun auditRun) {
        List<Finding> findings = new ArrayList<>();

        List<Integer> printLines = new ArrayList<>();
        List<Integer> stackTraceLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.strip().startsWith("//") || line.strip().startsWith("*")) {
                continue;
            }
            if (SYSTEM_PRINT.matcher(line).find()) {
                printLines.add(i + 1);
            }
            if (PRINT_STACK_TRACE.matcher(line).find()) {
                stackTraceLines.add(i + 1);
            }
        }

        if (!printLines.isEmpty()) {
            int first = printLines.get(0);
            findings.add(newFinding(auditRun, Category.OBSERVABILITY, Severity.LOW,
                    "System.out/err no lugar de logger (" + printLines.size() + ")",
                    "Saída direta no console não tem nível, formato nem destino configuráveis. " +
                            "Use um logger (ex.: SLF4J/JBoss Logging). Linhas: " + listLines(printLines) + ".",
                    file, first, lines.get(first - 1).strip(), "obs-system-out"));
        }
        if (!stackTraceLines.isEmpty()) {
            int first = stackTraceLines.get(0);
            findings.add(newFinding(auditRun, Category.OBSERVABILITY, Severity.MEDIUM,
                    "printStackTrace() em vez de log estruturado (" + stackTraceLines.size() + ")",
                    "printStackTrace() escreve no stderr sem contexto e pode vazar detalhes internos. " +
                            "Registre a exceção com um logger. Linhas: " + listLines(stackTraceLines) + ".",
                    file, first, lines.get(first - 1).strip(), "obs-print-stack-trace"));
        }
        return findings;
    }

    // ------------------------------------------------------------------ utilitários

    private static String stripCommentAndStrings(String line) {
        String noStrings = STRING_LITERAL.matcher(line).replaceAll("\"\"");
        int comment = noStrings.indexOf("//");
        return comment >= 0 ? noStrings.substring(0, comment) : noStrings;
    }

    private static String describeHits(List<Integer> lines, List<Integer> values) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(lines.size(), MAX_LINES_LISTED); i++) {
            parts.add(values.get(i) + " (linha " + lines.get(i) + ")");
        }
        String text = String.join(", ", parts);
        return lines.size() > MAX_LINES_LISTED ? text + " e mais " + (lines.size() - MAX_LINES_LISTED) : text;
    }

    private static String listLines(List<Integer> lines) {
        String text = lines.stream().limit(MAX_LINES_LISTED).map(String::valueOf).collect(Collectors.joining(", "));
        return lines.size() > MAX_LINES_LISTED ? text + " e mais " + (lines.size() - MAX_LINES_LISTED) : text;
    }

    private Finding newFinding(AuditRun auditRun, Category category, Severity severity, String title,
                               String description, String file, Integer line, String snippet, String ruleId) {
        Finding f = new Finding();
        f.setAuditRun(auditRun);
        f.setCategory(category);
        f.setSeverity(severity);
        f.setTitle(title);
        f.setDescription(description);
        f.setFilePath(file);
        f.setLineNumber(line);
        f.setCodeSnippet(snippet);
        f.setRuleId(ruleId);
        f.setToolSource(ANALYZER_NAME);
        return f;
    }

    private List<String> readLines(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return List.of();
            }
            try {
                return Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (CharacterCodingException e) {
                return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            }
        } catch (IOException e) {
            LOG.debugf("Não foi possível ler %s: %s", file.getFileName(), e.getMessage());
            return List.of();
        }
    }

    /** Lista arquivos .java, ignorando pastas de build/teste <b>dentro</b> do projeto. */
    private List<Path> findJavaFiles(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".java"))
                  .filter(Files::isRegularFile)
                  .filter(p -> !Files.isSymbolicLink(p))
                  .filter(p -> !isInIgnoredDirectory(root, p))
                  .forEach(result::add);
        }
        return result;
    }

    private boolean isInIgnoredDirectory(Path root, Path file) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            if (IGNORED_DIRS.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }
}
