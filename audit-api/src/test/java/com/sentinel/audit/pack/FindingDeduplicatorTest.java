package com.sentinel.audit.pack;

import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FindingDeduplicatorTest {

    @org.junit.jupiter.api.BeforeEach
    void carregaPacotes() {
        com.sentinel.audit.pack.TestPacks.registry();
    }

    private static Finding f(String tool, String rule, String file, Integer line, Severity sev) {
        Finding f = new Finding();
        f.setToolSource(tool);
        f.setRuleId(rule);
        f.setFilePath(file);
        f.setLineNumber(line);
        f.setSeverity(sev);
        f.setTitle(rule);
        f.setDescription("desc");
        return f;
    }

    @Test
    void mesmoProblemaDeDuasFerramentasViraUmSoComASeveridadeMaior() {
        Finding semgrep = f("Semgrep", "audit-rules.rules.quality.sentinel-ts-var", "web/a.ts", 10, Severity.MEDIUM);
        Finding eslint = f("ESLint", "eslint-no-var", "web/a.ts", 0, Severity.HIGH);

        List<Finding> r = FindingDeduplicator.apply(List.of(semgrep, eslint));

        assertEquals(1, r.size());
        assertSame(semgrep, r.get(0));                                         // fica o da ferramenta preferida
        assertEquals(Severity.HIGH, semgrep.getSeverity());                    // com a maior severidade
        assertEquals("audit-rules.rules.quality.sentinel-ts-var", semgrep.getRuleId());   // id estável (fingerprint)
        assertTrue(semgrep.getDescription().contains("Também detectado por ESLint (eslint-no-var)"));
    }

    @Test
    void complexidadeDoCleanCodeAbsorveADoCheckstyleNaMesmaFuncao() {
        Finding cc = f("CleanCode", "sentinel-cc-complexidade", "api/A.java", 40, Severity.MEDIUM);
        Finding cs = f("Checkstyle", "checkstyle-com.puppycrawl.tools.checkstyle.checks.metrics.CyclomaticComplexityCheck", "api/A.java", 41, Severity.LOW);
        Finding outraFuncao = f("Checkstyle", "checkstyle-com.puppycrawl.tools.checkstyle.checks.metrics.CyclomaticComplexityCheck", "api/A.java", 90, Severity.LOW);

        List<Finding> r = FindingDeduplicator.apply(List.of(cc, cs, outraFuncao));

        assertEquals(2, r.size());
        assertSame(cc, r.get(0));                                              // fica o da ferramenta que o tópico prefere
        assertSame(outraFuncao, r.get(1));                                     // outra função não some
        assertEquals(Severity.MEDIUM, cc.getSeverity());
        assertTrue(cc.getDescription().contains("Também detectado por Checkstyle"));
    }

    @Test
    void severidadeMenorDoOutroNaoRebaixaOQueFicou() {
        Finding semgrep = f("Semgrep", "sentinel-ts-var", "web/a.ts", 1, Severity.HIGH);
        Finding eslint = f("ESLint", "eslint-no-var", "web/a.ts", 1, Severity.LOW);
        FindingDeduplicator.apply(List.of(semgrep, eslint));
        assertEquals(Severity.HIGH, semgrep.getSeverity());
    }

    @Test
    void semParNaMesmaPosicaoNadaEJuntado() {
        Finding semgrep = f("Semgrep", "sentinel-ts-var", "web/a.ts", 5, Severity.HIGH);
        Finding outroArquivo = f("ESLint", "eslint-no-var", "web/b.ts", 5, Severity.HIGH);
        assertEquals(2, FindingDeduplicator.apply(List.of(semgrep, outroArquivo)).size());

        Finding soEslint = f("ESLint", "eslint-no-var", "web/a.ts", 3, Severity.HIGH);
        assertEquals(1, FindingDeduplicator.apply(List.of(soEslint)).size());      // sem âncora do Sentinel: mantém
    }

    @Test
    void mesmaRegraExigeMesmaLinha() {
        // o mesmo problema em duas linhas do mesmo arquivo são ocorrências distintas
        Finding s1 = f("Semgrep", "sentinel-ts-igualdade-frouxa", "web/a.ts", 1, Severity.MEDIUM);
        Finding t1 = f("ESLint", "eslint-eqeqeq", "web/a.ts", 1, Severity.MEDIUM);
        Finding t20 = f("ESLint", "eslint-eqeqeq", "web/a.ts", 20, Severity.MEDIUM);
        List<Finding> r = FindingDeduplicator.apply(List.of(s1, t1, t20));
        assertEquals(List.of(s1, t20), r);
    }

    @Test
    void topicosDiferentesNaoSeMisturam() {
        Finding root = f("Semgrep", "sentinel-ts-var", "web/a.ts", 1, Severity.MEDIUM);
        Finding latest = f("ESLint", "eslint-eqeqeq", "web/a.ts", 1, Severity.MEDIUM);
        assertEquals(2, FindingDeduplicator.apply(List.of(root, latest)).size());
    }

    @Test
    void regrasDesconhecidasEMesmaFerramentaNuncaSaoTocadas() {
        Finding a = f("Semgrep", "sentinel-ts-var", "web/a.ts", 1, Severity.MEDIUM);
        Finding b = f("Semgrep", "sentinel-ts-var", "web/a.ts", 1, Severity.MEDIUM);   // mesma ferramenta: não colapsa
        Finding x = f("ESLint", "trivy-AVD-AWS-0107", "main.tf", 3, Severity.HIGH);
        Finding y = f("Semgrep", "sentinel-tf-aberto-para-internet", "main.tf", 3, Severity.HIGH);
        Finding semRegra = f("ESLint", null, "web/a.ts", 1, Severity.LOW);
        assertEquals(5, FindingDeduplicator.apply(List.of(a, b, x, y, semRegra)).size());
    }

    @Test
    void naoAlteraAListaOriginalNemAOrdem() {
        Finding semgrep = f("Semgrep", "sentinel-ts-var", "web/a.ts", 1, Severity.MEDIUM);
        Finding outro = f("Semgrep", "sentinel-xss", "a.js", 1, Severity.HIGH);
        Finding eslint = f("ESLint", "eslint-no-var", "web/a.ts", 1, Severity.MEDIUM);
        List<Finding> original = List.of(semgrep, outro, eslint);

        List<Finding> r = FindingDeduplicator.apply(original);

        assertEquals(3, original.size());
        assertEquals(List.of(semgrep, outro), r);
        assertTrue(FindingDeduplicator.apply(List.of()).isEmpty());
    }

    @Test
    void aplicarDuasVezesNaoRepeteANota() {
        Finding semgrep = f("Semgrep", "sentinel-ts-var", "web/a.ts", 1, Severity.MEDIUM);
        Finding eslint = f("ESLint", "eslint-no-var", "web/a.ts", 1, Severity.MEDIUM);
        FindingDeduplicator.apply(List.of(semgrep, eslint));
        FindingDeduplicator.apply(List.of(semgrep, eslint));
        assertEquals(1, semgrep.getDescription().split("Também detectado", -1).length - 1);
    }

    @Test
    void lintEQuebraDeRegraSemgrepDoMesmoPontoViramUmSo() {
        List<Finding> in = List.of(
                f("Semgrep", "rules.quality.sentinel-ts-var", "web/a.ts", 2, Severity.LOW),
                f("ESLint", "eslint-no-var", "web/a.ts", 2, Severity.MEDIUM),
                f("Semgrep", "rules.quality.sentinel-java-catch-vazio", "A.java", 7, Severity.LOW),
                f("PMD", "pmd-EmptyCatchBlock", "A.java", 8, Severity.MEDIUM));

        List<Finding> r = FindingDeduplicator.apply(in);

        assertEquals(2, r.size());
        assertTrue(r.stream().allMatch(x -> "Semgrep".equals(x.getToolSource())), "fica o finding do Semgrep");
        assertTrue(r.stream().allMatch(x -> x.getSeverity() == Severity.MEDIUM), "com a maior severidade");
        assertTrue(r.get(0).getDescription().contains("Também detectado por ESLint (eslint-no-var)"));
    }

    @Test
    void retornoNuloDeColecaoApontadoPeloPmdEPeloSemgrepViraUm() {
        List<Finding> in = List.of(
                f("Semgrep", "rules.quality.sentinel-java-retorna-null-colecao", "Versions.java", 21, Severity.MEDIUM),
                f("PMD", "pmd-ReturnEmptyCollectionRatherThanNull", "Versions.java", 23, Severity.HIGH));

        List<Finding> r = FindingDeduplicator.apply(in);

        assertEquals(1, r.size());
        assertEquals(Severity.HIGH, r.get(0).getSeverity());
    }

    @Test
    void lintEmOutroLugarOuOutraRegraNaoEJuntado() {
        List<Finding> in = List.of(
                f("Semgrep", "rules.quality.sentinel-ts-var", "web/a.ts", 2, Severity.LOW),
                f("ESLint", "eslint-no-var", "web/a.ts", 40, Severity.MEDIUM),           // linha distante
                f("ESLint", "eslint-no-var", "web/b.ts", 2, Severity.MEDIUM),            // outro arquivo
                f("ESLint", "eslint-prefer-const", "web/a.ts", 2, Severity.MEDIUM),      // outra regra
                f("PMD", "pmd-UnusedPrivateField", "A.java", 5, Severity.LOW));

        assertEquals(5, FindingDeduplicator.apply(in).size());
    }
}
