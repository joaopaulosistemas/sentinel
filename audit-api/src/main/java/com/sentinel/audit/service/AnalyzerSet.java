package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.ArchitectureAnalyzer;
import com.sentinel.audit.analyzer.ArchitecturePolicyAnalyzer;
import com.sentinel.audit.analyzer.CleanCodeAnalyzer;
import com.sentinel.audit.analyzer.CodeAnalyzer;
import com.sentinel.audit.analyzer.EslintAnalyzer;
import com.sentinel.audit.analyzer.GitleaksAnalyzer;
import com.sentinel.audit.analyzer.HistorySecretsAnalyzer;
import com.sentinel.audit.analyzer.HygieneAnalyzer;
import com.sentinel.audit.analyzer.OWASPDependencyAnalyzer;
import com.sentinel.audit.analyzer.PmdAnalyzer;
import com.sentinel.audit.analyzer.SarifAnalyzer;
import com.sentinel.audit.analyzer.SecretsAnalyzer;
import com.sentinel.audit.analyzer.SemgrepAnalyzer;
import com.sentinel.audit.analyzer.StructuralAnalyzers;
import com.sentinel.audit.analyzer.TrivyAnalyzer;
import com.sentinel.audit.analyzer.evidence.EvidenceAnalyzer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Conjunto de todos os analisadores conhecidos. Existe para que o orquestrador da auditoria não carregue uma
 * dependência por analisador: quem orquestra pede {@link #all()} e escolhe entre eles.
 * Campos ausentes (testes que montam só parte do conjunto) ficam de fora.
 */
@ApplicationScoped
public class AnalyzerSet {

    @Inject
    SemgrepAnalyzer semgrepAnalyzer;

    @Inject
    OWASPDependencyAnalyzer dependencyAnalyzer;

    @Inject
    HistorySecretsAnalyzer historyAnalyzer;

    @Inject
    TrivyAnalyzer trivyAnalyzer;

    @Inject
    GitleaksAnalyzer gitleaksAnalyzer;

    @Inject
    ArchitectureAnalyzer architectureAnalyzer;

    @Inject
    CleanCodeAnalyzer cleanCodeAnalyzer;

    @Inject
    PmdAnalyzer pmdAnalyzer;

    @Inject
    EslintAnalyzer eslintAnalyzer;

    @Inject
    SarifAnalyzer sarifAnalyzer;

    @Inject
    ArchitecturePolicyAnalyzer architecturePolicyAnalyzer;

    @Inject
    HygieneAnalyzer hygieneAnalyzer;

    @Inject
    EvidenceAnalyzer evidenceAnalyzer;

    @Inject
    StructuralAnalyzers structuralAnalyzers;

    @Inject
    SecretsAnalyzer secretsAnalyzer;

    /** Todos os analisadores conhecidos (os estruturais entram se o conjunto existir). */
    List<CodeAnalyzer> all() {
        return Stream.concat(Stream.of(
                semgrepAnalyzer, dependencyAnalyzer, architectureAnalyzer, secretsAnalyzer, historyAnalyzer,
                trivyAnalyzer, gitleaksAnalyzer, cleanCodeAnalyzer,
                pmdAnalyzer, eslintAnalyzer, sarifAnalyzer, architecturePolicyAnalyzer, hygieneAnalyzer,
                evidenceAnalyzer),
                structuralAnalyzers == null ? Stream.<CodeAnalyzer>empty() : structuralAnalyzers.all().stream())
                .filter(Objects::nonNull).toList();
    }
}
