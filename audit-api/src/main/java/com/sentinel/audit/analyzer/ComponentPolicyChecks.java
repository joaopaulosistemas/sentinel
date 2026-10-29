package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.deps.Dependency;
import com.sentinel.audit.analyzer.deps.DependencyExtractor;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.pack.ArchitecturePolicy;
import com.sentinel.audit.pack.ArchitecturePolicy.DepRule;
import com.sentinel.audit.pack.Versions;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Confere as dependências declaradas do projeto com a política de componentes (proibidas, versão mínima, obrigatórias, lista aprovada). */
final class ComponentPolicyChecks {

    /** Teto de achados "fora da lista aprovada" por auditoria. */
    private static final int MAX_NOT_APPROVED = 200;


    private ComponentPolicyChecks() {
    }

    static void check(Path root, ArchitecturePolicy pol, AuditRun run, List<Finding> out) {
        ArchitecturePolicy.Components cp = pol.components();
        if (cp.forbidden().isEmpty() && cp.minVersion().isEmpty() && cp.required().isEmpty() && cp.approvedOnly().isEmpty()) {
            return;
        }
        List<Dependency> deps = DependencyExtractor.extract(root);
        for (Dependency d : deps) {
            checkForbidden(cp, d, run, out);
            checkMinVersion(cp, d, run, out);
        }
        checkRequired(cp, deps, run, out);
        checkApprovedOnly(cp, deps, run, out);
    }

    private static Severity severityOr(DepRule r, Severity fallback) {
        return r.severity() == null ? fallback : r.severity();
    }

    private static void checkForbidden(ArchitecturePolicy.Components cp, Dependency d, AuditRun run, List<Finding> out) {
        for (DepRule r : cp.forbidden()) {
            if (ArchGlobs.globMatches(r.dep(), d.name())) {
                out.add(ArchitecturePolicyAnalyzer.archFinding(run, new ArchitecturePolicyAnalyzer.ArchPlace(d.filePath(), 0, d.name() + ":" + d.version()), new ArchitecturePolicyAnalyzer.ArchRule("sentinel-comp-forbidden", severityOr(r, Severity.HIGH), Category.DEPENDENCY, "Biblioteca proibida: " + d.name(), reason(r, "A política proíbe " + d.name() + ".") + " (versão " + d.version() + ")")));
            }
        }
    }

    private static void checkMinVersion(ArchitecturePolicy.Components cp, Dependency d, AuditRun run, List<Finding> out) {
        for (DepRule r : cp.minVersion()) {
            if (ArchGlobs.globMatches(r.dep(), d.name()) && Boolean.FALSE.equals(Versions.satisfies(d.version(), r.version()))) {
                out.add(ArchitecturePolicyAnalyzer.archFinding(run, new ArchitecturePolicyAnalyzer.ArchPlace(d.filePath(), 0, d.name() + ":" + d.version()), new ArchitecturePolicyAnalyzer.ArchRule("sentinel-comp-min-version", severityOr(r, Severity.HIGH), Category.DEPENDENCY, "Versão fora da política: " + d.name() + " " + d.version() + " (exigido " + r.version() + ")", reason(r, "A política exige " + d.name() + " " + r.version() + "."))));
            }
        }
    }

    private static void checkRequired(ArchitecturePolicy.Components cp, List<Dependency> deps, AuditRun run, List<Finding> out) {
        for (DepRule r : cp.required()) {
            if (deps.stream().noneMatch(d -> ArchGlobs.globMatches(r.dep(), d.name()))) {
                out.add(ArchitecturePolicyAnalyzer.archFinding(run, new ArchitecturePolicyAnalyzer.ArchPlace("(dependências)", 0, ""), new ArchitecturePolicyAnalyzer.ArchRule("sentinel-comp-required", severityOr(r, Severity.MEDIUM), Category.DEPENDENCY, "Biblioteca obrigatória ausente: " + r.dep(), reason(r, "A política exige " + r.dep() + "."))));
            }
        }
    }

    /** Com lista aprovada, toda dependência declarada fora dela vira achado (no máximo 200). */
    private static void checkApprovedOnly(ArchitecturePolicy.Components cp, List<Dependency> deps, AuditRun run, List<Finding> out) {
        if (cp.approvedOnly().isEmpty()) {
            return;
        }
        int n = 0;
        for (Dependency d : deps) {
            String f = d.filePath().toLowerCase(Locale.ROOT);
            if (f.endsWith("package-lock.json") || f.endsWith("yarn.lock") || f.endsWith("pnpm-lock.yaml")) {
                continue;                                   // transitivas: só declaradas diretamente
            }
            if (cp.approvedOnly().stream().noneMatch(r -> ArchGlobs.globMatches(r.dep(), d.name())) && n < MAX_NOT_APPROVED) {
                n++;
                out.add(ArchitecturePolicyAnalyzer.archFinding(run, new ArchitecturePolicyAnalyzer.ArchPlace(d.filePath(), 0, d.name() + ":" + d.version()), new ArchitecturePolicyAnalyzer.ArchRule("sentinel-comp-not-approved", Severity.MEDIUM, Category.DEPENDENCY, "Biblioteca fora da lista aprovada: " + d.name(), "A política só permite as bibliotecas aprovadas; " + d.name() + " não está na lista.")));
            }
        }
    }

    private static String reason(DepRule r, String fallback) {
        return r.reason() == null || r.reason().isBlank() ? fallback : r.reason();
    }
}
