package com.sentinel.audit.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Copia uma lista de arquivos de uma pasta para outra, em paralelo, sem nunca sair da pasta de origem: links
 * simbólicos, arquivos grandes demais, caminhos que escapam ({@code ..}) e pastas que viraram link para fora são
 * ignorados. A origem só é lida.
 */
final class WorkingTreeCopier {

    /** Quantos arquivos são copiados ao mesmo tempo. */
    static final int COPY_THREADS = 8;
    /** Intervalo, em arquivos, entre dois avisos de andamento da cópia. */
    static final int PROGRESS_EVERY = 100;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final Path src;
    private final Path target;
    /** Pastas já verificadas/criadas: evita repetir, para cada arquivo, as consultas que só dependem da pasta. */
    private final Set<Path> trustedParents = ConcurrentHashMap.newKeySet();
    private final Set<Path> createdDirs = ConcurrentHashMap.newKeySet();

    WorkingTreeCopier(Path src, Path target) {
        this.src = src;
        this.target = target;
    }

    /**
     * Copia os arquivos (caminhos relativos à origem) dentro do prazo.
     *
     * @throws IllegalStateException se o prazo estourar ou um arquivo falhar de forma inesperada
     * @throws IOException           se a leitura ou a gravação de um arquivo falhar
     */
    void copyAll(List<String> files, long timeoutSeconds, LocalSource.CopyProgress progress)
            throws IOException, InterruptedException {
        long total = files.size();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(timeoutSeconds, 1));
        AtomicLong done = new AtomicLong();
        // Em volume lento (disco do Windows no WSL, bind mount do Docker Desktop) cada arquivo custa dezenas de
        // milissegundos só em chamadas ao sistema de arquivos: copiar em paralelo esconde essa espera.
        try (ExecutorService pool = Executors.newFixedThreadPool(COPY_THREADS)) {
            try {
                List<Future<Void>> pending = new ArrayList<>(files.size());
                for (String rel : files) {
                    pending.add(pool.submit(task(rel, deadline, timeoutSeconds, done, total, progress)));
                }
                awaitAll(pending, pool);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private Callable<Void> task(String rel, long deadline, long timeoutSeconds, AtomicLong done, long total,
                                LocalSource.CopyProgress progress) {
        return () -> {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("A cópia da pasta excedeu o tempo limite de " + timeoutSeconds
                        + "s (" + done.get() + " de " + total + " arquivos copiados)");
            }
            copyOne(rel);
            long n = done.incrementAndGet();
            if (n % PROGRESS_EVERY == 0) {
                progress.report(n, total);
            }
            return null;
        };
    }

    private static void awaitAll(List<Future<Void>> pending, ExecutorService pool) throws IOException, InterruptedException {
        for (Future<Void> f : pending) {
            try {
                f.get();
            } catch (ExecutionException e) {
                pool.shutdownNow();
                fail(e);
            }
        }
    }

    /** Repassa a falha da tarefa como o chamador espera: {@link IOException} continua sendo, o resto vira {@link IllegalStateException}. */
    private static void fail(ExecutionException e) throws IOException {
        Throwable cause = e.getCause();
        String message = cause == null ? e.getMessage() : cause.getMessage();
        if (cause instanceof IOException) {
            throw new IOException(message, e);
        }
        throw new IllegalStateException(message, e);
    }

    private void copyOne(String rel) throws IOException {
        Path relPath = Paths.get(rel).normalize();
        if (relPath.isAbsolute() || relPath.startsWith("..") || relPath.toString().isEmpty()) {
            return;
        }
        Path from = src.resolve(relPath);
        if (!isRegularSmallFile(from) || !parentStaysInside(from)) {
            return;
        }
        Path to = target.resolve(relPath).normalize();
        if (!to.startsWith(target)) {
            return;
        }
        ensureDirectory(to.getParent());
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Link simbólico, submódulo (pasta) ou arquivo apagado: não entram. Uma consulta só dá tipo e tamanho. */
    private static boolean isRegularSmallFile(Path from) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(from, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attrs.isRegularFile() && attrs.size() <= MAX_FILE_BYTES;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Um componente do meio pode ter virado link para fora da pasta: o caminho real da pasta do arquivo precisa
     * continuar dentro dela (o arquivo em si já não é link). A checagem vale para a pasta toda, então roda uma vez.
     */
    private boolean parentStaysInside(Path from) throws IOException {
        Path parent = from.getParent();
        if (parent == null || trustedParents.contains(parent)) {
            return true;
        }
        if (!parent.toRealPath().startsWith(src)) {
            return false;
        }
        trustedParents.add(parent);
        return true;
    }

    private void ensureDirectory(Path dir) throws IOException {
        if (!createdDirs.contains(dir)) {
            // Só marca depois de criar: outra thread não pode achar que a pasta existe antes da hora.
            Files.createDirectories(dir);
            createdDirs.add(dir);
        }
    }
}
