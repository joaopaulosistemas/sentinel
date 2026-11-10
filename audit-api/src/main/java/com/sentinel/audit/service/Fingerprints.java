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
 * muda com a versão, então contam regra + arquivo + a lista de vulnerabilidades (guardada como trecho): atualizar a
 * versão sem mudar as vulnerabilidades não cria um problema "novo", mas uma vulnerabilidade nova reabre o que estava suprimido.
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

    /**
     * Identidade que as dependências tinham antes de os ids de vulnerabilidade entrarem no cálculo (regra + arquivo).
     * Serve só para reconhecer, na auditoria anterior, uma dependência que já existia, e não marcá-la como nova.
     */
    public static String legacyDependency(Finding f) {
        return sha256(String.join("\u0000", nullToEmpty(f.getRuleId()), nullToEmpty(f.getFilePath()), ""));
    }

    /** A n-ésima ocorrência idêntica (mesma regra, arquivo e trecho) no mesmo arquivo; a primeira mantém a identidade base. */
    public static String withOrdinal(String base, int ordinal) {
        return ordinal == 0 ? base : sha256(base + "\u0000#" + ordinal);
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
