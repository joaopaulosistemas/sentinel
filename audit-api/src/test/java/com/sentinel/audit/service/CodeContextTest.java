package com.sentinel.audit.service;

import com.sentinel.audit.dto.FindingMapper;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O achado guarda o código real do arquivo ao redor da linha, sem copiar segredo nem sair do projeto. */
class CodeContextTest {

    @TempDir Path root;

    private static Finding finding(String file, Integer line, String tool, String rule) {
        Finding f = new Finding();
        f.setFilePath(file);
        f.setLineNumber(line);
        f.setToolSource(tool);
        f.setRuleId(rule);
        f.setCategory(Category.QUALITY);
        f.setSeverity(Severity.MEDIUM);
        f.setTitle("t");
        return f;
    }

    private Path write(String name, String content) throws IOException {
        Path p = root.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private static String lines(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> "linha " + i).collect(Collectors.joining("\n"));
    }

    @Test
    void guardaAsLinhasReaisAoRedorDaLinhaDoAchado() throws IOException {
        write("src/ContaService.java", lines(20));
        Finding f = finding("src/ContaService.java", 10, "Quality", "clean-code.long-function");
        f.setCodeSnippet("public Conta atualizarSaldo(Conta c)");   // identidade: não pode mudar

        CodeContext.attach(List.of(f), root);

        assertEquals(8, f.getCodeContextStart());
        assertEquals("linha 8\nlinha 9\nlinha 10\nlinha 11\nlinha 12\nlinha 13\nlinha 14", f.getCodeContext());
        assertEquals("public Conta atualizarSaldo(Conta c)", f.getCodeSnippet());
    }

    @Test
    void noInicioDoArquivoComecaNaLinhaUm() throws IOException {
        write("A.java", lines(3));
        Finding f = finding("A.java", 1, "Opengrep", "r");

        CodeContext.attach(List.of(f), root);

        assertEquals(1, f.getCodeContextStart());
        assertEquals("linha 1\nlinha 2\nlinha 3", f.getCodeContext());
    }

    @Test
    void achadoDeSegredoNuncaCopiaOArquivo() throws IOException {
        write("application.properties", "a=1\nsenha=abc123\nb=2");
        for (String tool : List.of("Secrets", "Secrets-History", "Gitleaks")) {
            Finding f = finding("application.properties", 2, tool, "generic.secret");
            CodeContext.attach(List.of(f), root);
            assertNull(f.getCodeContext(), tool);
        }
        Finding byRule = finding("application.properties", 2, "Opengrep", "generic.hardcoded-password");
        CodeContext.attach(List.of(byRule), root);
        assertNull(byRule.getCodeContext());
    }

    @Test
    void caminhoParaForaDoProjetoEIgnorado() throws IOException {
        Path outside = Files.createTempFile("fora", ".txt");
        Files.writeString(outside, "conteudo de fora");
        try {
            Finding f = finding("../" + outside.getFileName(), 1, "Quality", "r");
            CodeContext.attach(List.of(f), root);
            assertNull(f.getCodeContext());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void atalhoParaForaDoProjetoEIgnorado() throws IOException {
        Path outside = Files.createTempFile("fora", ".txt");
        Files.writeString(outside, "conteudo de fora");
        try {
            Files.createSymbolicLink(root.resolve("atalho.txt"), outside);
            Finding f = finding("atalho.txt", 1, "Quality", "r");
            CodeContext.attach(List.of(f), root);
            assertNull(f.getCodeContext());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void semArquivoSemLinhaOuLinhaAlemDoFimFicaSemContexto() throws IOException {
        write("A.java", lines(3));
        Finding semArquivo = finding("nao-existe.java", 1, "Quality", "r");
        Finding semLinha = finding("A.java", 0, "Quality", "r");
        Finding alemDoFim = finding("A.java", 99, "Quality", "r");
        Finding nula = finding("A.java", null, "Quality", "r");

        CodeContext.attach(List.of(semArquivo, semLinha, alemDoFim, nula), root);

        for (Finding f : List.of(semArquivo, semLinha, alemDoFim, nula)) {
            assertNull(f.getCodeContext());
            assertNull(f.getCodeContextStart());
        }
    }

    @Test
    void arquivoBinarioFicaSemContexto() throws IOException {
        Files.write(root.resolve("a.bin"), new byte[] {(byte) 0xff, (byte) 0xfe, 0, 1, (byte) 0xc3, 0x28, 10, 10});
        Finding f = finding("a.bin", 1, "Quality", "r");

        CodeContext.attach(List.of(f), root);

        assertNull(f.getCodeContext());
    }

    @Test
    void linhaMuitoLongaECortadaETudoCabeNaColuna() throws IOException {
        write("Min.js", "x".repeat(5000) + "\n" + "y".repeat(5000));
        Finding f = finding("Min.js", 1, "Quality", "r");

        CodeContext.attach(List.of(f), root);

        assertTrue(f.getCodeContext().startsWith("x".repeat(CodeContext.MAX_LINE_CHARS) + "…"));
        assertTrue(f.getCodeContext().length() <= CodeContext.MAX_CHARS);
    }

    @Test
    void respostaDaApiTrazOContextoESuaLinhaInicial() throws IOException {
        write("A.java", lines(10));
        Finding f = finding("A.java", 5, "Quality", "r");
        CodeContext.attach(List.of(f), root);

        var r = FindingMapper.toResponse(f);

        assertEquals(3, r.codeContextStart());
        assertTrue(r.codeContext().contains("linha 5"));
        assertFalse(r.codeContext().isBlank());
    }
}
