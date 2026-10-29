package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.structure.SourceFiles;
import com.sentinel.audit.analyzer.structure.SourceFiles.Src;
import com.sentinel.audit.analyzer.structure.UnusedExports;
import com.sentinel.audit.analyzer.structure.UnusedExports.Unused;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Código morto em TypeScript/JavaScript: exportações que nenhum outro arquivo (testes inclusive) menciona. Se o
 * item também não é usado dentro do próprio arquivo, é código morto; se é, basta tirar o {@code export}. Bibliotecas
 * publicadas (package.json com entrada e sem {@code "private": true}) são ignoradas: o que exportam é a API pública.
 * Em Java o PMD já cobre campos e métodos privados sem uso. Limite do perfil: {@code max-findings} (100).
 */
@ApplicationScoped
public class DeadCodeAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(DeadCodeAnalyzer.class);
    static final String ANALYZER_NAME = "Código morto";
    private static final int MAX_FILES = 20_000;

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        int max = policy == null ? 100 : policy.threshold("max-findings", 100);
        try {
            if (!isLibrary(projectPath)) {
                List<Src> production = SourceFiles.read(projectPath, SourceFiles.TS, MAX_FILES, false);
                List<Src> everything = SourceFiles.read(projectPath, SourceFiles.TS, MAX_FILES, true);
                findings = toFindings(auditRun, UnusedExports.find(production, everything, max));
            }
        } catch (Exception e) {
            LOG.warnf("Código morto não pôde ser verificado: %s", e.getMessage());
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    private static boolean isLibrary(Path root) {
        try {
            Path pkg = root.resolve("package.json");
            return Files.isRegularFile(pkg) && !Files.isSymbolicLink(pkg)
                    && UnusedExports.looksLikeLibrary(Files.readString(pkg, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    static List<Finding> toFindings(AuditRun run, List<Unused> unused) {
        List<Finding> out = new ArrayList<>();
        for (Unused u : unused) {
            String snippet = "export " + u.kind() + " " + u.name();
            if (u.usedInside()) {
                out.add(StructuralFindings.make(run, new StructuralFindings.Kind(ANALYZER_NAME, "sentinel-morto-export-interno", Severity.INFO, Category.QUALITY), new StructuralFindings.Spot(u.file(), u.line(), snippet), "Export desnecessário: " + u.name(), u.name() + " é exportado, mas nenhum outro arquivo o usa. Tire o `export` para deixar claro o que é "
                                + "interface do módulo e o que é detalhe."));
            } else {
                out.add(StructuralFindings.make(run, new StructuralFindings.Kind(ANALYZER_NAME, "sentinel-morto-export", Severity.LOW, Category.QUALITY), new StructuralFindings.Spot(u.file(), u.line(), snippet), "Código morto: " + u.name() + " não é usado em lugar nenhum", u.name() + " é exportado e nenhum arquivo do projeto, nem de teste, o menciona. Remova, ou confirme "
                                + "que ele é chamado de fora (por nome, por framework ou por outro repositório) antes de apagar."));
            }
        }
        return out;
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return stack == Stack.JAVASCRIPT || stack == Stack.MIXED;
    }
}
