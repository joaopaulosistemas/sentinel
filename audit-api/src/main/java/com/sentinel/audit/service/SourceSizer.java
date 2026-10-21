package com.sentinel.audit.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;

/**
 * Conta as linhas de código-fonte de um projeto (sem linhas em branco), a base do score por densidade.
 * <p>
 * Considera só as tecnologias que o Sentinel audita (Java, TypeScript/JavaScript, HTML, CSS/SCSS e SQL) e ignora
 * pastas de build, dependências, testes e código gerado, as mesmas que os analisadores ignoram, para o tamanho
 * refletir o que de fato é analisado. Nunca falha: erro de leitura de um arquivo apenas o deixa de fora.
 */
public final class SourceSizer {

    static final Set<String> EXTENSIONS = Set.of(
            ".java", ".ts", ".tsx", ".js", ".jsx", ".mjs", ".html", ".css", ".scss", ".sql");
    static final Set<String> IGNORED_DIRS = Set.of(
            "target", "build", "dist", "out", "node_modules", ".git", ".idea", ".gradle", "vendor", "coverage",
            "test", "tests", "__tests__", "generated", "generated-sources", "e2e", ".angular", ".sentinel");
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_FILES = 200_000;

    private SourceSizer() {
    }

    public static long countLines(Path root) {
        long[] total = {0};
        int[] files = {0};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && IGNORED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile() || Files.isSymbolicLink(file) || attrs.size() > MAX_FILE_BYTES) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    int dot = name.lastIndexOf('.');
                    if (dot < 0 || !EXTENSIONS.contains(name.substring(dot))
                            || name.contains(".min.") || name.endsWith(".d.ts")
                            || name.endsWith(".spec.ts") || name.contains(".test.")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (++files[0] > MAX_FILES) {
                        return FileVisitResult.TERMINATE;
                    }
                    try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
                        total[0] += lines.filter(l -> !l.isBlank()).count();
                    } catch (IOException | RuntimeException e) {
                        // arquivo binário ou com codificação inválida: fica fora da contagem
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            // devolve o que contou até aqui
        }
        return total[0];
    }
}
