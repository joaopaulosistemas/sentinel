package com.sentinel.audit.service;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Confidence;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.RuleQualityService.RuleQuality;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FindingConfidenceTest {

    private static Finding f(Category cat, String tool, String file, Integer line) {
        Finding f = new Finding();
        f.setCategory(cat);
        f.setSeverity(Severity.HIGH);
        f.setToolSource(tool);
        f.setFilePath(file);
        f.setLineNumber(line);
        f.setRuleId("regra-x");
        return f;
    }

    private static Finding assess(Finding f, Map<String, RuleQuality> q, Finding... others) {
        FindingConfidence.assess(f, q, List.of(others));
        return f;
    }

    @Test
    void foraDaProducaoTemBaixaConfiancaComMotivo() {
        Finding t = assess(f(Category.SECURITY, "Gitleaks", "src/test/java/FooTest.java", 10), Map.of());
        assertEquals(Confidence.LOW, t.getConfidence());
        assertTrue(t.getConfidenceReason().contains("teste"));

        Finding d = assess(f(Category.QUALITY, "CleanCode", "docs/guia.md", 1), Map.of());
        assertEquals(Confidence.LOW, d.getConfidence());
        assertTrue(d.getConfidenceReason().contains("documentação"));
    }

    @Test
    void dependenciaVulneravelNaoPerdeConfiancaPorEstarEmArquivoDeBuild() {
        Finding dep = f(Category.DEPENDENCY, "OSV", "target/pom.xml", null);
        dep.setCveId("CVE-2024-1");
        assess(dep, Map.of());
        assertEquals(Confidence.HIGH, dep.getConfidence());
    }

    @Test
    void segredoComCaraDeExemploTemBaixaConfianca() {
        Finding s = f(Category.SECURITY, "Gitleaks", "src/main/resources/application.properties", 5);
        s.setCodeSnippet("api.key=changeme");
        assess(s, Map.of());
        assertEquals(Confidence.LOW, s.getConfidence());

        Finding real = f(Category.SECURITY, "Gitleaks", "src/main/resources/application.properties", 6);
        real.setCodeSnippet("api.key=" + "9fA3kQ2l" + "Z8xV1mN7");
        assess(real, Map.of());
        assertEquals(Confidence.MEDIUM, real.getConfidence());
        assertNull(real.getConfidenceReason());
    }

    @Test
    void regraQueOTimeCostumaRecusarPerdeConfianca() {
        Map<String, RuleQuality> q = Map.of("regra-x", new RuleQuality("regra-x", "Semgrep", 10, 5, 0, 0.5, true));
        Finding x = assess(f(Category.SECURITY, "Semgrep", "src/main/java/A.java", 3), q);
        assertEquals(Confidence.LOW, x.getConfidence());
        assertTrue(x.getConfidenceReason().contains("50%"));
    }

    @Test
    void amostraPequenaDeRegraNaoMudaNada() {
        Map<String, RuleQuality> q = Map.of("regra-x", new RuleQuality("regra-x", "Semgrep", 3, 3, 0, 1.0, false));
        assertEquals(Confidence.MEDIUM, assess(f(Category.SECURITY, "Semgrep", "src/main/java/A.java", 3), q).getConfidence());
    }

    @Test
    void duasFerramentasNoMesmoLugarSobemParaAlta() {
        Finding a = f(Category.SECURITY, "Semgrep", "src/main/java/A.java", 40);
        Finding b = f(Category.SECURITY, "PMD", "src/main/java/A.java", 41);
        assess(a, Map.of(), b);
        assertEquals(Confidence.HIGH, a.getConfidence());
        assertTrue(a.getConfidenceReason().contains("PMD"));

        Finding longe = f(Category.SECURITY, "PMD", "src/main/java/A.java", 90);
        assertEquals(Confidence.MEDIUM, assess(f(Category.SECURITY, "Semgrep", "src/main/java/A.java", 40), Map.of(), longe).getConfidence());

        Finding mesmaFerramenta = f(Category.SECURITY, "Semgrep", "src/main/java/A.java", 41);
        assertEquals(Confidence.MEDIUM, assess(f(Category.SECURITY, "Semgrep", "src/main/java/A.java", 40), Map.of(), mesmaFerramenta).getConfidence());
    }

    @Test
    void modoSombraFicaMedioComMotivo() {
        Finding s = f(Category.QUALITY, "Semgrep", "src/main/java/A.java", 3);
        s.setShadow(true);
        assess(s, Map.of());
        assertEquals(Confidence.MEDIUM, s.getConfidence());
        assertTrue(s.getConfidenceReason().contains("sombra"));
    }

    @Test
    void assessAllMarcaTodosEAgrupaPorArquivo() {
        Finding a = f(Category.SECURITY, "Semgrep", "src/main/java/A.java", 10);
        Finding b = f(Category.SECURITY, "PMD", "src/main/java/A.java", 10);
        Finding c = f(Category.QUALITY, "CleanCode", "src/test/java/ATest.java", 1);
        List<Finding> all = List.of(a, b, c);
        FindingConfidence.assessAll(all, Map.of());
        assertEquals(Confidence.HIGH, a.getConfidence());
        assertEquals(Confidence.HIGH, b.getConfidence());
        assertEquals(Confidence.LOW, c.getConfidence());
    }

    @Test
    void ponderacaoDaNotaReduzSoOsDeBaixaConfiancaQuandoLigada() {
        Finding baixa = f(Category.QUALITY, "CleanCode", "src/test/java/ATest.java", 1);
        baixa.setSeverity(Severity.HIGH);
        Finding media = f(Category.QUALITY, "CleanCode", "src/main/java/A.java", 1);
        media.setSeverity(Severity.HIGH);
        FindingConfidence.assessAll(List.of(baixa, media), Map.of());

        ScoringService desligado = new ScoringService();
        assertEquals(100.0 - 10.0 - 10.0, desligado.calculateDimensionScore(List.of(baixa, media), Category.QUALITY), 0.001);

        ScoringService ligado = new ScoringService();
        ligado.confidenceWeighting = true;
        // 10 (média) + 10 * 0,4 (baixa) = 14
        assertEquals(100.0 - 14.0, ligado.calculateDimensionScore(List.of(baixa, media), Category.QUALITY), 0.001);
    }
}
