package com.sentinel.audit.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Obtém pacotes de um repositório Git central (a "fonte da verdade" das regras do time). O repositório inteiro é a
 * raiz de pacotes: cada pasta com {@code pack.yml} é um pacote e {@code profiles/} guarda os perfis.
 * <p>
 * Segurança: a URL e a ref entram como argumentos de processo (nunca num shell) e não podem começar com {@code -}
 * (injeção de opção do git); só {@code https://}, {@code ssh://} e {@code git@host:caminho} são aceitos.
 */
public final class PackSources {

    /** Quantos caracteres do hash da origem nomeiam a pasta no cache. */
    private static final int CACHE_DIR_NAME_CHARS = 16;


    private static final Pattern URL = Pattern.compile("(https://|ssh://)[^\\s]+|git@[A-Za-z0-9.-]+:[^\\s]+");
    private static final Pattern REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]*");

    private PackSources() {}

    /** @param allowFile só para testes: aceita {@code file://} */
    public static Path sync(String url, String ref, Path cacheRoot, long timeoutSeconds, boolean allowFile)
            throws IOException, InterruptedException {
        boolean fileUrl = allowFile && url != null && url.startsWith("file://");
        if (url == null || url.startsWith("-") || (!fileUrl && !URL.matcher(url).matches())) {
            throw new IOException("URL de pacotes inválida (use https://, ssh:// ou git@host:caminho)");
        }
        if (ref == null || !REF.matcher(ref).matches() || ref.contains("..")) {
            throw new IOException("ref de pacotes inválida: " + ref);
        }
        Path dir = cacheRoot.resolve(sha(url + "#" + ref).substring(0, CACHE_DIR_NAME_CHARS));
        Files.createDirectories(cacheRoot);
        if (Files.isDirectory(dir.resolve(".git"))) {
            run(List.of("git", "-C", dir.toString(), "fetch", "--depth", "1", "origin", ref), timeoutSeconds);
            run(List.of("git", "-C", dir.toString(), "reset", "--hard", "FETCH_HEAD"), timeoutSeconds);
        } else {
            run(List.of("git", "clone", "--depth", "1", "--branch", ref, "--", url, dir.toString()), timeoutSeconds);
        }
        return dir;
    }

    private static void run(List<String> cmd, long timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(cmd));
        Map<String, String> env = pb.environment();
        env.put("GIT_TERMINAL_PROMPT", "0");          // nunca ficar esperando usuário/senha
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("git excedeu o tempo limite de " + timeoutSeconds + "s");
        }
        if (p.exitValue() != 0) {
            throw new IOException("git terminou com código " + p.exitValue() + " (" + cmd.get(1).replace("-C", "") + ")");
        }
    }

    private static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
