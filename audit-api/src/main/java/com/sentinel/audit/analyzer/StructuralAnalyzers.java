package com.sentinel.audit.analyzer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/** Reúne os analisadores estruturais para a orquestração receber um só componente em vez de quatro. */
@ApplicationScoped
public class StructuralAnalyzers {

    @Inject
    DependencyCyclesAnalyzer cycles;

    @Inject
    DuplicationAnalyzer duplication;

    @Inject
    HotspotAnalyzer hotspots;

    @Inject
    DeadCodeAnalyzer deadCode;

    public List<CodeAnalyzer> all() {
        return List.of(cycles, duplication, hotspots, deadCode);
    }
}
