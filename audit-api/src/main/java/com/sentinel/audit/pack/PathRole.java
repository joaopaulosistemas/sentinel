package com.sentinel.audit.pack;

import java.util.Locale;
import java.util.Set;

/**
 * Papel de um arquivo no projeto, deduzido só pelo caminho: o que não é código de produção (testes, documentação,
 * exemplos, código gerado) costuma gerar achados que não são problema, como chaves de exemplo e complexidade de teste.
 */
public enum PathRole {
    PRODUCTION,
    TEST,
    DOCS,
    FIXTURE,
    GENERATED;

    private static final Set<String> TEST_DIRS = Set.of("test", "tests", "__tests__", "spec", "specs", "e2e", "integration-tests");
    private static final Set<String> DOC_DIRS = Set.of("docs", "doc", "documentation", "wiki");
    private static final Set<String> FIXTURE_DIRS = Set.of("fixtures", "__fixtures__", "testdata", "test-data", "mocks", "__mocks__",
            "examples", "example", "samples", "sample", "demo", "demos");
    private static final Set<String> GENERATED_DIRS = Set.of("generated", "target", "build", "dist", "node_modules",
            "vendor", ".next", ".gradle");

    /** {@code true} para tudo que não é código de produção. */
    public boolean isNonProduction() {
        return this != PRODUCTION;
    }

    /** Descrição curta para explicar a baixa confiança ao usuário. */
    public String describe() {
        return switch (this) {
            case TEST -> "arquivo de teste";
            case DOCS -> "documentação";
            case FIXTURE -> "arquivo de exemplo ou dado de teste";
            case GENERATED -> "código gerado ou dependência baixada";
            case PRODUCTION -> "código de produção";
        };
    }

    public static PathRole of(String path) {
        if (path == null || path.isBlank()) {
            return PRODUCTION;
        }
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String[] parts = p.split("/");
        String file = parts[parts.length - 1];
        for (int i = 0; i < parts.length - 1; i++) {
            if (GENERATED_DIRS.contains(parts[i])) {
                return GENERATED;
            }
        }
        if (file.endsWith(".min.js") || file.endsWith(".min.css") || file.endsWith(".map")) {
            return GENERATED;
        }
        if (file.endsWith(".example") || file.endsWith(".sample") || file.endsWith(".template")) {
            return FIXTURE;
        }
        for (int i = 0; i < parts.length - 1; i++) {
            if (FIXTURE_DIRS.contains(parts[i])) {
                return FIXTURE;
            }
        }
        for (int i = 0; i < parts.length - 1; i++) {
            if (TEST_DIRS.contains(parts[i])) {
                return TEST;
            }
        }
        if (file.endsWith("test.java") || file.endsWith("tests.java") || file.contains(".test.") || file.contains(".spec.")
                || file.startsWith("test_") || file.endsWith("_test.go") || file.endsWith("_test.py")) {
            return TEST;
        }
        for (int i = 0; i < parts.length - 1; i++) {
            if (DOC_DIRS.contains(parts[i])) {
                return DOCS;
            }
        }
        if (parts.length == 1 && (file.endsWith(".md") || file.startsWith("readme") || file.startsWith("changelog"))) {
            return DOCS;
        }
        return PRODUCTION;
    }
}
