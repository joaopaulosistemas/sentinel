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

    private static final Logger LOG = Logger.getLogger(EvidenceAnalyzer.class);
    public static final String NAME = "Evidências";
    public static final String DIR = ".sentinel/evidence";
    static final long MAX_FILE_BYTES = 200L * 1024 * 1024;
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    @ConfigProperty(name = "sentinel.audit.evidence.min-line-coverage", defaultValue = "60")
    double minLineCoverage = 60;

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
        Set<String> appPackages = null;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String rel = DIR + "/" + file.getFileName();
                try {
                    if (Files.size(file) > MAX_FILE_BYTES) {
                        LOG.warnf("Evidência %s ignorada: maior que %d MB", rel, MAX_FILE_BYTES / 1024 / 1024);
                        continue;
                    }
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    if (name.endsWith(".sql")) {
                        findings.addAll(SchemaChecks.run(text, rel, auditRun));
                    } else if (name.endsWith(".csv")) {
                        findings.addAll(csv(text, rel, auditRun));
                    } else if (name.endsWith(".json")) {
                        findings.addAll(PlanChecks.run(text, rel, auditRun));
                    } else if (name.endsWith(".xml")) {
                        findings.addAll(CoverageChecks.run(CoverageChecks.jacocoXml(text), rel, minLineCoverage, auditRun));
                    } else if (name.endsWith(".info") || name.startsWith("lcov")) {
                        findings.addAll(CoverageChecks.run(CoverageChecks.lcov(text), rel, minLineCoverage, auditRun));
                    } else if (name.endsWith(".txt") || name.endsWith(".histo")) {
                        if (appPackages == null) {
                            appPackages = applicationPackages(projectPath);
                        }
                        findings.addAll(HeapChecks.run(HeapChecks.parse(text), appPackages, rel, auditRun));
                    }
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
        try (Stream<Path> files = Files.walk(projectPath, 12)) {
            files.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/node_modules/")
                            && !p.toString().contains("/.sentinel/"))
                    .limit(5000).forEach(p -> {
                        try (Stream<String> lines = Files.lines(p, StandardCharsets.UTF_8)) {
                            lines.limit(40).map(PACKAGE::matcher).filter(Matcher::find).findFirst().ifPresent(m -> {
                                String[] parts = m.group(1).split("\\.");
                                // br.com.empresa.app -> "br.com.empresa"; com.empresa.app -> "com.empresa.app"
                                boolean paisComTipo = parts.length >= 4 && parts[0].length() == 2
                                        && Set.of("com", "org", "gov", "net", "edu").contains(parts[1]);
                                int n = Math.min(parts.length, paisComTipo ? 3 : 3);
                                roots.add(String.join(".", java.util.Arrays.copyOf(parts, n)));
                            });
                        } catch (IOException | RuntimeException e) {
                            // arquivo ilegível: segue
                        }
                    });
        } catch (IOException | RuntimeException e) {
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
