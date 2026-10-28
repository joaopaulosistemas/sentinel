package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.structure.Duplication;
import com.sentinel.audit.analyzer.structure.Duplication.Clone;
import com.sentinel.audit.analyzer.structure.SourceFiles;
import com.sentinel.audit.analyzer.structure.SourceFiles.Src;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Código duplicado em Java e TypeScript/JavaScript, só em código de produção. Ignora comentários, imports, getters e
 * setters. Limites do perfil: {@code dup-window} (linhas comparadas por vez, 10), {@code dup-min-lines} (tamanho mínimo
 * do bloco, 15) e {@code max-findings} (100).
 */
@ApplicationScoped
public class DuplicationAnalyzer implements CodeAnalyzer {

    private static final int DEFAULT_MIN_LINES = 15;
    /** Menor janela de comparação aceita, em linhas. */
    private static final int MIN_WINDOW = 4;
    /** Bloco repetido com tantas linhas ou mais é médio; menor, baixo. */
    private static final int MEDIUM_MIN_LINES = 30;


    private static final Logger LOG = Logger.getLogger(DuplicationAnalyzer.class);
    static final String ANALYZER_NAME = "Duplicação";
    private static final int MAX_FILES = 20_000;

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        int window = policy == null ? 10 : policy.threshold("dup-window", 10);
        int minLines = policy == null ? DEFAULT_MIN_LINES : policy.threshold("dup-min-lines", DEFAULT_MIN_LINES);
        int max = policy == null ? 100 : policy.threshold("max-findings", 100);
        try {
            Set<String> exts = new HashSet<>(SourceFiles.JAVA);
            exts.addAll(SourceFiles.TS);
            List<Src> files = SourceFiles.read(projectPath, exts, MAX_FILES, false);
            findings = toFindings(auditRun, Duplication.find(files, Math.max(MIN_WINDOW, window), Math.max(window, minLines), max));
        } catch (Exception e) {
            LOG.warnf("Duplicação não pôde ser verificada: %s", e.getMessage());
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    static List<Finding> toFindings(AuditRun run, List<Clone> clones) {
        List<Finding> out = new ArrayList<>();
        for (Clone c : clones) {
            Severity sev = c.lines() >= MEDIUM_MIN_LINES ? Severity.MEDIUM : Severity.LOW;
            out.add(StructuralFindings.make(run, ANALYZER_NAME, "sentinel-dup-bloco", sev, Category.QUALITY,
                    c.fileB(), c.lineB(),
                    "Código duplicado: " + c.lines() + " linhas iguais a " + c.fileA() + ":" + c.lineA(),
                    "Este bloco de " + c.lines() + " linhas significativas é igual ao de " + c.fileA() + " (linha " + c.lineA()
                            + "). Cópias divergem com o tempo: um conserto vai para um lado só. Extraia o trecho comum para "
                            + "um método ou classe usado pelos dois lugares.",
                    c.firstLine()));
        }
        return out;
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return stack == Stack.JAVA || stack == Stack.JAVASCRIPT || stack == Stack.MIXED;
    }
}
