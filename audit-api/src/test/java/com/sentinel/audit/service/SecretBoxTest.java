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
}
