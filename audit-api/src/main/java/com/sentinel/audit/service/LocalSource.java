package com.sentinel.audit.service;

import com.sentinel.audit.model.LocalSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Origem "pasta local": lê o código de uma pasta do servidor em vez de clonar uma URL.
 * <p>
 * Regras de segurança (esta classe é a única porta de entrada de caminhos de disco):
 * <ul>
 *   <li>só vale <b>dentro da raiz configurada</b>; sem raiz, o recurso fica desligado;</li>
 *   <li>o caminho é sempre relativo à raiz: {@code ..} e links simbólicos que escapem dela são recusados;</li>
 *   <li>a pasta de origem <b>nunca é alterada nem apagada</b>: a auditoria trabalha numa cópia;</li>
 *   <li>só repositórios Git (a pasta que contém o {@code .git});</li>
 *   <li>symlinks do projeto não são copiados nem seguidos; hooks do repositório não rodam.</li>
 * </ul>
 */
public final class LocalSource {

    private static final int BRANCH_MAX_CHARS = 255;
    private static final int OUTPUT_TAIL_CHARS = 600;


    private static final Pattern BRANCH = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]*$");
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_OUTPUT_BYTES = 64L * 1024 * 1024;
    private static final int MAX_PATH_CHARS = 500;
    /** Limite da verificação mostrada na tela: um repositório enorme não pode deixar o formulário esperando. */
    private static final long CHECK_TIMEOUT_SECONDS = 15;
    /** Contagem de arquivos e de alterações: informativa, então tem prazo curto (o navegador desiste aos 30s). */
    private static final long COUNT_TIMEOUT_SECONDS = 6;
    /** Commits baixados quando o sistema não define: o bastante para achar segredos já removidos do código. */
    static final int DEFAULT_HISTORY_DEPTH = 100;

    /** Resultado da verificação de uma pasta, mostrado antes de salvar o sistema. */
    public record PathCheck(boolean ok, String message, String defaultBranch, Boolean branchFound,
                            Long fileCount, Long uncommittedFiles) {
        static PathCheck fail(String message) {
            return new PathCheck(false, message, null, null, null, null);
        }
    }

    private final Path root;
    private final long timeoutSeconds;

    public LocalSource(Path root, long timeoutSeconds) throws IOException {
        this.root = root.toRealPath();
        this.timeoutSeconds = timeoutSeconds;
    }

    /** A raiz configurada, ou vazio se o recurso está desligado ou a raiz não existe. */
    public static Optional<LocalSource> ofRoot(String configured, long timeoutSeconds) {
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        try {
            Path p = Paths.get(configured.trim());
            if (!p.isAbsolute() || !Files.isDirectory(p)) {
                return Optional.empty();
            }
            return Optional.of(new LocalSource(p, timeoutSeconds));
        } catch (IOException | InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** O caminho do jeito que deve ser guardado: sem espaços nas pontas, com {@code /} e sem barra no fim. */
    public static String normalizeInput(String relative) {
        if (relative == null) {
            return "";
        }
        String s = relative.trim().replace('\\', '/');
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    static void validateBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            return;
        }
        if (branch.length() > BRANCH_MAX_CHARS || !BRANCH.matcher(branch).matches() || branch.contains("..")) {
            throw new IllegalArgumentException("Nome de branch inválido");
        }
    }

    // ------------------------------------------------------------------ caminho

    /**
     * Resolve o caminho informado para a pasta real, garantindo que fica dentro da raiz.
     *
     * @throws IllegalArgumentException com mensagem pronta para o usuário (nunca revela caminhos do servidor)
     */
    public Path resolve(String relative) {
        String typed = normalizeInput(relative);
        if (typed.isEmpty()) {
            throw new IllegalArgumentException("Informe a pasta do projeto");
        }
        if (typed.length() > MAX_PATH_CHARS || typed.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Caminho de pasta inválido");
        }
        Path wanted;
        try {
            wanted = Paths.get(typed);
            if (wanted.isAbsolute()) {
                // Aceita o caminho como o servidor o enxerga (ex.: /projects/app), desde que esteja na raiz.
                Path abs = wanted.normalize();
                if (!abs.startsWith(root)) {
                    throw new IllegalArgumentException("Use um caminho relativo à raiz de projetos do servidor");
                }
                wanted = root.relativize(abs);
            }
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Caminho de pasta inválido", e);
        }
        Path candidate = root.resolve(wanted).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            throw new IllegalArgumentException("A pasta precisa ficar dentro da raiz de projetos");
        }
        if (!Files.exists(candidate)) {
            throw new IllegalArgumentException("Pasta não encontrada dentro da raiz de projetos");
        }
        Path real;
        try {
            real = candidate.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Pasta não encontrada dentro da raiz de projetos", e);
        }
        if (!real.startsWith(root) || real.equals(root)) {
            throw new IllegalArgumentException("A pasta aponta para fora da raiz de projetos");
        }
        if (!Files.isDirectory(real)) {
            throw new IllegalArgumentException("O caminho informado não é uma pasta");
        }
        return real;
    }

    // ------------------------------------------------------------------ verificação

    /** Confere a pasta sem copiá-la: é um repositório Git, qual a branch, quantos arquivos e o que não foi commitado. */
    public PathCheck inspect(String relative, String branch) {
        Path dir;
        try {
            dir = resolve(relative);
            validateBranch(branch);
        } catch (IllegalArgumentException e) {
            return PathCheck.fail(e.getMessage());
        }
        try {
            String notRepo = notARepositoryRoot(dir);
            if (notRepo != null) {
                return PathCheck.fail(notRepo);
            }
            Out head = git(dir, CHECK_TIMEOUT_SECONDS, "symbolic-ref", "--short", "-q", "HEAD");
            String current = head.exit() == 0 ? head.text().strip() : null;
            Boolean branchFound = null;
            if (branch != null && !branch.isBlank()) {
                branchFound = branchExists(dir, branch);
            }
            // Contagens são só informativas e ficam lentas em repositório grande ou em disco de rede (/mnt/c no WSL):
            // com tempo curto, a pasta continua válida e as contagens saem em branco em vez de a verificação inteira falhar.
            Long fileCount = optionalCount(dir, '\0', "ls-files", "-z");
            Long uncommitted = optionalCount(dir, '\n', "status", "--porcelain");
            if (current == null && (branch == null || branch.isBlank())) {
                return new PathCheck(true, "O repositório está sem branch ativa (HEAD solto); informe a branch a auditar",
                        null, null, fileCount, uncommitted);
            }
            return new PathCheck(true, null, current, branchFound, fileCount, uncommitted);
        } catch (IllegalStateException e) {
            return PathCheck.fail("A verificação demorou mais de " + CHECK_TIMEOUT_SECONDS + "s; tente de novo ou use uma pasta menor");
        } catch (IOException e) {
            return PathCheck.fail("Não foi possível executar o git neste servidor");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PathCheck.fail("Verificação interrompida");
        }
    }

    /** Mensagem de erro se a pasta não for a raiz de um repositório Git; nulo se estiver tudo certo. */
    private String notARepositoryRoot(Path dir) throws IOException, InterruptedException {
        Out top = git(dir, CHECK_TIMEOUT_SECONDS, "rev-parse", "--show-toplevel");
        if (top.exit() != 0) {
            return "A pasta não é um repositório Git. Informe a pasta que contém o .git";
        }
        if (!Paths.get(top.text().strip()).toRealPath().equals(dir)) {
            return "Informe a raiz do repositório (a pasta que contém o .git), não uma subpasta";
        }
        return null;
    }

    /** " Branches da pasta: a, b, c" (até 10), para a pessoa saber qual informar; vazio se não der para listar. */
    private String availableBranches(Path dir) {
        try {
            Out out = git(dir, CHECK_TIMEOUT_SECONDS, "for-each-ref", "--format=%(refname:short)", "--count=10", "refs/heads");
            String names = out.exit() == 0 ? String.join(", ", out.text().strip().split("\\R")) : "";
            return names.isBlank() ? "" : " Branches da pasta: " + names + ".";
        } catch (IOException | IllegalStateException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private boolean branchExists(Path dir, String branch) throws IOException, InterruptedException {
        return git(dir, CHECK_TIMEOUT_SECONDS, "show-ref", "--verify", "--quiet", "refs/heads/" + branch).exit() == 0;
    }

    /** SHA da ponta da branch na pasta (ou do HEAD, sem branch). Melhor esforço: nulo se não for possível saber. */
    public String headSha(String relative, String branch) {
        try {
            Path dir = resolve(relative);
            validateBranch(branch);
            String ref = branch == null || branch.isBlank() ? "HEAD" : "refs/heads/" + branch;
            Out out = git(dir, CHECK_TIMEOUT_SECONDS, "rev-parse", "--verify", "--quiet", ref + "^{commit}");
            String sha = out.text().strip();
            return out.exit() == 0 && sha.matches("[0-9a-f]{7,64}") ? sha : null;
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Conta linhas/itens da saída de um comando git dentro de {@link #COUNT_TIMEOUT_SECONDS}; nulo se demorar ou falhar. */
    private Long optionalCount(Path dir, char separator, String... args) throws IOException, InterruptedException {
        try {
            Out out = git(dir, COUNT_TIMEOUT_SECONDS, args);
            return out.exit() == 0 ? countOf(out.text(), separator) : null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ cópia

    /**
     * Cria uma cópia da pasta em {@code baseDir/<uuid>} para a auditoria analisar (e depois apagar).
     * A pasta de origem só é lida.
     *
     * @param historyDepth commits a copiar no modo {@link LocalSnapshot#COMMIT}; nulo = padrão; 0 ou 1 = só o último
     */
    public Path snapshot(String relative, String branch, LocalSnapshot mode, Integer historyDepth, Path baseDir)
            throws IOException, InterruptedException {
        Path src = resolve(relative);
        validateBranch(branch);
        // Verificação leve (sem contar arquivos nem rodar "git status", lentos em repositório grande).
        String notRepo = notARepositoryRoot(src);
        if (notRepo != null) {
            throw new IllegalArgumentException(notRepo);
        }
        if (branch != null && !branch.isBlank() && !branchExists(src, branch)) {
            throw new IllegalArgumentException("A branch '" + branch + "' não existe na pasta." + availableBranches(src));
        }
        Files.createDirectories(baseDir);
        Path target = baseDir.toAbsolutePath().normalize().resolve(UUID.randomUUID().toString()).normalize();
        try {
            if (mode == LocalSnapshot.TREE) {
                copyWorkingTree(src, target);
            } else {
                cloneCommit(src, branch, historyDepth, target);
            }
            return target;
        } catch (IOException | InterruptedException | RuntimeException e) {
            deleteTree(target);
            throw e;
        }
    }

    private void cloneCommit(Path src, String branch, Integer historyDepth, Path target) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(safeGit(src));
        // file:// é o que faz o --depth valer num clone local; o resto só deixa o git ler a pasta de origem.
        cmd.addAll(List.of("-c", "protocol.file.allow=always", "-c", "core.symlinks=false", "-c", "core.hooksPath=/dev/null",
                "clone", "--depth", String.valueOf(Math.max(1, historyDepth == null ? DEFAULT_HISTORY_DEPTH : historyDepth)),
                "--single-branch", "--no-tags"));
        if (branch != null && !branch.isBlank()) {
            cmd.addAll(List.of("--branch", branch));
        }
        cmd.addAll(List.of("--", "file://" + src, target.toString()));
        // O clone local chama outro processo git (upload-pack) que não herda o "-c safe.directory"; ele lê o
        // arquivo apontado por GIT_CONFIG_GLOBAL.
        Path trust = trustConfig(src);
        Out out;
        try {
            out = run(cmd, timeoutSeconds, Map.of("GIT_CONFIG_GLOBAL", trust.toString()));
        } finally {
            Files.deleteIfExists(trust);
        }
        if (out.exit() != 0) {
            throw new IllegalStateException("Não foi possível copiar a pasta: " + tail(out.text()));
        }
    }

    /** Copia só o que o git conhece (versionado e não versionado que não é ignorado): sem node_modules, target etc. */
    private void copyWorkingTree(Path src, Path target) throws IOException, InterruptedException {
        Out list = git(src, "ls-files", "-z", "--cached", "--others", "--exclude-standard");
        if (list.exit() != 0) {
            throw new IllegalStateException("Não foi possível listar os arquivos da pasta: " + tail(list.text()));
        }
        Files.createDirectories(target);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(timeoutSeconds, 1));
        for (String rel : list.text().split("\0")) {
            if (rel.isEmpty()) {
                continue;
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("A cópia da pasta excedeu o tempo limite de " + timeoutSeconds + "s");
            }
            copyOne(src, target, rel);
        }
    }

    private static void copyOne(Path src, Path target, String rel) throws IOException {
        Path relPath = Paths.get(rel).normalize();
        if (relPath.isAbsolute() || relPath.startsWith("..") || relPath.toString().isEmpty()) {
            return;
        }
        Path from = src.resolve(relPath);
        // Link simbólico, submódulo (pasta) ou arquivo apagado: não entram.
        if (!Files.isRegularFile(from, LinkOption.NOFOLLOW_LINKS) || Files.size(from) > MAX_FILE_BYTES) {
            return;
        }
        // Um componente do meio pode ter virado link para fora da pasta: o caminho real precisa continuar dentro dela.
        if (!from.toRealPath().startsWith(src)) {
            return;
        }
        Path to = target.resolve(relPath).normalize();
        if (!to.startsWith(target)) {
            return;
        }
        Files.createDirectories(to.getParent());
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Remove uma cópia temporária (sem seguir links). Falhas são ignoradas: o que sobrar fica na pasta temporária. */
    static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    p.toFile().setWritable(true);
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // melhor esforço
                }
            });
        } catch (IOException ignored) {
            // melhor esforço
        }
    }

    // ------------------------------------------------------------------ git

    private record Out(int exit, String text) {
    }

    /**
     * Arquivo de configuração temporário que confia na pasta (e no .git dela) mesmo com outro dono, como acontece em
     * volume montado. Serve para os processos filhos do git (clone e fetch locais), que ignoram o "-c" da linha de
     * comando. Quem pede deve apagar o arquivo depois.
     */
    public Path trustConfig(Path src) throws IOException {
        Path file = Files.createTempFile("sentinel-gitconfig-", ".conf");
        String text = "[safe]\n\tdirectory = " + src + "\n\tdirectory = " + src.resolve(".git") + "\n";
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    /** Opções que deixam o git ler uma pasta de outro dono (comum em volume montado) só para esta pasta. */
    private static List<String> safeGit(Path dir) {
        return List.of("git", "-c", "safe.directory=" + dir, "-c", "core.quotepath=false");
    }

    private Out git(Path dir, String... args) throws IOException, InterruptedException {
        return git(dir, timeoutSeconds, args);
    }

    private Out git(Path dir, long limitSeconds, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(safeGit(dir));
        cmd.add("-C");
        cmd.add(dir.toString());
        cmd.addAll(List.of(args));
        return run(cmd, limitSeconds);
    }

    private Out run(List<String> cmd, long limitSeconds) throws IOException, InterruptedException {
        return run(cmd, limitSeconds, Map.of());
    }

    private Out run(List<String> cmd, long limitSeconds, Map<String, String> extraEnv) throws IOException, InterruptedException {
        Path out = Files.createTempFile("sentinel-local-", ".out");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(out.toFile());
            Map<String, String> env = pb.environment();
            env.put("GIT_TERMINAL_PROMPT", "0");
            // Em volume somente leitura o git não pode gravar o índice: sem isso o "status" falharia.
            env.put("GIT_OPTIONAL_LOCKS", "0");
            env.remove("GIT_SSH_COMMAND");
            env.putAll(extraEnv);
            Process p = pb.start();
            if (!p.waitFor(limitSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("O git excedeu o tempo limite de " + limitSeconds + "s");
            }
            try (InputStream in = Files.newInputStream(out)) {
                return new Out(p.exitValue(), new String(in.readNBytes((int) MAX_OUTPUT_BYTES), StandardCharsets.UTF_8));
            }
        } finally {
            Files.deleteIfExists(out);
        }
    }

    private static long countOf(String text, char separator) {
        long n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == separator) {
                n++;
            }
        }
        return n;
    }

    private static String tail(String output) {
        String s = output == null ? "" : output.strip();
        return s.length() > OUTPUT_TAIL_CHARS ? s.substring(s.length() - OUTPUT_TAIL_CHARS) : s;
    }
}
