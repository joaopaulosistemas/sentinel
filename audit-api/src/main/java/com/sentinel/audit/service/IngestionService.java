package com.sentinel.audit.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Serviço responsável por clonar repositórios Git e gerenciar diretórios temporários
 * para análise de código.
 * <p>
 * Medidas de segurança aplicadas ao clone:
 * <ul>
 *   <li>apenas URLs {@code https://} (sem {@code file://}, {@code ext::}, {@code ssh}, etc.);</li>
 *   <li>lista opcional de hosts permitidos ({@code sentinel.audit.git.allowed-hosts});</li>
 *   <li>o token <b>nunca</b> vai na URL: é enviado em um header HTTP via variável de ambiente
 *       do processo, e só para hosts listados em {@code sentinel.audit.git.token-hosts};</li>
 *   <li>symlinks do repositório não são materializados ({@code core.symlinks=false});</li>
 *   <li>timeout no clone e saída capturada em arquivo (sem risco de travar por buffer cheio).</li>
 * </ul>
 */
@ApplicationScoped
public class IngestionService {

    private static final Logger LOG = Logger.getLogger(IngestionService.class);

    private static final Pattern BRANCH_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]*$");
    private static final int MAX_ERROR_OUTPUT_CHARS = 1500;

    @ConfigProperty(name = "sentinel.audit.git.token")
    Optional<String> gitToken;

    @ConfigProperty(name = "sentinel.audit.git.clone-dir", defaultValue = "/tmp/sentinel-audit")
    String cloneBaseDir;

    /** Hosts em que o clone é permitido. Vazio = qualquer host https. */
    @ConfigProperty(name = "sentinel.audit.git.allowed-hosts")
    Optional<List<String>> allowedHosts;

    /** Hosts que podem receber o token de autenticação. */
    @ConfigProperty(name = "sentinel.audit.git.token-hosts", defaultValue = "github.com")
    List<String> tokenHosts;

    @ConfigProperty(name = "sentinel.audit.git.clone-timeout-seconds", defaultValue = "300")
    long cloneTimeoutSeconds;

    /** Quantos commits baixar (para a varredura de segredos no histórico). 0 ou 1 = clone raso. */
    @ConfigProperty(name = "sentinel.audit.git.history-depth", defaultValue = "100")
    int historyDepth;

    /**
     * Clona o repositório Git na branch especificada em um diretório temporário único.
     *
     * @param repoUrl URL https do repositório Git
     * @param branch  branch a ser clonada (opcional)
     * @param token   token de autenticação (opcional; se vazio, usa o token global)
     * @return {@link Path} do diretório onde o repositório foi clonado
     * @throws IllegalArgumentException se a URL ou a branch forem inválidas
     * @throws IllegalStateException    se o clone falhar
     */
    public Path cloneRepository(String repoUrl, String branch, String token) {
        URI uri = validateRepoUrl(repoUrl);
        validateBranch(branch);

        String effectiveToken = resolveToken(token);
        boolean sendToken = !effectiveToken.isBlank() && isTokenHost(uri.getHost());
        if (!effectiveToken.isBlank() && !sendToken) {
            LOG.warnf("Token Git NÃO enviado: o host %s não está em sentinel.audit.git.token-hosts", uri.getHost());
        }

        Path basePath = Paths.get(cloneBaseDir).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(UUID.randomUUID().toString()).normalize();
        Path logFile = null;

        try {
            Files.createDirectories(basePath);
            logFile = Files.createTempFile(basePath, "git-clone-", ".log");

            List<String> command = buildCloneCommand(uri.toString(), branch, targetPath);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile.toFile());
            configureEnvironment(pb.environment(), sendToken ? effectiveToken : null);

            LOG.infof("Clonando repositório %s (branch: %s)", uri, branch == null || branch.isBlank() ? "padrão" : branch);

            Process process = pb.start();
            boolean finished = process.waitFor(cloneTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git clone excedeu o tempo limite de " + cloneTimeoutSeconds + "s");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("git clone falhou (exit " + process.exitValue() + "): "
                        + readTail(logFile, effectiveToken));
            }
            return targetPath;

        } catch (IOException e) {
            cleanupDirectory(targetPath);
            throw new IllegalStateException("Erro ao executar git clone: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupDirectory(targetPath);
            throw new IllegalStateException("git clone interrompido", e);
        } catch (RuntimeException e) {
            cleanupDirectory(targetPath);
            throw e;
        } finally {
            deleteQuietly(logFile);
        }
    }

    /**
     * Remove recursivamente o diretório clonado após a análise.
     * Não segue symlinks e tolera arquivos somente-leitura.
     *
     * @param dir diretório a ser removido
     */
    public void cleanupDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        // arquivos somente-leitura (ex.: objetos do .git em alguns sistemas de arquivos)
                        file.toFile().setWritable(true);
                        Files.delete(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
            LOG.debugf("Diretório removido: %s", dir);
        } catch (IOException e) {
            LOG.warnf("Não foi possível remover o diretório %s: %s", dir, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ validação

    URI validateRepoUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("A URL do repositório é obrigatória");
        }
        URI uri;
        try {
            uri = new URI(repoUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL de repositório inválida");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Somente URLs https:// são aceitas para o repositório");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL de repositório sem host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("A URL do repositório não pode conter credenciais; use o token configurado");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (allowedHosts.isPresent() && !allowedHosts.get().isEmpty()
                && allowedHosts.get().stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).noneMatch(host::equals)) {
            throw new IllegalArgumentException("Host não permitido para auditoria: " + host);
        }
        return uri;
    }

    void validateBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            return;
        }
        if (branch.length() > 255 || !BRANCH_PATTERN.matcher(branch).matches() || branch.contains("..")) {
            throw new IllegalArgumentException("Nome de branch inválido");
        }
    }

    // ------------------------------------------------------------------ internos

    private String resolveToken(String token) {
        if (token != null && !token.isBlank()) {
            return token;
        }
        return gitToken.orElse("");
    }

    private boolean isTokenHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return tokenHosts.stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).anyMatch(h::equals);
    }

    List<String> buildCloneCommand(String url, String branch, Path targetPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        // Só HTTPS; nunca materializar symlinks do repositório auditado.
        cmd.add("-c");
        cmd.add("protocol.allow=never");
        cmd.add("-c");
        cmd.add("protocol.https.allow=always");
        cmd.add("-c");
        cmd.add("core.symlinks=false");
        cmd.add("clone");
        cmd.add("--depth");
        cmd.add(String.valueOf(Math.max(1, historyDepth)));
        cmd.add("--single-branch");
        cmd.add("--no-tags");
        if (branch != null && !branch.isBlank()) {
            cmd.add("--branch");
            cmd.add(branch);
        }
        cmd.add("--");
        cmd.add(url);
        cmd.add(targetPath.toString());
        return cmd;
    }

    /** Arquivos alterados no clone em relação ao branch base (auditoria de PR). Usa as mesmas credenciais e restrições do clone. */
    public ChangedFiles.Result changedFiles(String repoUrl, Path clonedPath, String baseRef) {
        URI uri = validateRepoUrl(repoUrl);
        String token = resolveToken(null);
        Map<String, String> env = new java.util.HashMap<>();
        configureEnvironment(env, !token.isBlank() && isTokenHost(uri.getHost()) ? token : null);
        return ChangedFiles.compute(clonedPath, baseRef,
                List.of("-c", "protocol.allow=never", "-c", "protocol.https.allow=always", "-c", "core.symlinks=false"),
                env, cloneTimeoutSeconds);
    }

    private void configureEnvironment(Map<String, String> env, String token) {
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GIT_ASKPASS", "echo");
        env.put("GCM_INTERACTIVE", "never");
        env.remove("GIT_SSH_COMMAND");
        if (token != null) {
            String basic = Base64.getEncoder()
                    .encodeToString(("x-access-token:" + token).getBytes(StandardCharsets.UTF_8));
            // Config via ambiente: o token não aparece na linha de comando, na URL nem em .git/config.
            env.put("GIT_CONFIG_COUNT", "1");
            env.put("GIT_CONFIG_KEY_0", "http.extraHeader");
            env.put("GIT_CONFIG_VALUE_0", "Authorization: Basic " + basic);
        }
    }

    private String readTail(Path logFile, String token) {
        try {
            String output = Files.readString(logFile, StandardCharsets.UTF_8).strip();
            if (!token.isBlank()) {
                output = output.replace(token, "***");
            }
            return output.length() > MAX_ERROR_OUTPUT_CHARS
                    ? output.substring(output.length() - MAX_ERROR_OUTPUT_CHARS)
                    : output;
        } catch (IOException e) {
            return "(saída do git indisponível)";
        }
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.debugf("Não foi possível apagar %s: %s", file, e.getMessage());
        }
    }
}
