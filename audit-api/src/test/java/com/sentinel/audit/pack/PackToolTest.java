package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.sentinel.audit.pack.PackFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PackToolTest {

    private record Out(int code, String out, String err) {}

    private static Out run(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int code = PackTool.run(args, new PrintStream(o, true, StandardCharsets.UTF_8), new PrintStream(e, true, StandardCharsets.UTF_8));
        return new Out(code, o.toString(StandardCharsets.UTF_8), e.toString(StandardCharsets.UTF_8));
    }

    @Test
    void semArgumentosMostraAjuda() {
        Out r = run();
        assertEquals(2, r.code());
        assertTrue(r.err().isEmpty() && r.out().contains("Uso: sentinel-pack"));
        assertEquals(0, run("--help").code());
        assertEquals(2, run("inexistente").code());
    }

    @Test
    void validaOsPacotesEmbutidosEListaPorPacote() {
        Out r = run("validate", TestPacks.builtinRoot().toString());

        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("OK     core-quality v1.0.3 — 41 regra(s) Semgrep"), r.out());
        assertTrue(r.out().contains("OK     core-architecture"), r.out());
    }

    @Test
    void strictExigeTesteEmTodaRegra() throws Exception {
        Out builtin = run("validate", "--strict", TestPacks.builtinRoot().toString());
        assertEquals(0, builtin.code(), "todo pacote embutido tem teste para cada regra: " + builtin.err() + builtin.out());

        Path root = tempRoot();
        run("new", "sem-teste", "--dir", root.toString());
        try (var files = Files.walk(root.resolve("sem-teste/tests"))) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Files.delete(f);
            }
        }
        assertEquals(0, run("validate", root.resolve("sem-teste").toString()).code(), "sem --strict é só aviso");
        Out strict = run("validate", "--strict", root.resolve("sem-teste").toString());
        assertEquals(1, strict.code());
        assertTrue(strict.err().contains("sem caso de teste"), strict.err());
    }

    @Test
    void validateFalhaComCodigoUmEMostraMotivos() throws Exception {
        Path root = tempRoot();
        write(root.resolve("ruim/pack.yml"), "id: ruim\n");
        pack(root, "bom", "1.0.0", true);

        Out r = run("validate", root.toString());

        assertEquals(1, r.code());
        assertTrue(r.out().contains("OK     bom"));
        assertTrue(r.err().contains("'name' é obrigatório"), r.err());
    }

    @Test
    void validateTambemConferePerfis() throws Exception {
        Path root = tempRoot();
        pack(root, "bom", "1.0.0", true);
        profile(root, "p1", "id: p1\nthresholds: {max-linhas: 3}\n");

        Out r = run("validate", root.toString());
        assertEquals(1, r.code());
        assertTrue(r.err().contains("limite desconhecido"), r.err());
    }

    @Test
    void newCriaPacoteQueJaValidaEPassaNosTestesDoSemgrep() throws Exception {
        Path root = tempRoot();

        Out created = run("new", "meu-time", "--dir", root.toString());
        assertEquals(0, created.code(), created.err());

        Out valid = run("validate", root.resolve("meu-time").toString());
        assertEquals(0, valid.code(), valid.err());
        assertTrue(valid.out().contains("OK     meu-time v1.0.0 — 1 regra(s)"), valid.out());
        assertFalse(valid.out().contains("aviso"), "o exemplo já vem com teste: " + valid.out());

        Out tested = run("test", root.resolve("meu-time").toString());
        assertEquals(0, tested.code(), tested.out() + tested.err());
        assertTrue(tested.out().contains("All tests passed"), tested.out());

        assertEquals(1, run("new", "meu-time", "--dir", root.toString()).code(), "não sobrescreve");
        assertEquals(2, run("new", "Ruim_Id").code());
    }

    @Test
    void testDetectaRegraQueNaoCasaOQueDeveria() throws Exception {
        Path root = tempRoot();
        run("new", "x1", "--dir", root.toString());
        Path rule = root.resolve("x1/rules/exemplo.yml");
        Files.writeString(rule, Files.readString(rule).replace("System.exit(...)", "Runtime.nada(...)"));

        Out r = run("test", root.resolve("x1").toString());
        assertEquals(1, r.code(), "a regra deixou de casar o caso 'ruleid'");
    }

    @Test
    void fluxoDeAssinaturaPackVerifyKeygen() throws Exception {
        Path root = tempRoot();
        run("new", "assinado", "--dir", root.toString());
        Path dir = root.resolve("assinado");
        String prefix = root.resolve("minha").toString();

        assertEquals(0, run("keygen", prefix).code());
        assertEquals(1, run("keygen", prefix).code(), "não sobrescreve chave");
        assertEquals(1, run("verify", dir.toString()).code(), "ainda sem pack.lock");

        assertEquals(0, run("pack", dir.toString(), "--key", prefix + ".key").code());
        Out ok = run("verify", dir.toString(), "--public-key", prefix + ".pub");
        assertEquals(0, ok.code(), ok.err());
        assertTrue(ok.out().contains("OK assinado"));

        Files.writeString(dir.resolve("remediation/remediation.yml"), "- key: x\n  summary: adulterado\n");
        Out bad = run("verify", dir.toString(), "--public-key", prefix + ".pub");
        assertEquals(1, bad.code());
        assertTrue(bad.err().contains("INVÁLIDO: arquivo alterado: remediation/remediation.yml"), bad.err());
    }

    @Test
    void packRecusaPacoteInvalidoEArquivoDeChaveEstranho() throws Exception {
        Path root = tempRoot();
        write(root.resolve("ruim/pack.yml"), "id: ruim\n");
        assertEquals(1, run("pack", root.resolve("ruim").toString()).code());

        Path bom = pack(root, "bom", "1.0.0", true);
        Path lixo = root.resolve("lixo.key");
        Files.writeString(lixo, "x".repeat(20_000));
        assertEquals(2, run("pack", bom.toString(), "--key", lixo.toString()).code());
        assertEquals(2, run("pack", bom.toString(), "--key").code(), "opção sem valor");
    }
}
