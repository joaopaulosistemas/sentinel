package com.sentinel.audit.service;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Calcula a identidade estável de um finding entre auditorias.
 * <p>
 * Usa regra + arquivo + trecho de código normalizado e <b>não</b> usa o número da linha: se alguém
 * insere código acima, o problema continua sendo "o mesmo". Para dependências o trecho não existe e o título
 * muda com a versão, então só regra + arquivo contam (atualizar a versão não cria um problema "novo").
 */
public final class Fingerprints {

    private static final int SNIPPET_MAX_CHARS = 300;


    private Fingerprints() {}

    public static String compute(Finding f) {
        String snippet = normalize(f.getCodeSnippet());
        String discriminator = snippet != null ? snippet
                : f.getCategory() == Category.DEPENDENCY ? "" : normalize(f.getTitle());
        String raw = String.join("\u0000",
                nullToEmpty(f.getRuleId()),
                nullToEmpty(f.getFilePath()),
                nullToEmpty(discriminator));
        return sha256(raw);
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String squashed = text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return squashed.length() > SNIPPET_MAX_CHARS ? squashed.substring(0, SNIPPET_MAX_CHARS) : squashed;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
