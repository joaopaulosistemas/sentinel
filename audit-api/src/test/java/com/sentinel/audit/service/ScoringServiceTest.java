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
        // SECURITY tem peso 0.35, CRITICAL penaliza -20 => security = 80
        // geral = 80 * 0.35 + 100 * 0.65 = 28 + 65 = 93
        Finding finding = buildFinding(Category.SECURITY, Severity.CRITICAL);

        RiskScore score = scoringService.calculate(List.of(finding));

        double expectedOverall = (80 * 0.35) + (100 * 0.25) + (100 * 0.20) + (100 * 0.10) + (100 * 0.10);
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

    private Finding buildFinding(Category category, Severity severity) {
        Finding f = new Finding();
        f.setCategory(category);
        f.setSeverity(severity);
        f.setTitle("Test finding");
        return f;
    }
}
