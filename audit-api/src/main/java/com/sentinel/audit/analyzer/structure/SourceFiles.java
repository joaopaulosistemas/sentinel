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
        List<Src> out = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                if (SKIP_DIRS.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String rel = rel(root, dir);
                PathRole role = PathRole.of(rel + "/x");
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
                String name = file.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (attrs.isSymbolicLink() || !attrs.isRegularFile() || attrs.size() > MAX_FILE_BYTES
                        || extensions.stream().noneMatch(lower::endsWith)
                        || lower.endsWith(".d.ts") || lower.endsWith(".min.js")) {
                    return FileVisitResult.CONTINUE;
                }
                String rel = rel(root, file);
                if (!includeNonProduction && PathRole.of(rel).isNonProduction()) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    out.add(new Src(rel, Files.readString(file, StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    // binário ou codificação inesperada: ignora o arquivo
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        out.sort(java.util.Comparator.comparing(Src::rel));        // a ordem do disco varia; o relatório não pode variar
        return out;
    }

    static String rel(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }
}
