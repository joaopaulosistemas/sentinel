package com.sentinel.audit.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Guarda segredos (como o token Git de cada sistema) cifrados no banco, com AES-256-GCM.
 * A chave sai de {@code SECRETS_KEY}; se não existir, de {@code API_KEY}. Sem nenhuma das duas, o token não é salvo.
 */
@ApplicationScoped
public class SecretBox {

    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @ConfigProperty(name = "sentinel.audit.secrets-key")
    Optional<String> secretsKey;

    @ConfigProperty(name = "sentinel.audit.api-key")
    Optional<String> apiKey;

    public SecretBox() {
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
        return secretsKey.filter(s -> !s.isBlank()).or(() -> apiKey.filter(s -> !s.isBlank())).orElse("");
    }

    private SecretKeySpec key() throws Exception {
        String seed = seed();
        if (seed.isBlank()) {
            throw new IllegalStateException("Defina SECRETS_KEY (ou API_KEY) no ambiente da API para guardar tokens com segurança.");
        }
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}
