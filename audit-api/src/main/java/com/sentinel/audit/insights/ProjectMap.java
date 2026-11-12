package com.sentinel.audit.insights;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Mapa do projeto auditado: o que foi encontrado no repositório, só por evidência de arquivos.
 * Tudo aqui é fato observado; nada é inferido por heurística de "qualidade".
 */
public record ProjectMap(
        @Schema(description = "Arquivos de código por linguagem.") Map<String, Integer> languages,
        @Schema(description = "Ferramentas de build encontradas.") List<String> buildTools,
        @Schema(description = "Frameworks encontrados.") List<String> frameworks,
        @Schema(description = "Pontos de entrada encontrados.") List<String> entryPoints,
        @Schema(description = "Arquivos de CI.") List<String> ciConfigs,
        @Schema(description = "Dockerfiles.") List<String> dockerfiles,
        @Schema(description = "Arquivos de infraestrutura como código.") List<String> iacFiles,
        @Schema(description = "Documentação encontrada.") List<String> docs,
        @Schema(description = "Arquivos de trava de dependências.") List<String> lockfiles,
        @Schema(description = "Arquivos de código-fonte.") int sourceFiles,
        @Schema(description = "Arquivos de teste.") int testFiles,
        @Schema(description = "Tem README.") boolean hasReadme,
        @Schema(description = "Linhas do README.") int readmeLines,
        @Schema(description = "Tem .gitignore.") boolean hasGitignore,
        @Schema(description = "Tem arquivo de licença.") boolean hasLicense,
        @Schema(description = "Scripts encontrados.") List<String> scripts,
        @Schema(description = "Arquivos .env versionados.") List<String> committedEnvFiles,
        @Schema(description = "Arquivos de chave versionados.") List<String> committedKeyFiles,
        @Schema(description = "Pastas de artefatos de build versionadas.") List<String> committedArtifactDirs,
        @Schema(description = "package.json sem arquivo de trava.") List<String> packageJsonWithoutLock,
        @Schema(description = "Dockerfiles sem .dockerignore.") List<String> dockerfilesWithoutIgnore,
        @Schema(description = "Verdadeiro quando a lista foi cortada por tamanho.") boolean truncated) {

    /** Proporção de arquivos de teste sobre o total de arquivos de código (0 se não há código). */
    public double testRatio() {
        int total = sourceFiles + testFiles;
        return total == 0 ? 0.0 : (double) testFiles / total;
    }
}
