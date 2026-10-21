package com.sentinel.audit.insights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.audit.analyzer.HygieneAnalyzer;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectProfilerTest {

    @TempDir
    Path tmp;

    private void w(String rel, String content) throws IOException {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private Set<String> rules(ProjectMap m, int minSrc, int minReadme) {
        return HygieneAnalyzer.check(m, new AuditRun(), minSrc, minReadme).stream()
                .map(Finding::getRuleId).collect(Collectors.toSet());
    }

    @Test
    void projetoVazioNaoGeraAchadoSemEvidencia() throws IOException {
        ProjectMap m = ProjectProfiler.profile(tmp);
        assertEquals(0, m.sourceFiles());
        // só o README ausente é fato; testes/CI/gitignore exigem haver código
        assertEquals(Set.of("sentinel-proj-no-readme"), rules(m, 10, 10));
    }

    @Test
    void projetoSemTestesNemCi() throws IOException {
        for (int i = 0; i < 12; i++) {
            w("src/main/java/a/C" + i + ".java", "class C" + i + " {}");
        }
        w("pom.xml", "<project>quarkus</project>");
        w("README.md", "# x\n");
        ProjectMap m = ProjectProfiler.profile(tmp);
        assertEquals(12, m.sourceFiles());
        assertTrue(m.frameworks().contains("Quarkus"));
        Set<String> r = rules(m, 10, 10);
        assertTrue(r.contains("sentinel-proj-no-tests"));
        assertTrue(r.contains("sentinel-proj-no-ci"));
        assertTrue(r.contains("sentinel-proj-readme-short"));
        assertTrue(r.contains("sentinel-proj-no-gitignore"));
        assertFalse(r.contains("sentinel-proj-no-readme"));
    }

    @Test
    void detectaTestesCiEReadmeCompleto() throws IOException {
        for (int i = 0; i < 12; i++) {
            w("src/main/java/a/C" + i + ".java", "class C" + i + " {}");
        }
        w("src/test/java/a/CTest.java", "class CTest {}");
        w(".github/workflows/ci.yml", "on: push");
        w(".gitignore", "target/");
        w("README.md", "a\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\n");
        ProjectMap m = ProjectProfiler.profile(tmp);
        assertEquals(1, m.testFiles());
        assertEquals(1, m.ciConfigs().size());
        assertTrue(rules(m, 10, 10).isEmpty(), rules(m, 10, 10).toString());
    }

    @Test
    void segredosLockfileEDockerignore() throws IOException {
        w("app/package.json", "{\"dependencies\":{\"react\":\"18\"},\"scripts\":{\"build\":\"vite build\"}}");
        w("app/src/index.tsx", "export {}");
        w(".env", "DB_PASSWORD=abc");
        w(".env.example", "DB_PASSWORD=");
        w("keys/server.pem", "-----BEGIN RSA PRIVATE KEY-----\nabc\n-----END RSA PRIVATE KEY-----");
        w("keys/ca.pem", "-----BEGIN CERTIFICATE-----\nabc\n-----END CERTIFICATE-----");
        w("Dockerfile", "FROM alpine");
        w("README.md", "x");
        ProjectMap m = ProjectProfiler.profile(tmp);
        assertEquals(List.of(".env"), m.committedEnvFiles());
        assertEquals(List.of("keys/server.pem"), m.committedKeyFiles());
        assertEquals(List.of("app/package.json"), m.packageJsonWithoutLock());
        assertTrue(m.frameworks().contains("React"));
        Set<String> r = rules(m, 10, 10);
        assertTrue(r.contains("sentinel-proj-committed-env"));
        assertTrue(r.contains("sentinel-proj-committed-key"));
        assertTrue(r.contains("sentinel-proj-no-lockfile"));
        assertTrue(r.contains("sentinel-proj-docker-no-dockerignore"));
    }

    @Test
    void lockNaRaizCobreMonorepo() throws IOException {
        w("package-lock.json", "{}");
        w("web/package.json", "{}");
        w("web/a.ts", "export {}");
        ProjectMap m = ProjectProfiler.profile(tmp);
        assertTrue(m.packageJsonWithoutLock().isEmpty());
    }

    @Test
    void nodeModulesVersionadoEIgnoradoNaContagem() throws IOException {
        w("node_modules/x/index.js", "1");
        w("src/a.js", "1");
        ProjectMap m = ProjectProfiler.profile(tmp);
        assertEquals(1, m.sourceFiles());
        assertEquals(List.of("node_modules"), m.committedArtifactDirs());
    }

    @Test
    void distESrcSemEvidenciaNaoViramArtefato() throws IOException {
        w("dist/a.js", "1");
        w("target/note.txt", "1");
        ProjectMap m = ProjectProfiler.profile(tmp);
        assertTrue(m.committedArtifactDirs().isEmpty());
    }

    @Test
    void dockerfileEmPastaDeTesteNaoContaComoDoProjeto() throws IOException {
        w("tests/fixtures/Dockerfile", "FROM alpine");
        w("infra/main.tf", "resource {}");
        w("tests/fixtures/x.tf", "resource {}");
        ProjectMap m = ProjectProfiler.profile(tmp);
        assertTrue(m.dockerfiles().isEmpty());
        assertEquals(List.of("infra/main.tf"), m.iacFiles());
    }
}
