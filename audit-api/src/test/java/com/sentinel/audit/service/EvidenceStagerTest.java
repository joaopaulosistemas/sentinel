package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceStagerTest {

    @TempDir
    Path dir;
    EvidenceStager stager;
    Path evidencias;
    Path projeto;

    @BeforeEach
    void setUp() throws Exception {
        stager = new EvidenceStager();
        evidencias = Files.createDirectories(dir.resolve("evidencias"));
        projeto = Files.createDirectories(dir.resolve("clone"));
        stager.evidenceDir = Optional.of(evidencias.toString());
    }

    @Test
    void copiaAPastaDoSistemaPeloNomeSemAcentoEmMinusculo() throws Exception {
        Path origem = Files.createDirectories(evidencias.resolve("sistema-de-cambio"));
        Files.writeString(origem.resolve("schema.sql"), "CREATE TABLE a (id int);");
        Files.createDirectories(origem.resolve("subpasta"));

        int n = stager.stage("Sistema de Câmbio", "https://git/x/outro.git", projeto);

        assertEquals(1, n);
        assertTrue(Files.exists(projeto.resolve(".sentinel/evidence/schema.sql")));
        assertFalse(Files.exists(projeto.resolve(".sentinel/evidence/subpasta")));
    }

    @Test
    void usaONomeDoRepositorioQuandoNaoHaPastaComONomeDoSistema() throws Exception {
        Files.writeString(Files.createDirectories(evidencias.resolve("meu-repo")).resolve("a.csv"), "x");

        assertEquals(1, stager.stage("Nome Que Não Existe", "git@github.com:org/meu-repo.git", projeto));
    }

    @Test
    void semConfiguracaoOuSemPastaNaoFazNada() {
        stager.evidenceDir = Optional.empty();
        assertEquals(0, stager.stage("x", "y", projeto));

        stager.evidenceDir = Optional.of(evidencias.toString());
        assertEquals(0, stager.stage("sem-pasta", "https://git/x/sem-pasta", projeto));
        assertFalse(Files.exists(projeto.resolve(".sentinel")));
    }

    @Test
    void nomeComTentativaDeSairDaPastaNaoEscapa() throws Exception {
        Files.createDirectories(dir.resolve("fora"));
        Files.writeString(dir.resolve("fora/a.sql"), "x");

        assertEquals(0, stager.stage("../fora", "../fora", projeto));
        assertFalse(Files.exists(projeto.resolve(".sentinel/evidence/a.sql")));
    }

    @Test
    void slugERepoName() {
        assertEquals("sistema-de-cambio", EvidenceStager.slug("Sistema de Câmbio"));
        assertEquals("", EvidenceStager.slug(".."));
        assertEquals("meu-repo", EvidenceStager.repoName("https://github.com/org/meu-repo.git/"));
    }
}
