package com.sentinel.audit.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
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

    /** Tamanho do código analisado: arquivos considerados e linhas não vazias. */
    public record Stats(long files, long lines) {}

    public static long countLines(Path root) {
        return stats(root).lines();
    }

    public static Stats stats(Path root) {
        Counter counter = new Counter(root);
        try {
            Files.walkFileTree(root, counter);
        } catch (IOException | RuntimeException ignored) {
            // devolve o que contou até aqui
        }
        return new Stats(counter.files, counter.total);
    }

    /** Percorre a árvore contando arquivos de código e suas linhas não vazias. */
    private static final class Counter extends SimpleFileVisitor<Path> {
        private final Path root;
        long total;
        int files;

        Counter(Path root) {
            this.root = root;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (!dir.equals(root) && IGNORED_DIRS.contains(dir.getFileName().toString())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!attrs.isRegularFile() || Files.isSymbolicLink(file) || attrs.size() > MAX_FILE_BYTES
                    || !isCountable(file.getFileName().toString().toLowerCase(Locale.ROOT))) {
                return FileVisitResult.CONTINUE;
            }
            files++;
            if (files > MAX_FILES) {
                return FileVisitResult.TERMINATE;
            }
            try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
                total += lines.filter(l -> !l.isBlank()).count();
            } catch (IOException | RuntimeException ignored) {
                // arquivo binário ou com codificação inválida: fica fora da contagem
            }
            return FileVisitResult.CONTINUE;
        }

        private static boolean isCountable(String name) {
            int dot = name.lastIndexOf('.');
            return dot >= 0 && EXTENSIONS.contains(name.substring(dot)) && !isGeneratedOrTest(name);
        }

        private static boolean isGeneratedOrTest(String name) {
            return name.contains(".min.") || name.contains(".test.") || name.endsWith(".d.ts") || name.endsWith(".spec.ts");
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }
}
