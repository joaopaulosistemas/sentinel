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
            "vendor", ".next", ".gradle", "third_party", "thirdparty", "opensource");

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

    private static final java.util.List<String> GENERATED_SUFFIXES = java.util.List.of(".min.js", ".min.css", ".map");
    private static final java.util.List<String> FIXTURE_SUFFIXES = java.util.List.of(".example", ".sample", ".template");
    private static final java.util.List<String> TEST_SUFFIXES = java.util.List.of("test.java", "tests.java", "_test.go", "_test.py");
    private static final java.util.List<String> TEST_PARTS = java.util.List.of(".test.", ".spec.");
    private static final java.util.List<String> DOC_PREFIXES = java.util.List.of("readme", "changelog");

    public static PathRole of(String path) {
        if (path == null || path.isBlank()) {
            return PRODUCTION;
        }
        String[] parts = path.replace('\\', '/').toLowerCase(Locale.ROOT).split("/");
        String file = parts[parts.length - 1];
        PathRole role = PRODUCTION;
        if (inDirs(parts, GENERATED_DIRS) || GENERATED_SUFFIXES.stream().anyMatch(file::endsWith)) {
            role = GENERATED;
        } else if (FIXTURE_SUFFIXES.stream().anyMatch(file::endsWith) || inDirs(parts, FIXTURE_DIRS)) {
            role = FIXTURE;
        } else if (inDirs(parts, TEST_DIRS) || isTestFile(file)) {
            role = TEST;
        } else if (inDirs(parts, DOC_DIRS) || isTopLevelDoc(parts, file)) {
            role = DOCS;
        }
        return role;
    }

    /** Algum diretório do caminho (o último elemento é o arquivo e não conta) está no conjunto. */
    private static boolean inDirs(String[] parts, java.util.Set<String> dirs) {
        for (int i = 0; i < parts.length - 1; i++) {
            if (dirs.contains(parts[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTestFile(String file) {
        return TEST_SUFFIXES.stream().anyMatch(file::endsWith) || TEST_PARTS.stream().anyMatch(file::contains)
                || file.startsWith("test_");
    }

    private static boolean isTopLevelDoc(String[] parts, String file) {
        return parts.length == 1 && (file.endsWith(".md") || DOC_PREFIXES.stream().anyMatch(file::startsWith));
    }
}
