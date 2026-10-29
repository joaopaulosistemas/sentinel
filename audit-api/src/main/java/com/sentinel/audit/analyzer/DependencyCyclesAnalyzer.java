package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.structure.DependencyCycles;
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
import java.util.List;
import java.util.Map;

/**
 * Ciclos de dependência: pacotes Java que se importam em círculo e arquivos TypeScript/JavaScript que se importam
 * em círculo. Só lê o texto dos imports (nada é compilado nem executado). Limite do perfil: {@code max-cycle-findings}.
 */
@ApplicationScoped
public class DependencyCyclesAnalyzer implements CodeAnalyzer {

    private static final int DEFAULT_MAX_FINDINGS = 30;
    /** Quantos membros do ciclo aparecem no texto do achado. */
    private static final int SUMMARY_MEMBERS = 12;
    /** Ciclo com tantos membros ou mais é alto; menor, médio. */
    private static final int BIG_CYCLE_SIZE = 8;


    private static final Logger LOG = Logger.getLogger(DependencyCyclesAnalyzer.class);
    static final String ANALYZER_NAME = "Ciclos de dependência";
    private static final int MAX_FILES = 20_000;

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        int max = policy == null ? DEFAULT_MAX_FINDINGS : policy.threshold("max-cycle-findings", DEFAULT_MAX_FINDINGS);
        try {
            List<Src> java = SourceFiles.read(projectPath, SourceFiles.JAVA, MAX_FILES, false);
            List<Src> ts = SourceFiles.read(projectPath, SourceFiles.TS, MAX_FILES, false);
            findings.addAll(packageFindings(auditRun, java, max));
            findings.addAll(fileFindings(auditRun, ts, max));
        } catch (Exception e) {
            LOG.warnf("Ciclos de dependência não puderam ser verificados: %s", e.getMessage());
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    static List<Finding> packageFindings(AuditRun run, List<Src> java, int max) {
        Map<String, String> fileOf = DependencyCycles.packageFiles(java);
        List<Finding> out = new ArrayList<>();
        for (List<String> cycle : DependencyCycles.javaPackages(java)) {
            if (out.size() >= max) {
                break;
            }
            out.add(StructuralFindings.make(run, new StructuralFindings.Kind(ANALYZER_NAME, "sentinel-arq-ciclo-pacotes", severity(cycle.size()), Category.ARCHITECTURE), new StructuralFindings.Spot(fileOf.getOrDefault(cycle.get(0), "."), 1, String.join(",", cycle)), "Ciclo de dependência entre " + cycle.size() + " pacotes", "Estes pacotes se importam em círculo, então não dá para entender, testar nem trocar um sem os outros: "
                            + StructuralFindings.summarize(cycle, SUMMARY_MEMBERS) + ". Escolha o sentido da dependência (quem conhece quem) "
                            + "e quebre a volta: extraia a interface para o pacote de quem é dependido ou mova o que é "
                            + "compartilhado para um pacote que ninguém do ciclo importe de volta."));
        }
        return out;
    }

    static List<Finding> fileFindings(AuditRun run, List<Src> ts, int max) {
        List<Finding> out = new ArrayList<>();
        for (List<String> cycle : DependencyCycles.tsFiles(ts)) {
            if (out.size() >= max) {
                break;
            }
            out.add(StructuralFindings.make(run, new StructuralFindings.Kind(ANALYZER_NAME, "sentinel-arq-ciclo-arquivos", severity(cycle.size()), Category.ARCHITECTURE), new StructuralFindings.Spot(cycle.get(0), 1, String.join(",", cycle)), "Ciclo de importação entre " + cycle.size() + " arquivos", "Estes arquivos se importam em círculo: " + StructuralFindings.summarize(cycle, SUMMARY_MEMBERS) + ". Um ciclo pode "
                            + "carregar o módulo pela metade (valor indefinido em tempo de execução) e atrapalha testes e "
                            + "divisão do código. Mova o que é compartilhado para um arquivo que nenhum dos dois importe de volta."));
        }
        return out;
    }

    private static Severity severity(int size) {
        return size >= BIG_CYCLE_SIZE ? Severity.HIGH : Severity.MEDIUM;
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
