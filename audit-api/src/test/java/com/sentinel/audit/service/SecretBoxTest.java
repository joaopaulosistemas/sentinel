package com.sentinel.audit.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretBoxTest {

    @Test
    void cifraEDecifraSemVazarOTexto() {
        SecretBox box = new SecretBox("minha-chave");
        String enc = box.encrypt("ghp_SEGREDO");
        assertTrue(enc.startsWith("v1:"));
        assertFalse(enc.contains("ghp_SEGREDO"));
        assertEquals("ghp_SEGREDO", box.decrypt(enc));
        assertNotEquals(enc, box.encrypt("ghp_SEGREDO"), "cada cifra usa um IV novo");
    }

    @Test
    void chaveDiferenteOuLixoNaoDecifra() {
        String enc = new SecretBox("a").encrypt("segredo");
        assertNull(new SecretBox("b").decrypt(enc));
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
}
