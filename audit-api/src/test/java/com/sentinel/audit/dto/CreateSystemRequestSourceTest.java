package com.sentinel.audit.dto;

import com.sentinel.audit.model.SourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regras de cadastro por origem: Git exige URL; pasta local exige a pasta; clientes antigos continuam valendo. */
class CreateSystemRequestSourceTest {

    /** Só origem, URL e pasta importam aqui; os demais campos ficam ausentes. */
    private static CreateSystemRequest request(SourceType type, String repoUrl, String localPath) {
        return new CreateSystemRequest(null, null, repoUrl, type, localPath, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void semOrigemInformadaContinuaExigindoAUrlComoAntes() {
        assertFalse(request(null, null, null).isRepoUrlPresentForGit());
        assertFalse(request(null, "   ", null).isRepoUrlPresentForGit());
        CreateSystemRequest r = request(null, "https://github.com/org/repo", null);
        assertTrue(r.isRepoUrlPresentForGit());
        assertTrue(r.isLocalPathPresentForLocal());
    }

    @Test
    void origemGitExigeUrlEIgnoraPasta() {
        assertFalse(request(SourceType.GIT_URL, null, null).isRepoUrlPresentForGit());
        CreateSystemRequest r = request(SourceType.GIT_URL, "https://github.com/org/repo", null);
        assertTrue(r.isRepoUrlPresentForGit());
        assertTrue(r.isLocalPathPresentForLocal());
    }

    @Test
    void pastaLocalExigeAPastaENaoExigeUrl() {
        CreateSystemRequest semPasta = request(SourceType.LOCAL_PATH, null, null);
        assertTrue(semPasta.isRepoUrlPresentForGit());
        assertFalse(semPasta.isLocalPathPresentForLocal());
        assertFalse(request(SourceType.LOCAL_PATH, null, "  ").isLocalPathPresentForLocal());
        assertTrue(request(SourceType.LOCAL_PATH, null, "projetos/api").isLocalPathPresentForLocal());
    }
}
