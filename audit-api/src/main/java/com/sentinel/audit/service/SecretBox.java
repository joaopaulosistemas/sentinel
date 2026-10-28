package com.sentinel.audit.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Guarda segredos (como o token Git de cada sistema) cifrados no banco, com AES-256-GCM.
 * A chave sai de {@code SECRETS_KEY}; se não existir, de {@code API_KEY}. Sem nenhuma das duas, o Sentinel cria uma chave
 * aleatória num arquivo fora do banco ({@code SECRETS_KEY_FILE}, num volume próprio) e a reutiliza nas próximas subidas,
 * para o token poder ser digitado só na tela, sem mexer no {@code .env}.
 */
@ApplicationScoped
public class SecretBox {

    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @ConfigProperty(name = "sentinel.audit.secrets-key")
    Optional<String> secretsKey;

    @ConfigProperty(name = "sentinel.audit.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "sentinel.audit.secrets-key-file", defaultValue = "/var/lib/sentinel/secrets.key")
    String keyFile;

    private volatile String fileKey;

    public SecretBox() {
    }

    SecretBox(Path file) {
        this.secretsKey = Optional.empty();
        this.apiKey = Optional.empty();
        this.keyFile = file.toString();
    }

    SecretBox(String key) {
        this.secretsKey = Optional.ofNullable(key);
        this.apiKey = Optional.empty();
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(all);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível cifrar o segredo", e);
        }
    }

    /** Texto original, ou {@code null} se o valor estiver vazio, corrompido ou a chave tiver mudado. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Há chave para cifrar? */
    public boolean available() {
        return !seed().isBlank();
    }

    private String seed() {
        return secretsKey.filter(s -> !s.isBlank()).or(() -> apiKey.filter(s -> !s.isBlank())).orElseGet(this::fileSeed);
    }

    /** Chave do arquivo: lê se existe; senão cria uma aleatória (só dono lê). Vazio quando não dá para guardá-la. */
    private String fileSeed() {
        String cached = fileKey;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (fileKey != null) {
                return fileKey;
            }
            if (keyFile == null || keyFile.isBlank()) {
                return "";
            }
            try {
                Path path = Path.of(keyFile);
                if (!Files.isRegularFile(path)) {
                    byte[] raw = new byte[32];
                    RANDOM.nextBytes(raw);
                    String generated = Base64.getEncoder().encodeToString(raw);
                    if (path.getParent() != null) {
                        Files.createDirectories(path.getParent());
                    }
                    try {
                        Files.writeString(path, generated, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                        try {
                            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
                        } catch (UnsupportedOperationException | IOException ignored) {
                            // sistema de arquivos sem permissões POSIX
                        }
                    } catch (java.nio.file.FileAlreadyExistsException ignored) {
                        // outra instância criou primeiro: usa a dela
                    }
                }
                String read = Files.readString(path).strip();
                fileKey = read;
                return read;
            } catch (IOException | RuntimeException e) {
                return "";
            }
        }
    }

    private SecretKeySpec key() throws Exception {
        String seed = seed();
        if (seed.isBlank()) {
            throw new IllegalStateException("Não há chave para cifrar os segredos: defina SECRETS_KEY (ou API_KEY) ou deixe a API gravar o arquivo de chave.");
        }
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}
