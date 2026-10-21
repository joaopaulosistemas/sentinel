package com.sentinel.audit.insights;

import java.util.List;
import java.util.Map;

/**
 * Mapa do projeto auditado: o que foi encontrado no repositório, só por evidência de arquivos.
 * Tudo aqui é fato observado; nada é inferido por heurística de "qualidade".
 */
public record ProjectMap(
        Map<String, Integer> languages,
        List<String> buildTools,
        List<String> frameworks,
        List<String> entryPoints,
        List<String> ciConfigs,
        List<String> dockerfiles,
        List<String> iacFiles,
        List<String> docs,
        List<String> lockfiles,
        int sourceFiles,
        int testFiles,
        boolean hasReadme,
        int readmeLines,
        boolean hasGitignore,
        boolean hasLicense,
        List<String> scripts,
        List<String> committedEnvFiles,
        List<String> committedKeyFiles,
        List<String> committedArtifactDirs,
        List<String> packageJsonWithoutLock,
        List<String> dockerfilesWithoutIgnore,
        boolean truncated) {

    /** Proporção de arquivos de teste sobre o total de arquivos de código (0 se não há código). */
    public double testRatio() {
        int total = sourceFiles + testFiles;
        return total == 0 ? 0.0 : (double) testFiles / total;
    }
}
