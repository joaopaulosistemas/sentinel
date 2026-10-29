package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.pack.PathRole;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lê os arquivos de código de um projeto clonado, sem seguir links e sem entrar em pastas que não são do sistema. */
public final class SourceFiles {

    private SourceFiles() {}

    /** Arquivo lido: caminho relativo (com "/") e conteúdo. */
    public record Src(String rel, String text) {}

    static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final Set<String> SKIP_DIRS = Set.of(".git", ".sentinel", ".idea", ".angular", ".nx", "coverage");

    public static final Set<String> JAVA = Set.of(".java");
    public static final Set<String> TS = Set.of(".ts", ".tsx", ".js", ".jsx", ".mjs");

    /**
     * @param includeNonProduction {@code true} traz também testes, exemplos e documentação (para saber quem usa o quê);
     *                             {@code false} só o código de produção (o que se audita)
     */
    public static List<Src> read(Path root, Set<String> extensions, int maxFiles, boolean includeNonProduction) throws IOException {
        Collector collector = new Collector(root, extensions, maxFiles, includeNonProduction);
        Files.walkFileTree(root, collector);
        List<Src> out = collector.out;
        out.sort(java.util.Comparator.comparing(Src::rel));        // a ordem do disco varia; o relatório não pode variar
        return out;
    }

    /** Percorre a árvore juntando os arquivos de código que interessam. */
    private static final class Collector extends SimpleFileVisitor<Path> {
        private final Path root;
        private final Set<String> extensions;
        private final int maxFiles;
        private final boolean includeNonProduction;
        final List<Src> out = new ArrayList<>();

        Collector(Path root, Set<String> extensions, int maxFiles, boolean includeNonProduction) {
            this.root = root;
            this.extensions = extensions;
            this.maxFiles = maxFiles;
            this.includeNonProduction = includeNonProduction;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (dir.equals(root)) {
                return FileVisitResult.CONTINUE;
            }
            if (SKIP_DIRS.contains(dir.getFileName().toString())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            PathRole role = PathRole.of(rel(root, dir) + "/x");
            if (role == PathRole.GENERATED || (!includeNonProduction && role.isNonProduction())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (out.size() >= maxFiles) {
                return FileVisitResult.TERMINATE;
            }
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (isCandidate(attrs, lower)) {
                addIfProduction(file);
            }
            return FileVisitResult.CONTINUE;
        }

        private boolean isCandidate(BasicFileAttributes attrs, String lower) {
            boolean usable = attrs.isRegularFile() && !attrs.isSymbolicLink() && attrs.size() <= MAX_FILE_BYTES;
            return usable && extensions.stream().anyMatch(lower::endsWith)
                    && !lower.endsWith(".d.ts") && !lower.endsWith(".min.js");
        }

        private void addIfProduction(Path file) {
            String rel = rel(root, file);
            if (!includeNonProduction && PathRole.of(rel).isNonProduction()) {
                return;
            }
            try {
                out.add(new Src(rel, Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException ignored) {
                // binário ou codificação inesperada: ignora o arquivo
            }
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }

    static String rel(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }
}
