package com.sentinel.audit.service;

import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectConfigTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 28);

    private static Finding f(String rule, String path) {
        Finding f = new Finding();
        f.setRuleId(rule);
        f.setFilePath(path);
        f.setDescription("descrição");
        return f;
    }

    private static final String ARQUIVO = """
            version: 1
            exclude:
              - "docs/**"
              - "audit-rules/packs/*/tests/**"
              - "**/*.min.js"
              - vendor
            suppress:
              - rule: sentinel-java-retorna-null-colecao
                path: "src/main/java/pack/Yamls.java"
                reason: "null é o sinal de valor inválido"
              - rule: regra-vencida
                reason: "temporário"
                until: 2020-01-01
              - rule: sem-motivo
              - rule: com-data-futura
                reason: "ok"
                until: "2999-12-31"
            """;

    @Test
    void excludeRetiraOsCaminhosEContaQuantos() {
        List<Finding> in = new ArrayList<>(List.of(
                f("r", "docs/evolucao/a.md"),
                f("r", "audit-rules/packs/core-iac/tests/t.java"),
                f("r", "audit-rules/packs/core-iac/rules/r.yml"),
                f("r", "web/app.min.js"),
                f("r", "a/vendor/lib/x.js"),
                f("r", "src/Main.java")));

        ProjectConfig.Result r = ProjectConfig.parse(ARQUIVO).apply(in, HOJE);

        assertEquals(4, r.excluded());
        assertEquals(2, r.findings().size());
        assertTrue(r.findings().stream().anyMatch(x -> x.getFilePath().endsWith("rules/r.yml")));
    }

    @Test
    void suppressMarcaComOMotivoEMantemVisivel() {
        Finding yamls = f("audit-rules.rules.sentinel-java-retorna-null-colecao", "src/main/java/pack/Yamls.java");
        Finding outro = f("audit-rules.rules.sentinel-java-retorna-null-colecao", "src/main/java/pack/Outro.java");

        ProjectConfig.Result r = ProjectConfig.parse(ARQUIVO).apply(List.of(yamls, outro), HOJE);

        assertEquals(1, r.suppressed());
        assertEquals(2, r.findings().size());                       // continua na lista
        assertEquals(FindingStatus.SUPPRESSED, yamls.getStatus());
        assertTrue(yamls.getDescription().contains("null é o sinal de valor inválido"));
        assertNull(outro.getStatus());                              // o path restringe a supressão
    }

    @Test
    void regraComAsteriscoNoFinalValeComoPrefixo() {
        String yml = """
                suppress:
                  - rule: "gitleaks-*"
                    path: "src/test/**"
                    reason: "segredos de mentira dos testes"
                """;
        Finding teste = f("gitleaks-generic-api-key", "src/test/java/A.java");
        Finding producao = f("gitleaks-generic-api-key", "src/main/java/A.java");
        Finding outraRegra = f("pmd-x", "src/test/java/B.java");

        ProjectConfig.parse(yml).apply(List.of(teste, producao, outraRegra), HOJE);

        assertEquals(FindingStatus.SUPPRESSED, teste.getStatus());
        assertNull(producao.getStatus());
        assertNull(outraRegra.getStatus());
    }

    @Test
    void supressaoVencidaNaoValeEGeraAviso() {
        Finding vencida = f("regra-vencida", "src/A.java");
        Finding futura = f("com-data-futura", "src/B.java");

        ProjectConfig.Result r = ProjectConfig.parse(ARQUIVO).apply(List.of(vencida, futura), HOJE);

        assertNull(vencida.getStatus());
        assertEquals(FindingStatus.SUPPRESSED, futura.getStatus());
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("regra-vencida") && p.contains("venceu")));
    }

    @Test
    void supressaoSemMotivoEIgnoradaComAviso() {
        Finding sem = f("sem-motivo", "src/A.java");

        ProjectConfig.Result r = ProjectConfig.parse(ARQUIVO).apply(List.of(sem), HOJE);

        assertNull(sem.getStatus());
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("obrigatórios")));
        assertTrue(r.summary().contains(".sentinel.yml"));
    }

    @Test
    void arquivoInvalidoNuncaLancaExcecao() {
        assertFalse(ProjectConfig.parse("a: [1, 2").isEmpty());
        assertEquals(1, ProjectConfig.parse("- a\n- b").apply(List.of(), HOJE).problems().size());
        assertEquals(1, ProjectConfig.parse("exclude: 5").apply(List.of(), HOJE).problems().size());
        assertTrue(ProjectConfig.parse("").isEmpty());
    }

    @Test
    void semAlteracaoOResumoFicaVazio() {
        ProjectConfig.Result r = ProjectConfig.empty().apply(List.of(f("r", "src/A.java")), HOJE);
        assertFalse(r.touched());
        assertEquals("", r.summary());
    }

    @Test
    void loadIgnoraLinkSimbolicoEArquivoGrande(@TempDir Path dir) throws IOException {
        Path real = dir.resolve("real.yml");
        Files.writeString(real, "exclude: [\"**\"]\n");
        Path linkDir = Files.createDirectory(dir.resolve("com-link"));
        try {
            Files.createSymbolicLink(linkDir.resolve(ProjectConfig.FILE_NAME), real);
            assertTrue(ProjectConfig.load(linkDir).isEmpty());      // não segue link para fora da pasta
        } catch (UnsupportedOperationException | IOException e) {
            // sistema de arquivos sem link simbólico: nada a verificar aqui
        }

        Path grande = Files.createDirectory(dir.resolve("grande"));
        Files.writeString(grande.resolve(ProjectConfig.FILE_NAME), "x: " + "a".repeat(300_000));
        ProjectConfig c = ProjectConfig.load(grande);
        assertTrue(c.apply(List.of(), HOJE).problems().get(0).contains("KB"));
    }

    @Test
    void loadLeOArquivoDaRaiz(@TempDir Path dir) throws IOException {
        assertTrue(ProjectConfig.load(dir).isEmpty());              // sem arquivo = sem ajustes
        Files.writeString(dir.resolve(ProjectConfig.FILE_NAME), "exclude: [\"docs/**\"]\n");
        assertEquals(1, ProjectConfig.load(dir).apply(List.of(f("r", "docs/x.md")), HOJE).excluded());
    }
}
