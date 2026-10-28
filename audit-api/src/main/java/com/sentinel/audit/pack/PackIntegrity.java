package com.sentinel.audit.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Integridade de pacotes: {@code pack.lock} lista o SHA-256 de cada arquivo; {@code pack.sig} é a assinatura Ed25519
 * do {@code pack.lock}. Só JDK, sem dependências. Quem publica o pacote assina; o servidor confere com a chave pública.
 */
public final class PackIntegrity {

    /** Tamanho de um SHA-256 em hexadecimal. */
    private static final int SHA256_HEX_CHARS = 64;
    private static final int READ_BUFFER_BYTES = 8192;


    public static final String LOCK = "pack.lock";
    public static final String SIG = "pack.sig";

    public enum State {
        /** Sem {@code pack.lock}. */
        UNSIGNED,
        /** Hashes conferem, mas não há assinatura ou não há chave pública para conferi-la. */
        CHECKSUM_OK,
        /** Hashes conferem e a assinatura é válida para a chave pública informada. */
        SIGNED,
        /** Arquivo alterado, incluído ou removido; ou assinatura que não confere. */
        INVALID
    }

    public record Result(State state, String detail) {
        static Result of(State s) {
            return new Result(s, null);
        }
    }

    private PackIntegrity() {}

    // ------------------------------------------------------------------ chaves

    /** @return [0] chave privada (PKCS#8, Base64), [1] chave pública (X.509, Base64) */
    public static String[] generateKeyPair() throws GeneralSecurityException {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        return new String[]{Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())};
    }

    public static PublicKey publicKey(String base64) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(decode(base64)));
    }

    public static PrivateKey privateKey(String base64) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(decode(base64)));
    }

    private static byte[] decode(String b64) {
        return Base64.getMimeDecoder().decode(b64.strip());
    }

    // ------------------------------------------------------------------ lock / assinatura

    /** Conteúdo do {@code pack.lock}: uma linha {@code <sha256>  <caminho>} por arquivo, em ordem alfabética. */
    public static String computeLock(Path dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String rel : listFiles(dir)) {
            sb.append(sha256(dir.resolve(rel))).append("  ").append(rel).append('\n');
        }
        return sb.toString();
    }

    /** Grava {@code pack.lock} e, se houver chave, {@code pack.sig}. Substitui os anteriores. */
    public static void seal(Path dir, PrivateKey key) throws IOException, GeneralSecurityException {
        Files.deleteIfExists(dir.resolve(SIG));
        Files.deleteIfExists(dir.resolve(LOCK));
        String lock = computeLock(dir);
        Files.writeString(dir.resolve(LOCK), lock, StandardCharsets.UTF_8);
        if (key != null) {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(key);
            s.update(lock.getBytes(StandardCharsets.UTF_8));
            Files.writeString(dir.resolve(SIG), Base64.getEncoder().encodeToString(s.sign()) + "\n", StandardCharsets.UTF_8);
        }
    }

    /** @param key chave pública para conferir a assinatura (pode ser nula) */
    public static Result verify(Path dir, PublicKey key) {
        Path lockFile = dir.resolve(LOCK);
        if (!Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS)) {
            return Result.of(State.UNSIGNED);
        }
        try {
            byte[] lockBytes = Files.readAllBytes(lockFile);
            String problem = compareWithDisk(dir, new String(lockBytes, StandardCharsets.UTF_8));
            if (problem != null) {
                return new Result(State.INVALID, problem);
            }
            Path sigFile = dir.resolve(SIG);
            boolean hasSig = Files.isRegularFile(sigFile, LinkOption.NOFOLLOW_LINKS);
            if (!hasSig) {
                return Result.of(State.CHECKSUM_OK);
            }
            if (key == null) {
                return Result.of(State.CHECKSUM_OK);
            }
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(lockBytes);
            boolean ok = s.verify(decode(Files.readString(sigFile, StandardCharsets.UTF_8)));
            return ok ? Result.of(State.SIGNED) : new Result(State.INVALID, "assinatura não confere com a chave pública");
        } catch (IllegalArgumentException e) {
            return new Result(State.INVALID, "pack.sig ilegível");
        } catch (IOException | GeneralSecurityException e) {
            return new Result(State.INVALID, "não foi possível verificar: " + e.getMessage());
        }
    }

    private static String compareWithDisk(Path dir, String lock) throws IOException {
        List<String> onDisk = listFiles(dir);
        List<String> listed = new ArrayList<>();
        for (String line : lock.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int sep = line.indexOf("  ");
            if (sep != SHA256_HEX_CHARS) {
                return "pack.lock malformado";
            }
            String rel = line.substring(sep + 2);
            listed.add(rel);
            if (!onDisk.contains(rel)) {
                return "arquivo removido: " + rel;
            }
            if (!sha256(dir.resolve(rel)).equals(line.substring(0, SHA256_HEX_CHARS))) {
                return "arquivo alterado: " + rel;
            }
        }
        for (String rel : onDisk) {
            if (!listed.contains(rel)) {
                return "arquivo não previsto no pack.lock: " + rel;
            }
        }
        return null;
    }

    /** Arquivos do pacote (relativos, com {@code /}), exceto lock e assinatura. Link simbólico é erro. */
    private static List<String> listFiles(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<String> out = new ArrayList<>();
            for (Path p : walk.toList()) {
                if (Files.isSymbolicLink(p)) {
                    throw new IOException("link simbólico não é permitido: " + dir.relativize(p));
                }
                if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    String rel = dir.relativize(p).toString().replace('\\', '/');
                    if (!rel.equals(LOCK) && !rel.equals(SIG)) {
                        out.add(rel);
                    }
                }
            }
            return out.stream().sorted().collect(Collectors.toList());
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[READ_BUFFER_BYTES];
            int n = in.read(buf);
            while (n > 0) {
                md.update(buf, 0, n);
                n = in.read(buf);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
