package com.sentinel.audit.dto;

import com.sentinel.audit.model.SourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regras de cadastro por origem: Git exige URL; pasta local exige a pasta; clientes antigos continuam valendo. */
class CreateSystemRequestSourceTest {

    @Test
    void semOrigemInformadaContinuaExigindoAUrlComoAntes() {
        CreateSystemRequest r = new CreateSystemRequest();
        assertFalse(r.isRepoUrlPresentForGit());
        r.setRepoUrl("   ");
        assertFalse(r.isRepoUrlPresentForGit());
        r.setRepoUrl("https://github.com/org/repo");
        assertTrue(r.isRepoUrlPresentForGit());
        assertTrue(r.isLocalPathPresentForLocal());
    }

    @Test
    void origemGitExigeUrlEIgnoraPasta() {
        CreateSystemRequest r = new CreateSystemRequest();
        r.setSourceType(SourceType.GIT_URL);
        assertFalse(r.isRepoUrlPresentForGit());
        r.setRepoUrl("https://github.com/org/repo");
        assertTrue(r.isRepoUrlPresentForGit());
        assertTrue(r.isLocalPathPresentForLocal());
    }

    @Test
    void pastaLocalExigeAPastaENaoExigeUrl() {
        CreateSystemRequest r = new CreateSystemRequest();
        r.setSourceType(SourceType.LOCAL_PATH);
        assertTrue(r.isRepoUrlPresentForGit());
        assertFalse(r.isLocalPathPresentForLocal());
        r.setLocalPath("  ");
        assertFalse(r.isLocalPathPresentForLocal());
        r.setLocalPath("projetos/api");
        assertTrue(r.isLocalPathPresentForLocal());
    }
}
