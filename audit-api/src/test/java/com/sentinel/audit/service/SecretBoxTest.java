package com.sentinel.audit.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretBoxTest {

    @Test
    void cifraEDecifraSemVazarOTexto() {
        SecretBox box = new SecretBox("minha-chave");
        String enc = box.encrypt("ghp_SEGREDO");
        assertTrue(enc.startsWith("v2:"));
        assertFalse(enc.contains("ghp_SEGREDO"));
        assertEquals("ghp_SEGREDO", box.decrypt(enc));
        assertNotEquals(enc, box.encrypt("ghp_SEGREDO"), "cada cifra usa um IV novo");
    }

    @Test
    void chaveDiferenteOuLixoNaoDecifra() {
        String enc = new SecretBox("a").encrypt("segredo");
        assertNull(new SecretBox("b").decrypt(enc));
        assertNull(new SecretBox("a").decrypt("v2:lixo"));
        assertNull(new SecretBox("a").decrypt("v1:lixo"));
        assertNull(new SecretBox("a").decrypt(null));
        assertNull(new SecretBox("a").decrypt("texto-puro"));
    }

    @Test
    void semChaveNaoGuarda() {
        SecretBox box = new SecretBox("");
        assertFalse(box.available());
        assertThrows(IllegalStateException.class, () -> box.encrypt("x"));
    }

    @Test
    void semChaveNoAmbienteCriaUmArquivoDeChaveEReusaNaProximaSubida(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("sub/secrets.key");
        SecretBox first = new SecretBox(file);
        assertTrue(first.available());
        assertTrue(java.nio.file.Files.isRegularFile(file));
        String enc = first.encrypt("ghp_SEGREDO");

        SecretBox second = new SecretBox(file);                 // outra subida da API
        assertEquals("ghp_SEGREDO", second.decrypt(enc));
        assertFalse(java.nio.file.Files.readString(file).isBlank());
    }

    @Test
    void arquivoDeChaveQueNaoPodeSerGravadoDeixaOSegredoIndisponivel(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path blocker = dir.resolve("arquivo");
        java.nio.file.Files.writeString(blocker, "x");
        SecretBox box = new SecretBox(blocker.resolve("dentro/secrets.key"));   // pai é um arquivo, não pasta
        assertFalse(box.available());
        assertThrows(IllegalStateException.class, () -> box.encrypt("x"));
    }

    @Test
    void valorGravadoNoFormatoAntigoV1AindaDecifra() throws Exception {
        // v1 = AES-GCM com chave SHA-256(seed); gravado por versões anteriores, não pode "sumir" depois da atualização
        byte[] key = java.security.MessageDigest.getInstance("SHA-256").digest("minha-chave".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal("ghp_ANTIGO".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        String v1 = "v1:" + java.util.Base64.getEncoder().encodeToString(all);

        assertEquals("ghp_ANTIGO", new SecretBox("minha-chave").decrypt(v1));
        assertNull(new SecretBox("outra").decrypt(v1));
    }

    @Test
    void aChaveDerivadaNaoEOSha256DaSemente() throws Exception {
        // um valor v2 não pode ser aberto com a chave do formato antigo: prova que a derivação mudou
        String v2 = new SecretBox("minha-chave").encrypt("segredo");
        String disfarcado = "v1:" + v2.substring(3);
        assertNull(new SecretBox("minha-chave").decrypt(disfarcado));
    }
}
