package com.sentinel.audit.service;

import jakarta.enterprise.context.ApplicationScoped;
import com.sentinel.audit.model.LocalSnapshot;
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
 *   <li>o token <b>nunca</b> vai na URL: é enviado em um header HTTP via variável de ambiente
 *       do processo, e só para os hosts listados no cadastro do sistema (padrão github.com);</li>
 *   <li>symlinks do repositório não são materializados ({@code core.symlinks=false});</li>
 *   <li>timeout no clone e saída capturada em arquivo (sem risco de travar por buffer cheio).</li>
 * </ul>
 */
@ApplicationScoped
public class IngestionService {

    private static final Logger LOG = Logger.getLogger(IngestionService.class);

    private static final Pattern BRANCH_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]*$");
    private static final int MAX_ERROR_OUTPUT_CHARS = 1500;

    @ConfigProperty(name = "sentinel.audit.git.clone-dir", defaultValue = "/tmp/sentinel-audit")
    String cloneBaseDir;

    /**
     * Raiz das pastas locais auditáveis ({@code SENTINEL_LOCAL_ROOT}). Vazia = origem "pasta local" desligada.
     * No Docker é o ponto onde a pasta de projetos do computador é montada, somente leitura.
     */
    @ConfigProperty(name = "sentinel.audit.local.root")
    Optional<String> localRoot = Optional.empty();

    /** Hosts que recebem o token quando o sistema não lista os seus. */
    static final List<String> DEFAULT_TOKEN_HOSTS = List.of("github.com");

    @ConfigProperty(name = "sentinel.audit.git.clone-timeout-seconds", defaultValue = "300")
    long cloneTimeoutSeconds;

    /** Commits baixados quando o sistema não define: o bastante para achar segredos já removidos do código. */
    public static final int DEFAULT_HISTORY_DEPTH = 100;

    /**
     * Clona o repositório Git na branch especificada em um diretório temporário único.
     *
     * @param repoUrl URL https do repositório Git
     * @param branch  branch a ser clonada (opcional)
     * @param token   token de autenticação do sistema (opcional)
     * @return {@link Path} do diretório onde o repositório foi clonado
     * @throws IllegalArgumentException se a URL ou a branch forem inválidas
     * @throws IllegalStateException    se o clone falhar
     */
    public Path cloneRepository(String repoUrl, String branch, String token) {
        return cloneRepository(repoUrl, branch, token, null);
    }

    /**
     * @param hosts hosts que podem receber o token (lista separada por vírgula); vazio = {@code github.com}
     *              (ou o que estiver em {@code sentinel.audit.git.token-hosts}, para o token global)
     */
    public Path cloneRepository(String repoUrl, String branch, String token, String hosts) {
        return cloneRepository(repoUrl, branch, token, hosts, null);
    }

    /** @param historyDepth commits a baixar; {@code null} = {@link #DEFAULT_HISTORY_DEPTH}; 0 ou 1 = clone raso */
    public Path cloneRepository(String repoUrl, String branch, String token, String hosts, Integer historyDepth) {
        URI uri = validateRepoUrl(repoUrl);
        validateBranch(branch);

        String effectiveToken = resolveToken(token);
        boolean sendToken = !effectiveToken.isBlank() && isTokenHost(uri.getHost(), hosts);
        if (!effectiveToken.isBlank() && !sendToken) {
            LOG.warnf("Token Git NÃO enviado: o host %s não está na lista de hosts do token do sistema", uri.getHost());
        }

        Path basePath = Paths.get(cloneBaseDir).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(UUID.randomUUID().toString()).normalize();
        Path logFile = null;

        try {
            Files.createDirectories(basePath);
            logFile = Files.createTempFile(basePath, "git-clone-", ".log");

            List<String> command = buildCloneCommand(uri.toString(), branch, targetPath, historyDepth);
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

    /** SHA do commit em que o clone está, ou nulo se não for possível descobrir. */
    public String headCommit(Path repoDir) {
        if (repoDir == null) {
            return null;
        }
        try {
            Process process = new ProcessBuilder("git", "-C", repoDir.toString(), "rev-parse", "HEAD")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && out.matches("[0-9a-f]{7,64}") ? out : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ------------------------------------------------------------------ pasta local

    /** A origem "pasta local" só existe quando o servidor tem uma raiz configurada e ela existe. */
    public boolean localSourceEnabled() {
        return localSource().isPresent();
    }

    private Optional<LocalSource> localSource() {
        return LocalSource.ofRoot(localRoot == null ? null : localRoot.orElse(null), cloneTimeoutSeconds);
    }

    private LocalSource requireLocalSource() {
        return localSource().orElseThrow(() -> new IllegalStateException(
                "A origem \"pasta local\" está desligada neste servidor: defina SENTINEL_LOCAL_ROOT e monte a pasta de projetos"));
    }

    /** Confere a pasta (é um repositório Git? qual branch? o que não foi commitado?) sem copiar nada. */
    public LocalSource.PathCheck checkLocalPath(String relative, String branch) {
        Optional<LocalSource> source = localSource();
        if (source.isEmpty()) {
            return new LocalSource.PathCheck(false, "A origem \"pasta local\" está desligada neste servidor (defina SENTINEL_LOCAL_ROOT)",
                    null, null, null, null);
        }
        return source.get().inspect(relative, branch);
    }

    /**
     * Valida e normaliza o caminho para guardar no cadastro: recusa o que sai da raiz, não existe ou não é um
     * repositório Git. Devolve o caminho no formato guardado (com {@code /}, sem barra no fim).
     *
     * @throws IllegalArgumentException com a mensagem para o usuário
     */
    public String validateLocalPath(String relative, String branch) {
        LocalSource.PathCheck check = checkLocalPath(relative, branch);
        if (!check.ok()) {
            throw new IllegalArgumentException(check.message());
        }
        return LocalSource.normalizeInput(relative);
    }

    /**
     * Copia a pasta local para um diretório temporário único, no mesmo lugar onde os clones ficam. A pasta de origem
     * só é lida. O chamador apaga a cópia com {@link #cleanupDirectory(Path)}.
     */
    public Path snapshotLocal(String relative, String branch, LocalSnapshot mode, Integer historyDepth) {
        LocalSource source = requireLocalSource();
        Path basePath = Paths.get(cloneBaseDir).toAbsolutePath().normalize();
        try {
            LOG.infof("Copiando a pasta local %s (branch: %s, %s)", relative,
                    branch == null || branch.isBlank() ? "atual" : branch, mode == null ? LocalSnapshot.COMMIT : mode);
            return source.snapshot(relative, branch, mode == null ? LocalSnapshot.COMMIT : mode, historyDepth, basePath);
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao copiar a pasta local: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A cópia da pasta local foi interrompida", e);
        }
    }

    /** SHA da ponta da branch na pasta local; nulo se não der para saber (serve ao aviso de auditoria desatualizada). */
    public String localHeadSha(String relative, String branch) {
        return localSource().map(s -> s.headSha(relative, branch)).orElse(null);
    }

    /** Arquivos alterados em relação à branch base, numa cópia {@link LocalSnapshot#COMMIT} (auditoria de PR). */
    public ChangedFiles.Result changedFilesLocal(String relative, Path clonedPath, String baseRef) {
        Path src;
        try {
            src = requireLocalSource().resolve(relative);
        } catch (RuntimeException e) {
            return ChangedFiles.Result.unavailable("pasta local indisponível");
        }
        Path trust = null;
        try {
            Map<String, String> env = new java.util.HashMap<>();
            env.put("GIT_TERMINAL_PROMPT", "0");
            // O "git fetch" local chama o upload-pack na pasta de origem, que só confia na configuração global.
            trust = requireLocalSource().trustConfig(src);
            env.put("GIT_CONFIG_GLOBAL", trust.toString());
            return ChangedFiles.compute(clonedPath, baseRef,
                    List.of("-c", "protocol.file.allow=always", "-c", "safe.directory=" + src, "-c", "core.symlinks=false",
                            "-c", "core.hooksPath=/dev/null"),
                    env, cloneTimeoutSeconds);
        } catch (java.io.IOException e) {
            return ChangedFiles.Result.unavailable("pasta local indisponível");
        } finally {
            if (trust != null) {
                try {
                    java.nio.file.Files.deleteIfExists(trust);
                } catch (java.io.IOException ignored) {
                    // arquivo temporário; o sistema operacional limpa depois
                }
            }
        }
    }

    // ------------------------------------------------------------------ verificação rápida

    /** Resultado da verificação de um repositório antes de cadastrá-lo ou auditá-lo. */
    public record RepoCheck(boolean reachable, String defaultBranch, Boolean branchFound, String message, Long fileCount) {

        public RepoCheck(boolean reachable, String defaultBranch, Boolean branchFound, String message) {
            this(reachable, defaultBranch, branchFound, message, null);
        }
    }

    private static final long CHECK_TIMEOUT_SECONDS = 20;

    /**
     * Confere, sem clonar, se o repositório responde e qual é a branch padrão ({@code git ls-remote --symref}).
     * Usa as mesmas regras de URL, token e protocolo do clone. Nunca lança exceção por repositório inacessível:
     * o motivo vem em {@link RepoCheck#message()}.
     *
     * @param branch branch que o usuário pretende auditar (opcional); se informada, diz se ela existe
     */
    public RepoCheck checkRepository(String repoUrl, String branch) {
        return checkRepository(repoUrl, branch, null, null);
    }

    public RepoCheck checkRepository(String repoUrl, String branch, String tokenOverride, String hosts) {
        URI uri;
        try {
            uri = validateRepoUrl(repoUrl);
            validateBranch(branch);
        } catch (IllegalArgumentException e) {
            return new RepoCheck(false, null, null, e.getMessage());
        }
        String token = resolveToken(tokenOverride);
        boolean sendToken = !token.isBlank() && isTokenHost(uri.getHost(), hosts);
        Path logFile = null;
        try {
            Path basePath = Paths.get(cloneBaseDir).toAbsolutePath().normalize();
            Files.createDirectories(basePath);
            logFile = Files.createTempFile(basePath, "git-check-", ".log");
            List<String> command = new ArrayList<>(List.of("git", "-c", "protocol.allow=never", "-c", "protocol.https.allow=always",
                    "ls-remote", "--symref", "--", uri.toString(), "HEAD"));
            if (branch != null && !branch.isBlank()) {
                command.add("refs/heads/" + branch);
            }
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile.toFile());
            configureEnvironment(pb.environment(), sendToken ? token : null);
            Process process = pb.start();
            if (!process.waitFor(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new RepoCheck(false, null, null, "O repositório não respondeu em " + CHECK_TIMEOUT_SECONDS + "s");
            }
            String output = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new RepoCheck(false, null, null, friendlyGitError(readTail(logFile, token)));
            }
            String defaultBranch = parseDefaultBranch(output);
            Boolean branchFound = branch == null || branch.isBlank() ? null : branchListed(output, branch);
            String target = branch != null && !branch.isBlank() && Boolean.TRUE.equals(branchFound) ? branch : defaultBranch;
            return new RepoCheck(true, defaultBranch, branchFound, null, countFiles(uri.toString(), target, sendToken ? token : null));
        } catch (IOException e) {
            return new RepoCheck(false, null, null, "Não foi possível executar o git neste servidor");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RepoCheck(false, null, null, "Verificação interrompida");
        } finally {
            deleteQuietly(logFile);
        }
    }

    /**
     * Conta os arquivos da branch sem baixar o conteúdo (clone raso, sem blobs, só a árvore). Melhor esforço: se
     * demorar mais que o limite ou falhar, devolve nulo e a verificação segue sem o número.
     */
    Long countFiles(String repoUrl, String branch, String token) {
        Path dir = null;
        try {
            Path basePath = Paths.get(cloneBaseDir).toAbsolutePath().normalize();
            dir = basePath.resolve("count-" + UUID.randomUUID()).normalize();
            List<String> clone = new ArrayList<>(List.of("git", "-c", "protocol.allow=never", "-c", "protocol.https.allow=always",
                    "clone", "--depth", "1", "--filter=blob:none", "--no-checkout", "--single-branch"));
            if (branch != null && !branch.isBlank()) {
                clone.addAll(List.of("--branch", branch));
            }
            clone.addAll(List.of("--", repoUrl, dir.toString()));
            ProcessBuilder pb = new ProcessBuilder(clone).redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            configureEnvironment(pb.environment(), token);
            Process cloneProc = pb.start();
            if (!cloneProc.waitFor(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS) || cloneProc.exitValue() != 0) {
                cloneProc.destroyForcibly();
                return null;
            }
            Process ls = new ProcessBuilder("git", "-C", dir.toString(), "ls-tree", "-r", "--name-only", "HEAD")
                    .redirectErrorStream(true).start();
            long count;
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(ls.getInputStream(), StandardCharsets.UTF_8))) {
                count = reader.lines().count();
            }
            ls.waitFor(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return count;
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            cleanupDirectory(dir);
        }
    }

    /**
     * SHA da ponta de uma branch no remoto, sem clonar ({@code git ls-remote}). Melhor esforço: nulo se o
     * repositório não responde, a branch não existe ou a URL é inválida.
     */
    public String remoteHeadSha(String repoUrl, String branch, String tokenOverride, String hosts) {
        if (branch == null || branch.isBlank()) {
            return null;
        }
        Path logFile = null;
        try {
            URI uri = validateRepoUrl(repoUrl);
            validateBranch(branch);
            String token = resolveToken(tokenOverride);
            boolean sendToken = !token.isBlank() && isTokenHost(uri.getHost(), hosts);
            Path basePath = Paths.get(cloneBaseDir).toAbsolutePath().normalize();
            Files.createDirectories(basePath);
            logFile = Files.createTempFile(basePath, "git-head-", ".log");
            ProcessBuilder pb = new ProcessBuilder("git", "-c", "protocol.allow=never", "-c", "protocol.https.allow=always",
                    "ls-remote", "--", uri.toString(), "refs/heads/" + branch);
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile.toFile());
            configureEnvironment(pb.environment(), sendToken ? token : null);
            Process process = pb.start();
            if (!process.waitFor(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            return parseRefSha(Files.readString(logFile, StandardCharsets.UTF_8), branch);
        } catch (IllegalArgumentException | IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            deleteQuietly(logFile);
        }
    }

    /** SHA da linha "&lt;sha&gt;\trefs/heads/&lt;branch&gt;" da saída do ls-remote. */
    static String parseRefSha(String lsRemoteOutput, String branch) {
        if (lsRemoteOutput == null) {
            return null;
        }
        String suffix = "\trefs/heads/" + branch;
        for (String line : lsRemoteOutput.split("\\R")) {
            if (line.endsWith(suffix)) {
                String sha = line.substring(0, line.length() - suffix.length()).trim();
                return sha.isEmpty() ? null : sha;
            }
        }
        return null;
    }

    static boolean branchListed(String lsRemoteOutput, String branch) {
        String suffix = "\trefs/heads/" + branch;
        return lsRemoteOutput.lines().anyMatch(l -> l.endsWith(suffix));
    }

    /** Extrai a branch padrão da saída de {@code git ls-remote --symref} ("ref: refs/heads/main\tHEAD"). */
    static String parseDefaultBranch(String lsRemoteOutput) {
        if (lsRemoteOutput == null) {
            return null;
        }
        for (String line : lsRemoteOutput.split("\\R")) {
            if (line.startsWith("ref: refs/heads/") && line.endsWith("\tHEAD")) {
                return line.substring("ref: refs/heads/".length(), line.length() - "\tHEAD".length()).trim();
            }
        }
        return null;
    }

    /** Traduz as falhas mais comuns do git em uma frase que o usuário entende. */
    static String friendlyGitError(String raw) {
        String t = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (t.contains("not found") || t.contains("repository") && t.contains("does not exist")) {
            return "Repositório não encontrado (confira o endereço ou, se for privado, o token configurado)";
        }
        if (t.contains("authentication failed") || t.contains("could not read username") || t.contains("403")) {
            return "Sem permissão para acessar o repositório (configure o token do Git na API)";
        }
        if (t.contains("could not resolve host") || t.contains("unable to access")) {
            return "Não foi possível alcançar o servidor do repositório";
        }
        return "Não foi possível acessar o repositório";
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
        return "";
    }

    private boolean isTokenHost(String host, String hosts) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        List<String> allowed = hosts == null || hosts.isBlank() ? DEFAULT_TOKEN_HOSTS : List.of(hosts.split(","));
        return allowed.stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).anyMatch(h::equals);
    }

    List<String> buildCloneCommand(String url, String branch, Path targetPath) {
        return buildCloneCommand(url, branch, targetPath, null);
    }

    List<String> buildCloneCommand(String url, String branch, Path targetPath, Integer historyDepth) {
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
        cmd.add(String.valueOf(Math.max(1, historyDepth == null ? DEFAULT_HISTORY_DEPTH : historyDepth)));
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
    public ChangedFiles.Result changedFiles(String repoUrl, Path clonedPath, String baseRef, String tokenOverride, String hosts) {
        URI uri = validateRepoUrl(repoUrl);
        String token = resolveToken(tokenOverride);
        Map<String, String> env = new java.util.HashMap<>();
        configureEnvironment(env, !token.isBlank() && isTokenHost(uri.getHost(), hosts) ? token : null);
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
