package com.sentinel.audit.repository;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditedSystemRepositoryTest {

    @Test
    void repoName_pegaOUltimoTrechoSemGit() {
        assertEquals("repo", AuditedSystemRepository.repoName("github.com/org/repo.git"));
        assertEquals("repo", AuditedSystemRepository.repoName("GitHub.com/Org/Repo"));
        assertEquals("repo", AuditedSystemRepository.repoName("repo"));
        assertEquals("", AuditedSystemRepository.repoName(null));
    }

    @Test
    void candidatesByRepo_semEnderecosNaoVaiAoBanco() {
        assertEquals(0, new AuditedSystemRepository().candidatesByRepo(List.of()).size());
        assertEquals(0, new AuditedSystemRepository().candidatesByRepo(List.of("")).size());
    }
}
