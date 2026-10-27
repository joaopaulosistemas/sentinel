package com.sentinel.audit.service;

import java.util.Map;

/** Descrição curta, para a tela de acompanhamento, do que cada analisador faz. */
public final class AnalyzerCatalog {

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("Semgrep", "SAST · pacotes de regras"),
            Map.entry("Secrets", "Segredos no código · regex nos arquivos"),
            Map.entry("Secrets-History", "Segredos no histórico · últimos commits do Git"),
            Map.entry("Gitleaks", "Segredos e histórico"),
            Map.entry("Trivy", "Dependências · SCA e IaC"),
            Map.entry("OWASP-Dependency", "Dependências · base OWASP"),
            Map.entry("Architecture", "Estrutura e camadas"),
            Map.entry("Arquitetura (pacotes)", "Política de arquitetura do perfil"),
            Map.entry("CleanCode", "Funções longas, complexidade"),
            Map.entry("PMD", "Lint de Java"),
            Map.entry("ESLint", "TypeScript e Angular"),
            Map.entry("Higiene do projeto", "README, testes, CI, lockfile"),
            Map.entry("SARIF", "Ferramentas externas (SARIF)"),
            Map.entry("Evidências", "Dump do banco, cobertura, memória"),
            Map.entry("Ciclos de dependência", "Pacotes Java e arquivos TypeScript que se importam em círculo"),
            Map.entry("Duplicação", "Blocos de código repetidos"),
            Map.entry("Pontos quentes", "Arquivos que mudam muito e são complexos (histórico do Git)"),
            Map.entry("Código morto", "Exportações TypeScript que ninguém usa"));

    private AnalyzerCatalog() {
    }

    /** Nomes dos analisadores conhecidos, na ordem de exibição. */
    public static java.util.List<String> names() {
        return java.util.List.of("Semgrep", "Secrets", "Secrets-History", "Gitleaks", "Trivy", "OWASP-Dependency",
                "Architecture", "Arquitetura (pacotes)", "CleanCode", "PMD", "ESLint", "Higiene do projeto", "SARIF", "Evidências",
                "Ciclos de dependência", "Duplicação", "Pontos quentes", "Código morto");
    }

    /** Descrição do analisador, ou vazio quando não há. */
    public static String describe(String name) {
        return name == null ? "" : DESCRIPTIONS.getOrDefault(name, "");
    }
}
