package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.structure.SourceFiles.Src;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalAuthTest {

    @Test
    void reconheceFiltroJaxRsQueRejeita() {
        String found = GlobalAuth.fromJava(List.of(new Src("a/ApiKeyFilter.java",
                "@Provider\n@Priority(1)\nclass ApiKeyFilter implements ContainerRequestFilter {\n"
                        + " void f(){ ctx.abortWith(Response.status(401).build()); } }")));
        assertTrue(found.contains("ApiKeyFilter"));
    }

    @Test
    void reconheceSpringSecurityComTudoAutenticado() {
        assertNotNull(GlobalAuth.fromJava(List.of(new Src("a/Sec.java",
                "@EnableWebSecurity class Sec { void c(){ http.authorizeHttpRequests(a -> a.anyRequest().authenticated()); } }"))));
    }

    @Test
    void filtroQueNaoRejeitaNaoContaComoBarreira() {
        assertNull(GlobalAuth.fromJava(List.of(new Src("a/Log.java", "class Log implements ContainerRequestFilter { }"))));
    }

    @Test
    void reconhecePoliticaHttpDoQuarkusEmModulo(@TempDir Path root) throws Exception {
        Path res = root.resolve("api/src/main/resources");
        Files.createDirectories(res);
        Files.writeString(res.resolve("application.properties"), "quarkus.http.auth.permission.all.paths=/*\n"
                + "quarkus.http.auth.permission.all.policy=authenticated\n");
        assertTrue(GlobalAuth.detect(root).contains("Quarkus"));
    }

    @Test
    void projetoSemBarreiraDevolveNulo(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/A.java"), "class A {}");
        assertNull(GlobalAuth.detect(root));
    }
}
