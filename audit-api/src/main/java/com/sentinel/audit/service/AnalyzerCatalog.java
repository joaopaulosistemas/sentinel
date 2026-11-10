package com.sentinel.audit.service;

import java.util.Map;

/** Descrição curta, para a tela de acompanhamento, do que cada analisador faz. */
public final class AnalyzerCatalog {

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("Opengrep", "SAST · pacotes de regras"),
            Map.entry("Secrets", "Segredos no código · regex nos arquivos"),
            Map.entry("Secrets-History", "Segredos no histórico · últimos commits do Git"),
            Map.entry("Gitleaks", "Segredos e histórico"),
            Map.entry("Betterleaks", "Segredos e histórico · sucessor do Gitleaks (opt-in)"),
            Map.entry("Trivy", "Dependências · SCA e IaC"),
            Map.entry("OWASP-Dependency", "Dependências · base OWASP"),
            Map.entry("Architecture", "Estrutura e camadas"),
            Map.entry("Arquitetura (pacotes)", "Política de arquitetura do perfil"),
            Map.entry("CleanCode", "Funções longas, complexidade"),
            Map.entry("PMD", "Lint de Java"),
            Map.entry("ESLint", "TypeScript e Angular"),
            Map.entry("Higiene do projeto", "README, testes, CI, lockfile"),
            Map.entry("SARIF", "Ferramentas externas (SARIF), inclui o Joern; desligar aqui desliga todas"),
            Map.entry("Evidências", "Dump do banco, cobertura, memória"),
            Map.entry("Ciclos de dependência", "Pacotes Java e arquivos TypeScript que se importam em círculo"),
            Map.entry("Duplicação", "Blocos de código repetidos"),
            Map.entry("Pontos quentes", "Arquivos que mudam muito e são complexos (histórico do Git)"),
            Map.entry("Código morto", "Exportações TypeScript que ninguém usa"),
            Map.entry("Mensagens de commit", "Primeira linha dos commits no padrão Conventional Commits"));

    /** Analisadores que só tratam de um lado; os demais (segredos, dependências, Git, duplicação, clean code) valem para os dois. */
    private static final Map<String, String> SIDES = Map.of(
            "PMD", "back",
            "OWASP-Dependency", "back",
            "Evidências", "back",
            "ESLint", "front",
            "Código morto", "front");

    private AnalyzerCatalog() {
    }

    /** Nomes dos analisadores conhecidos, na ordem de exibição. */
    public static java.util.List<String> names() {
        return java.util.List.of("Opengrep", "Secrets", "Secrets-History", "Gitleaks", "Betterleaks", "Trivy", "OWASP-Dependency",
                "Architecture", "Arquitetura (pacotes)", "CleanCode", "PMD", "ESLint", "Higiene do projeto", "SARIF", "Evidências",
                "Ciclos de dependência", "Duplicação", "Pontos quentes", "Código morto", "Mensagens de commit");
    }

    /** Descrição do analisador, ou vazio quando não há. */
    public static String describe(String name) {
        return name == null ? "" : DESCRIPTIONS.getOrDefault(name, "");
    }

    /** Lado do analisador: {@code back}, {@code front} ou {@code common} (também para nome desconhecido). */
    public static String sideOf(String name) {
        return name == null ? "common" : SIDES.getOrDefault(name, "common");
    }
}
