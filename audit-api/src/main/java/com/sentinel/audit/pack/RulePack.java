package com.sentinel.audit.pack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Pacote de regras já carregado e validado. */
public record RulePack(PackManifest manifest, Path dir, List<RemediationEntry> remediation, List<DedupTopic> dedup,
                       ArchitecturePolicy architecture, List<ToolSpec> tools, int semgrepRuleCount) {

    public String id() {
        return manifest.id();
    }

    /** Pasta com as regras Semgrep do pacote, ou {@code null} se ele não traz regras de código. */
    public Path rulesDir() {
        Path d = dir.resolve("rules");
        return Files.isDirectory(d) && semgrepRuleCount > 0 ? d : null;
    }
}
