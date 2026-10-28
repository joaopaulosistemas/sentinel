package com.sentinel.audit.service;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.RiskScore;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para {@link ScoringService}.
 */
class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    @Test
    void scoreDeveSerCemQuandoNaoHaFindings() {
        RiskScore score = scoringService.calculate(List.of());

        assertEquals(100.0, score.getOverall());
        assertEquals(100.0, score.getSecurity());
        assertEquals(100.0, score.getQuality());
        assertEquals(100.0, score.getArchitecture());
        assertEquals(100.0, score.getDependencies());
        assertEquals(100.0, score.getObservability());
        assertEquals(100.0, score.getPerformance());
    }

    @Test
    void umFindingCriticalDeveReduzirScoreDeSeguranca() {
        Finding finding = buildFinding(Category.SECURITY, Severity.CRITICAL);

        RiskScore score = scoringService.calculate(List.of(finding));

        // 100 - 20 (penalidade CRITICAL) = 80
        assertEquals(80.0, score.getSecurity());
        // Dimensões sem findings continuam em 100
        assertEquals(100.0, score.getQuality());
        assertEquals(100.0, score.getArchitecture());
    }

    @Test
    void scoreGeralDeveRespeiteOsPesosDosDimensoes() {
        // SECURITY tem peso 0.30, CRITICAL penaliza -20 => security = 80
        // geral = 80 * 0.30 + 100 * 0.70 = 24 + 70 = 94
        Finding finding = buildFinding(Category.SECURITY, Severity.CRITICAL);

        RiskScore score = scoringService.calculate(List.of(finding));

        double expectedOverall = (80 * 0.30) + (100 * 0.20) + (100 * 0.20) + (100 * 0.10) + (100 * 0.10) + (100 * 0.10);
        assertEquals(Math.round(expectedOverall * 100.0) / 100.0, score.getOverall());
    }

    @Test
    void scoreNuncaDeveSerNegativo() {
        // Muitos findings CRITICAL devem levar o score a 0, não a negativo
        List<Finding> muitosFindings = List.of(
                buildFinding(Category.SECURITY, Severity.CRITICAL),
                buildFinding(Category.SECURITY, Severity.CRITICAL),
                buildFinding(Category.SECURITY, Severity.CRITICAL),
                buildFinding(Category.SECURITY, Severity.CRITICAL),
                buildFinding(Category.SECURITY, Severity.CRITICAL),
                buildFinding(Category.SECURITY, Severity.CRITICAL),
                buildFinding(Category.SECURITY, Severity.CRITICAL)
        );

        RiskScore score = scoringService.calculate(muitosFindings);

        assertTrue(score.getSecurity() >= 0.0);
        assertTrue(score.getOverall() >= 0.0);
    }

    @Test
    void calculaDimensionScoreParaCategoriaEspecifica() {
        Finding highQuality = buildFinding(Category.QUALITY, Severity.HIGH);
        Finding mediumQuality = buildFinding(Category.QUALITY, Severity.MEDIUM);

        // 100 - 10 (HIGH) - 5 (MEDIUM) = 85
        double score = scoringService.calculateDimensionScore(
                List.of(highQuality, mediumQuality), Category.QUALITY);

        assertEquals(85.0, score);
    }

    @Test
    void ocorrenciasBaixasRepetidasDaMesmaRegraTemTeto() {
        List<Finding> muitas = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Finding f = buildFinding(Category.ARCHITECTURE, Severity.LOW);
            f.setRuleId("arch-magic-number");
            muitas.add(f);
        }
        // 30 x 2 = 60, mas o teto por regra (LOW) é 20 => 80
        assertEquals(80.0, scoringService.calculateDimensionScore(muitas, Category.ARCHITECTURE));
    }

    @Test
    void regrasDiferentesDeSeveridadeBaixaSomamNormalmente() {
        List<Finding> varias = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Finding f = buildFinding(Category.QUALITY, Severity.LOW);
            f.setRuleId("regra-" + i);
            varias.add(f);
        }
        assertEquals(90.0, scoringService.calculateDimensionScore(varias, Category.QUALITY));
    }

    @Test
    void criticalNuncaTemTetoPorRegra() {
        List<Finding> segredos = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Finding f = buildFinding(Category.SECURITY, Severity.CRITICAL);
            f.setRuleId("secrets-password-assignment");
            segredos.add(f);
        }
        assertEquals(0.0, scoringService.calculateDimensionScore(segredos, Category.SECURITY));
    }

    @Test
    void desempenhoEUmaDimensaoPropria() {
        Finding f = buildFinding(Category.PERFORMANCE, Severity.HIGH);

        RiskScore score = scoringService.calculate(List.of(f));

        assertEquals(90.0, score.getPerformance());
        assertEquals(100.0, score.getQuality());
    }

    @Test
    void projetoGrandeToleraProporcionalmenteMaisAchados() {
        List<Finding> achados = List.of(
                buildFinding(Category.QUALITY, Severity.HIGH),
                buildFinding(Category.QUALITY, Severity.HIGH));

        double semTamanho = scoringService.calculate(achados).getQuality();
        double pequeno = scoringService.calculate(achados, 5_000L).getQuality();
        double grande = scoringService.calculate(achados, 80_000L).getQuality();

        assertEquals(80.0, semTamanho);
        assertEquals(80.0, pequeno, "abaixo da referência não muda nada");
        // 80 mil linhas: fator = raiz(20 mil / 80 mil) = 0,5 => penalidade 20 * 0,5 = 10
        assertEquals(90.0, grande);
    }

    @Test
    void criticalNuncaEDiluidoPeloTamanho() {
        List<Finding> achados = List.of(buildFinding(Category.SECURITY, Severity.CRITICAL));

        assertEquals(80.0, scoringService.calculate(achados, 1_000_000L).getSecurity());
    }

    @Test
    void fatorTemPisoParaSistemasEnormes() {
        assertEquals(0.2, scoringService.sizeFactor(100_000_000L));
        assertEquals(1.0, scoringService.sizeFactor(null));
    }

    @Test
    void densidadePodeSerDesligada() {
        scoringService.densityEnabled = false;
        List<Finding> achados = List.of(buildFinding(Category.QUALITY, Severity.HIGH));

        assertEquals(90.0, scoringService.calculate(achados, 1_000_000L).getQuality());
    }

    @Test
    void curvaLinearDoServicoCriadoSemConfiguracaoZeraComMuitaPenalidade() {
        assertEquals(0.0, scoringService.applyCurve(250.0));
        assertEquals(90.0, scoringService.applyCurve(10.0), 0.0001);
    }

    @Test
    void curvaSuaveNuncaZeraEPreservaPenalidadesPequenas() {
        scoringService.curve = "suave";
        assertEquals(100.0, scoringService.applyCurve(0.0), 0.0001);
        assertEquals(90.48, scoringService.applyCurve(10.0), 0.01);
        assertTrue(scoringService.applyCurve(250.0) > 0.0);
        // continua distinguindo ruim de péssimo
        assertTrue(scoringService.applyCurve(250.0) > scoringService.applyCurve(400.0));
    }

    @Test
    void curvaSuaveNaoMudaOrdemDeSistemas() {
        scoringService.curve = "suave";
        List<Finding> poucos = List.of(buildFinding(Category.SECURITY, Severity.HIGH));
        List<Finding> muitos = List.of(buildFinding(Category.SECURITY, Severity.CRITICAL),
                buildFinding(Category.SECURITY, Severity.CRITICAL), buildFinding(Category.SECURITY, Severity.CRITICAL),
                buildFinding(Category.SECURITY, Severity.CRITICAL), buildFinding(Category.SECURITY, Severity.CRITICAL),
                buildFinding(Category.SECURITY, Severity.CRITICAL), buildFinding(Category.SECURITY, Severity.CRITICAL));
        assertTrue(scoringService.calculate(poucos).getSecurity() > scoringService.calculate(muitos).getSecurity());
        assertTrue(scoringService.calculate(muitos).getSecurity() > 0.0);
    }

    private Finding withRule(Category category, Severity severity, String rule) {
        Finding f = buildFinding(category, severity);
        f.setRuleId(rule);
        return f;
    }

    @Test
    void explicacaoBateComANotaEMarcaOndeOTetoPorRegraCortou() {
        List<Finding> findings = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            findings.add(withRule(Category.QUALITY, Severity.MEDIUM, "regra-repetida"));   // 6 x 5 = 30, teto 20
        }
        findings.add(withRule(Category.QUALITY, Severity.LOW, "regra-rara"));
        findings.add(withRule(Category.SECURITY, Severity.HIGH, "segredo"));

        var explanation = scoringService.explain(findings, null);

        assertEquals(scoringService.calculate(findings).getOverall(), explanation.overall(), 0.0001);
        assertEquals(6, explanation.dimensions().size());
        var security = explanation.dimensions().get(0);
        assertEquals(Category.SECURITY, security.category());
        assertEquals(90.0, security.score(), 0.0001);
        var quality = explanation.dimensions().get(1);
        assertEquals(78.0, quality.score(), 0.0001);                    // 100 - 20 (com teto) - 2
        var repetida = quality.groups().get(0);
        assertEquals("regra-repetida", repetida.ruleId());
        assertEquals(6, repetida.count());
        assertEquals(30.0, repetida.raw(), 0.0001);
        assertEquals(20.0, repetida.counted(), 0.0001);
        assertTrue(repetida.capped());
        assertFalse(quality.groups().get(1).capped());
        assertEquals(0, quality.hidden());
    }

    @Test
    void simulacaoSemUmaRegraMostraANotaRealEANova() {
        List<Finding> findings = List.of(withRule(Category.SECURITY, Severity.HIGH, "segredo"),
                withRule(Category.SECURITY, Severity.HIGH, "Outra"));

        var simulated = scoringService.explainWithout(findings, null, List.of(" SEGREDO ", ""));

        assertEquals(List.of("SEGREDO"), simulated.removedRules());
        assertEquals(scoringService.calculate(findings).getOverall(), simulated.overallBefore(), 0.0001);
        assertTrue(simulated.overall() > simulated.overallBefore());
        assertEquals(1, simulated.dimensions().get(0).groups().size());
        assertTrue(scoringService.explainWithout(findings, null, List.of()).removedRules().isEmpty());
    }

    private Finding buildFinding(Category category, Severity severity) {
        Finding f = new Finding();
        f.setCategory(category);
        f.setSeverity(severity);
        f.setTitle("Test finding");
        return f;
    }
}
