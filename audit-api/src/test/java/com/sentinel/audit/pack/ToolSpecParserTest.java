package com.sentinel.audit.pack;

import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSpecParserTest {

    private static Path pacoteComTools(String conteudo) throws IOException {
        Path dir = Files.createTempDirectory("tools");
        Files.writeString(Files.createDirectories(dir.resolve("tools")).resolve("t.yml"), conteudo);
        return dir;
    }

    private static boolean temErro(List<String> errors, String trecho) {
        return errors.stream().anyMatch(e -> e.contains(trecho));
    }

    @Test
    void ferramentaComCommandEOutputEValida() throws Exception {
        List<String> errors = new ArrayList<>();
        List<ToolSpec> out = ToolSpecParser.parse(pacoteComTools("""
                - id: checkstyle
                  name: Checkstyle
                  command: [checkstyle, "-f", sarif, "-o", "{output}", "{files}"]
                  applies-to: ["**/*.java"]
                  timeout-seconds: 120
                  severity-map: {error: high, warning: low}
                """), errors);

        assertTrue(errors.isEmpty(), errors.toString());
        ToolSpec t = out.get(0);
        assertEquals("Checkstyle", t.name());
        assertTrue(t.executes());
        assertEquals(120, t.timeoutSeconds());
        assertEquals(Severity.HIGH, t.levelSeverity().get("error"));
        assertEquals(List.of("**/*.java"), t.appliesTo());
    }

    @Test
    void ferramentaSoComInputLeArquivoSarifSemExecutarNada() throws Exception {
        List<String> errors = new ArrayList<>();
        List<ToolSpec> out = ToolSpecParser.parse(pacoteComTools("- id: externa\n  input: \"reports/*.sarif\"\n"), errors);

        assertTrue(errors.isEmpty(), errors.toString());
        assertFalse(out.get(0).executes());
        assertEquals("externa", out.get(0).name(), "sem name vale o id");
    }

    @Test
    void commandEInputJuntosOuNenhumSaoRecusados() throws Exception {
        List<String> errors = new ArrayList<>();
        List<ToolSpec> out = ToolSpecParser.parse(pacoteComTools("""
                - id: ambos
                  command: [x, "{output}"]
                  input: "a.sarif"
                - id: nenhum
                """), errors);

        assertTrue(out.isEmpty());
        assertEquals(2L, errors.stream().filter(e -> e.contains("exatamente um entre 'command' e 'input'")).count(), errors.toString());
    }

    @Test
    void comandoSemDestinoDeSaidaOuComOsDoisDestinosEErro() throws Exception {
        List<String> errors = new ArrayList<>();
        ToolSpecParser.parse(pacoteComTools("""
                - id: sem-saida
                  command: [x, "--sarif"]
                - id: dois-destinos
                  command: [x, "{output}"]
                  stdout: true
                - id: stdout-sem-comando
                  input: "a.sarif"
                  stdout: true
                """), errors);

        assertTrue(temErro(errors, "precisa do placeholder {output}"), errors.toString());
        assertTrue(temErro(errors, "com 'stdout: true' o comando não usa {output}"), errors.toString());
        assertTrue(temErro(errors, "'stdout' só vale com 'command'"), errors.toString());
    }

    @Test
    void globsQueSaemDoProjetoSaoRecusados() throws Exception {
        List<String> errors = new ArrayList<>();
        ToolSpecParser.parse(pacoteComTools("""
                - id: absoluto
                  command: [x, "{output}", "{files}"]
                  applies-to: ["/etc/**"]
                - id: sobe
                  command: [x, "{output}"]
                  applies-to: ["../fora/*.java"]
                - id: input-ruim
                  input: "../../etc/passwd"
                - id: files-sem-applies
                  command: [x, "{output}", "{files}"]
                """), errors);

        assertEquals(2L, errors.stream().filter(e -> e.contains("'applies-to' deve ser glob relativo")).count(), errors.toString());
        assertTrue(temErro(errors, "'input' deve ser um glob relativo ao projeto"), errors.toString());
        assertTrue(temErro(errors, "{files} exige 'applies-to'"), errors.toString());
    }

    @Test
    void idInvalidoIdRepetidoNivelInvalidoEChaveDesconhecidaViramErros() throws Exception {
        List<String> errors = new ArrayList<>();
        ToolSpecParser.parse(pacoteComTools("""
                - id: BAD_ID
                  input: "a.sarif"
                - id: repetida
                  input: "a.sarif"
                - id: repetida
                  input: "b.sarif"
                - id: niveis
                  input: "c.sarif"
                  severity-map: {fatal: high}
                  extra: 1
                """), errors);

        assertTrue(temErro(errors, "'id' ausente ou inválido"), errors.toString());
        assertTrue(temErro(errors, "ferramenta 'repetida' repetida"), errors.toString());
        assertTrue(temErro(errors, "nível 'fatal' inválido"), errors.toString());
        assertTrue(temErro(errors, "extra"), errors.toString());
    }

    @Test
    void pacoteSemPastaToolsNaoTemFerramentas() throws Exception {
        List<String> errors = new ArrayList<>();
        assertTrue(ToolSpecParser.parse(Files.createTempDirectory("vazio"), errors).isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void ladoEscritoNaFerramentaValeMaisQueADeducao() throws Exception {
        List<String> errors = new ArrayList<>();
        List<ToolSpec> out = ToolSpecParser.parse(pacoteComTools("""
                - id: njsscan
                  side: back
                  input: "reports/*.sarif"
                  applies-to: ["**/*.js", "**/*.ts"]
                - id: banco
                  side: common
                  input: "reports/*.sarif"
                  applies-to: ["**/*.sql"]
                - id: tela
                  side: front
                  input: "reports/*.sarif"
                """), errors);

        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(RuleSide.BACK, out.get(0).side(), "js e ts sozinhos seriam deduzidos como front");
        assertEquals(RuleSide.COMMON, out.get(1).side(), ".sql sozinho seria deduzido como back");
        assertEquals(RuleSide.FRONT, out.get(2).side());
    }

    @Test
    void semLadoAFerramentaEDeduzidaDosGlobsDeAppliesTo() throws Exception {
        List<String> errors = new ArrayList<>();
        List<ToolSpec> out = ToolSpecParser.parse(pacoteComTools("""
                - id: estilo
                  input: "reports/*.sarif"
                  applies-to: ["**/*.css", "**/*.scss"]
                - id: java
                  input: "reports/*.sarif"
                  applies-to: ["**/*.java"]
                - id: mistura
                  input: "reports/*.sarif"
                  applies-to: ["**/*.java", "**/*.ts"]
                - id: docker
                  input: "reports/*.sarif"
                  applies-to: ["**/Dockerfile"]
                - id: sem-globs
                  input: "reports/*.sarif"
                """), errors);

        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(RuleSide.FRONT, out.get(0).side());
        assertEquals(RuleSide.BACK, out.get(1).side());
        assertEquals(RuleSide.COMMON, out.get(2).side(), "mistura de lados é comum");
        assertEquals(RuleSide.COMMON, out.get(3).side());
        assertEquals(RuleSide.COMMON, out.get(4).side(), "na dúvida é comum");
    }

    @Test
    void ladoInvalidoNaFerramentaViraErroDeCarregamento() throws Exception {
        List<String> errors = new ArrayList<>();
        ToolSpecParser.parse(pacoteComTools("- id: ferramenta-x\n  side: meio\n  input: \"reports/*.sarif\"\n"), errors);

        assertTrue(temErro(errors, "'side' inválido 'meio'"), errors.toString());
        assertTrue(temErro(errors, "back, front ou common"), errors.toString());
    }
}
