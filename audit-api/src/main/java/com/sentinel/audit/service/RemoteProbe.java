package com.sentinel.audit.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Verificações rápidas de um repositório remoto, sem clonar por inteiro: se responde, qual a branch padrão,
 * quantos arquivos tem e qual o SHA da ponta de uma branch. Usa as regras de URL, token e protocolo do
 * {@link IngestionService}, que continua sendo a porta de entrada.
 */
final class RemoteProbe {

    private static final long CHECK_TIMEOUT_SECONDS = 20;

    private final IngestionService ingestion;

    RemoteProbe(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    /** Confere, sem clonar, se o repositório responde e qual é a branch padrão; ver {@link IngestionService#checkRepository}. */
    IngestionService.RepoCheck check(String repoUrl, String branch, String tokenOverride, String hosts) {
        URI uri;
        try {
            uri = ingestion.validateRepoUrl(repoUrl);
            ingestion.validateBranch(branch);
        } catch (IllegalArgumentException e) {
            return new IngestionService.RepoCheck(false, null, null, e.getMessage());
        }
        String token = ingestion.resolveToken(tokenOverride);
        boolean sendToken = !token.isBlank() && ingestion.isTokenHost(uri.getHost(), hosts);
        Path logFile = null;
        try {
            Path basePath = Paths.get(ingestion.cloneBaseDir).toAbsolutePath().normalize();
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
            ingestion.configureEnvironment(pb.environment(), sendToken ? token : null);
            Process process = pb.start();
            if (!process.waitFor(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new IngestionService.RepoCheck(false, null, null, "O repositório não respondeu em " + CHECK_TIMEOUT_SECONDS + "s");
            }
            String output = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new IngestionService.RepoCheck(false, null, null, friendlyGitError(ingestion.readTail(logFile, token)));
            }
            String defaultBranch = parseDefaultBranch(output);
            Boolean branchFound = branch == null || branch.isBlank() ? null : branchListed(output, branch);
            String target = branch != null && !branch.isBlank() && Boolean.TRUE.equals(branchFound) ? branch : defaultBranch;
            return new IngestionService.RepoCheck(true, defaultBranch, branchFound, null, countFiles(uri.toString(), target, sendToken ? token : null));
        } catch (IOException e) {
            return new IngestionService.RepoCheck(false, null, null, "Não foi possível executar o git neste servidor");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new IngestionService.RepoCheck(false, null, null, "Verificação interrompida");
        } finally {
            ingestion.deleteQuietly(logFile);
        }
    }

    /**
     * Conta os arquivos da branch sem baixar o conteúdo (clone raso, sem blobs, só a árvore). Melhor esforço: se
     * demorar mais que o limite ou falhar, devolve nulo e a verificação segue sem o número.
     */
    Long countFiles(String repoUrl, String branch, String token) {
        Path dir = null;
        try {
            Path basePath = Paths.get(ingestion.cloneBaseDir).toAbsolutePath().normalize();
            dir = basePath.resolve("count-" + UUID.randomUUID()).normalize();
            List<String> clone = new ArrayList<>(List.of("git", "-c", "protocol.allow=never", "-c", "protocol.https.allow=always",
                    "clone", "--depth", "1", "--filter=blob:none", "--no-checkout", "--single-branch"));
            if (branch != null && !branch.isBlank()) {
                clone.addAll(List.of("--branch", branch));
            }
            clone.addAll(List.of("--", repoUrl, dir.toString()));
            ProcessBuilder pb = new ProcessBuilder(clone).redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            ingestion.configureEnvironment(pb.environment(), token);
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
            ingestion.cleanupDirectory(dir);
        }
    }

    /**
     * SHA da ponta de uma branch no remoto, sem clonar ({@code git ls-remote}). Melhor esforço: nulo se o
     * repositório não responde, a branch não existe ou a URL é inválida.
     */
    String headSha(String repoUrl, String branch, String tokenOverride, String hosts) {
        if (branch == null || branch.isBlank()) {
            return null;
        }
        Path logFile = null;
        try {
            URI uri = ingestion.validateRepoUrl(repoUrl);
            ingestion.validateBranch(branch);
            String token = ingestion.resolveToken(tokenOverride);
            boolean sendToken = !token.isBlank() && ingestion.isTokenHost(uri.getHost(), hosts);
            Path basePath = Paths.get(ingestion.cloneBaseDir).toAbsolutePath().normalize();
            Files.createDirectories(basePath);
            logFile = Files.createTempFile(basePath, "git-head-", ".log");
            ProcessBuilder pb = new ProcessBuilder("git", "-c", "protocol.allow=never", "-c", "protocol.https.allow=always",
                    "ls-remote", "--", uri.toString(), "refs/heads/" + branch);
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile.toFile());
            ingestion.configureEnvironment(pb.environment(), sendToken ? token : null);
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
            ingestion.deleteQuietly(logFile);
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
}
