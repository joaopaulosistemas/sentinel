package com.sentinel.audit.analyzer.evidence;

import com.sentinel.audit.analyzer.AnalyzerResult;
import com.sentinel.audit.analyzer.CodeAnalyzer;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Stack;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Analisa evidências geradas fora do Sentinel, sem tocar em banco nem aplicação em produção: o dump do esquema, as
 * estatísticas e os planos do Postgres, a cobertura de testes e o histograma de memória da JVM.
 * <p>
 * Os arquivos ficam em {@code .sentinel/evidence/} dentro do projeto auditado (o Sentinel copia para lá a pasta do
 * sistema em {@code SENTINEL_EVIDENCE_DIR}; veja {@code EvidenceStager}). O formato é reconhecido pelo conteúdo e
 * pelo nome, e o que não for reconhecido é ignorado.
 */
@ApplicationScoped
public class EvidenceAnalyzer implements CodeAnalyzer {

    private static final double DEFAULT_MIN_LINE_COVERAGE = 60;
    private static final long BYTES_PER_MB = 1024L * 1024;
    private static final int PACKAGE_SCAN_DEPTH = 12;
    private static final int PACKAGE_SCAN_MAX_FILES = 5000;
    /** Quantas linhas do início de cada arquivo .java são lidas para achar o {@code package}. */
    private static final int PACKAGE_SCAN_HEAD_LINES = 40;
    /** Pacote raiz da aplicação: os três primeiros segmentos (br.com.empresa). */
    private static final int ROOT_PACKAGE_SEGMENTS = 3;
    private static final int MIN_SEGMENTS_FOR_COUNTRY_TYPE = 4;


    private static final Logger LOG = Logger.getLogger(EvidenceAnalyzer.class);
    public static final String NAME = "Evidências";
    public static final String DIR = ".sentinel/evidence";
    static final long MAX_FILE_BYTES = 200L * 1024 * 1024;
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    @ConfigProperty(name = "sentinel.audit.evidence.min-line-coverage", defaultValue = "60")
    double minLineCoverage = DEFAULT_MIN_LINE_COVERAGE;

    /** Há algo para analisar em {@code .sentinel/evidence}? Sem evidências o analisador nem entra na auditoria. */
    public boolean hasEvidence(Path projectPath) {
        Path dir = projectPath.resolve(DIR);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        Path dir = projectPath.resolve(DIR);
        if (!Files.isDirectory(dir)) {
            return new AnalyzerResult(findings, NAME, Duration.between(start, Instant.now()));
        }
        LazyPackages packages = new LazyPackages(projectPath);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String rel = DIR + "/" + file.getFileName();
                try {
                    findings.addAll(checkEvidence(file, rel, auditRun, packages));
                } catch (IOException | RuntimeException e) {
                    LOG.warnf("Não foi possível analisar a evidência %s: %s", rel, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.warnf("Não foi possível listar %s: %s", DIR, e.getMessage());
        }
        LOG.infof("Evidências: %d findings", findings.size());
        return new AnalyzerResult(findings, NAME, Duration.between(start, Instant.now()));
    }

    /** Escolhe a verificação pelo tipo do arquivo de evidência (SQL, CSV, plano JSON, cobertura, histograma de heap). */
    private List<Finding> checkEvidence(Path file, String rel, AuditRun auditRun, LazyPackages packages) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) {
            LOG.warnf("Evidência %s ignorada: maior que %d MB", rel, MAX_FILE_BYTES / BYTES_PER_MB);
            return List.of();
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (name.endsWith(".sql")) {
            return SchemaChecks.run(text, rel, auditRun);
        }
        if (name.endsWith(".csv")) {
            return csv(text, rel, auditRun);
        }
        if (name.endsWith(".json")) {
            return PlanChecks.run(text, rel, auditRun);
        }
        if (name.endsWith(".xml")) {
            return CoverageChecks.run(CoverageChecks.jacocoXml(text), rel, minLineCoverage, auditRun);
        }
        if (name.endsWith(".info") || name.startsWith("lcov")) {
            return CoverageChecks.run(CoverageChecks.lcov(text), rel, minLineCoverage, auditRun);
        }
        if (name.endsWith(".txt") || name.endsWith(".histo")) {
            return HeapChecks.run(HeapChecks.parse(text), packages.get(), rel, auditRun);
        }
        return List.of();
    }

    /** Os pacotes da aplicação só são levantados (varrendo o projeto) se algum histograma de heap aparecer. */
    private static final class LazyPackages {
        private final Path projectPath;
        private Set<String> value;

        LazyPackages(Path projectPath) {
            this.projectPath = projectPath;
        }

        Set<String> get() {
            if (value == null) {
                value = applicationPackages(projectPath);
            }
            return value;
        }
    }

    private List<Finding> csv(String text, String rel, AuditRun run) {
        List<Csv.Row> rows = Csv.parse(text);
        if (!rows.isEmpty() && rows.get(0).has("line_missed")) {
            return CoverageChecks.run(CoverageChecks.jacocoCsv(rows), rel, minLineCoverage, run);
        }
        return switch (PgStatChecks.detect(rows)) {
            case STATEMENTS -> PgStatChecks.statements(rows, rel, run);
            case TABLES -> PgStatChecks.tables(rows, rel, run);
            case INDEXES -> PgStatChecks.indexes(rows, rel, run);
            case UNKNOWN -> List.of();
        };
    }

    /** Pacotes raiz das classes do projeto (dois primeiros níveis do {@code package}), para separar código próprio de biblioteca. */
    static Set<String> applicationPackages(Path projectPath) {
        Set<String> roots = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(projectPath, PACKAGE_SCAN_DEPTH)) {
            files.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/node_modules/")
                            && !p.toString().contains("/.sentinel/"))
                    .limit(PACKAGE_SCAN_MAX_FILES).forEach(p -> {
                        try (Stream<String> lines = Files.lines(p, StandardCharsets.UTF_8)) {
                            lines.limit(PACKAGE_SCAN_HEAD_LINES).map(PACKAGE::matcher).filter(Matcher::find).findFirst().ifPresent(m -> {
                                String[] parts = m.group(1).split("\\.");
                                // br.com.empresa.app -> "br.com.empresa"; com.empresa.app -> "com.empresa.app"
                                boolean paisComTipo = parts.length >= MIN_SEGMENTS_FOR_COUNTRY_TYPE && parts[0].length() == 2
                                        && Set.of("com", "org", "gov", "net", "edu").contains(parts[1]);
                                int n = Math.min(parts.length, ROOT_PACKAGE_SEGMENTS);
                                roots.add(String.join(".", java.util.Arrays.copyOf(parts, n)));
                            });
                        } catch (IOException | RuntimeException ignored) {
                            // arquivo ilegível: segue
                        }
                    });
        } catch (IOException | RuntimeException ignored) {
            // sem pacotes conhecidos: só o histograma geral é gerado
        }
        return roots;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return true;
    }
}
