package com.sentinel.audit.service;

import com.sentinel.audit.model.Finding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Arquivos alterados em relação a um branch base (auditoria de PR), via {@code git diff} no clone já feito.
 * O {@code baseRef} é validado antes de virar argumento do git; nada passa por shell.
 */
public final class ChangedFiles {

    static final Pattern REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}$");
    private static final int FETCH_DEPTH = 200;
    private static final long MAX_OUTPUT_BYTES = 8L * 1024 * 1024;

    private ChangedFiles() {}

    /**
     * @param files       caminhos relativos (com {@code /}) alterados; {@code null} quando não foi possível comparar
     * @param approximate verdadeiro quando não havia ancestral comum no histórico baixado e a comparação foi entre as pontas
     * @param note        motivo, quando {@code files} é nulo
     */
    public record Result(Set<String> files, boolean approximate, String note) {
        static Result unavailable(String note) {
            return new Result(null, false, note);
        }
    }

    /**
     * @param gitPrefix opções {@code -c ...} colocadas antes do subcomando (ex.: restringir protocolos)
     * @param env       variáveis de ambiente do git (credenciais por cabeçalho, sem prompt)
     */
    public static Result compute(Path repo, String baseRef, List<String> gitPrefix, Map<String, String> env, long timeoutSeconds) {
        if (baseRef == null || !REF.matcher(baseRef).matches() || baseRef.contains("..") || baseRef.endsWith("/")
                || baseRef.endsWith(".lock")) {
            return Result.unavailable("referência base inválida");
        }
        try {
            // Traz o branch base (raso). Se já existir localmente ou o remoto estiver indisponível, segue com o que houver.
            git(repo, gitPrefix, env, timeoutSeconds, "fetch", "--no-tags", "--depth=" + FETCH_DEPTH, "origin",
                    "+refs/heads/" + baseRef + ":refs/remotes/origin/" + baseRef);

            String ref = null;
            for (String candidate : List.of("refs/remotes/origin/" + baseRef, "refs/heads/" + baseRef)) {
                if (git(repo, gitPrefix, env, timeoutSeconds, "rev-parse", "--verify", "--quiet", candidate + "^{commit}").exit() == 0) {
                    ref = candidate;
                    break;
                }
            }
            if (ref == null) {
                return Result.unavailable("branch base não encontrado no repositório");
            }
            GitOut diff = git(repo, gitPrefix, env, timeoutSeconds, "diff", "--name-only", "-z", "--diff-filter=ACMR", ref + "...HEAD", "--");
            boolean approximate = false;
            if (diff.exit() != 0) {                                    // clone raso: aprofunda o histórico da própria branch
                git(repo, gitPrefix, env, timeoutSeconds, "fetch", "--no-tags", "--deepen=" + FETCH_DEPTH, "origin");
                diff = git(repo, gitPrefix, env, timeoutSeconds, "diff", "--name-only", "-z", "--diff-filter=ACMR", ref + "...HEAD", "--");
            }
            if (diff.exit() != 0) {                                    // ainda sem ancestral comum
                diff = git(repo, gitPrefix, env, timeoutSeconds, "diff", "--name-only", "-z", "--diff-filter=ACMR", ref, "HEAD", "--");
                approximate = true;
            }
            if (diff.exit() != 0) {
                return Result.unavailable("git diff falhou");
            }
            Set<String> files = new HashSet<>();
            for (String p : diff.out().split("\0")) {
                if (!p.isBlank()) {
                    files.add(normalize(p));
                }
            }
            return new Result(files, approximate, null);
        } catch (IOException e) {
            return Result.unavailable(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.unavailable("interrompido");
        }
    }

    /** Mantém só achados cujo arquivo foi alterado. Achados sem arquivo (ex.: "dependência ausente") não pertencem a um PR. */
    public static List<Finding> filter(List<Finding> findings, Set<String> changed) {
        List<Finding> out = new ArrayList<>();
        for (Finding f : findings) {
            if (f.getFilePath() != null && changed.contains(normalize(f.getFilePath()))) {
                out.add(f);
            }
        }
        return out;
    }

    static String normalize(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p;
    }

    private record GitOut(int exit, String out) {}

    private static GitOut git(Path repo, List<String> prefix, Map<String, String> env, long timeoutSeconds, String... args)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(prefix);
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(repo.toFile());
        pb.environment().putAll(env);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] chunk = new byte[8192];
                int n = in.read(chunk);
                while (n > 0) {
                    if (buf.size() + n <= MAX_OUTPUT_BYTES) {
                        buf.write(chunk, 0, n);
                    }
                    n = in.read(chunk);
                }
            } catch (IOException ignored) {
                // processo encerrado
            }
        });
        reader.setDaemon(true);
        reader.start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new GitOut(-1, "");
        }
        reader.join(2000);
        return new GitOut(process.exitValue(), buf.toString(StandardCharsets.UTF_8));
    }
}
