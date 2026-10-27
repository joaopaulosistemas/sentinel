package com.sentinel.audit.service;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Confidence;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.pack.PathRole;
import com.sentinel.audit.service.RuleQualityService.RuleQuality;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Atribui a cada achado um nível de confiança e o motivo, para o time olhar primeiro o que tem mais chance de ser real.
 * <p>
 * Sinais (do mais forte para o mais fraco):
 * <ol>
 *   <li>fora do código de produção (teste, documentação, exemplo, gerado) → baixa;</li>
 *   <li>segredo com cara de valor de exemplo ({@code changeme}, {@code xxxx}, {@code example}...) → baixa;</li>
 *   <li>regra que o time costuma marcar como falso positivo (a partir de {@link RuleQualityService}) → baixa;</li>
 *   <li>vulnerabilidade conhecida (CVE) ou achado confirmado por outra ferramenta no mesmo lugar → alta;</li>
 *   <li>regra em modo sombra → média, com o motivo registrado;</li>
 *   <li>sem sinais → média.</li>
 * </ol>
 * Não altera severidade nem remove nada: só informa.
 */
public final class FindingConfidence {

    private FindingConfidence() {}

    /** Quantas linhas de distância ainda contam como "o mesmo lugar" para duas ferramentas. */
    static final int SAME_PLACE_LINES = 2;
    /** Regra com taxa de falso positivo a partir daqui (e amostra mínima) perde confiança. */
    static final double RULE_LOW_RATE = RuleQualityService.REVIEW_RATE;

    private static final Set<String> PLACEHOLDERS = Set.of("example", "changeme", "change_me", "change-me", "your_", "your-",
            "xxxx", "dummy", "sample", "fake", "placeholder", "redacted", "<token>", "<secret>", "${", "{{", "todo");

    /** Calcula e grava {@code confidence}/{@code confidenceReason} em cada achado. */
    public static void assessAll(List<Finding> findings, Map<String, RuleQuality> ruleQuality) {
        Map<String, List<Finding>> byFile = new HashMap<>();
        for (Finding f : findings) {
            if (f.getFilePath() != null) {
                byFile.computeIfAbsent(f.getFilePath(), k -> new java.util.ArrayList<>()).add(f);
            }
        }
        for (Finding f : findings) {
            assess(f, ruleQuality, byFile.getOrDefault(f.getFilePath(), List.of()));
        }
    }

    static void assess(Finding f, Map<String, RuleQuality> ruleQuality, List<Finding> sameFile) {
        PathRole role = PathRole.of(f.getFilePath());
        if (role.isNonProduction() && f.getCategory() != Category.DEPENDENCY) {
            set(f, Confidence.LOW, "Em " + role.describe() + ": provavelmente não afeta o sistema em produção.");
            return;
        }
        if (f.getCategory() == Category.SECURITY && looksLikeExample(f.getCodeSnippet())) {
            set(f, Confidence.LOW, "O valor parece de exemplo (texto como 'example', 'changeme' ou 'xxxx').");
            return;
        }
        RuleQuality quality = ruleQuality == null ? null : ruleQuality.get(ruleKey(f));
        if (quality != null && quality.total() >= RuleQualityService.MIN_SAMPLE && quality.falsePositiveRate() >= RULE_LOW_RATE) {
            set(f, Confidence.LOW, "O time marcou " + Math.round(quality.falsePositiveRate() * 100) + "% dos achados desta regra como falso positivo.");
            return;
        }
        if (Boolean.TRUE.equals(f.getShadow())) {
            set(f, Confidence.MEDIUM, "Regra em modo sombra: ainda está sendo medida.");
            return;
        }
        if (f.getCveId() != null && !f.getCveId().isBlank()) {
            set(f, Confidence.HIGH, "Vulnerabilidade conhecida (" + f.getCveId() + ").");
            return;
        }
        String other = corroboratedBy(f, sameFile);
        if (other != null) {
            set(f, Confidence.HIGH, "Confirmado também por " + other + " no mesmo lugar.");
            return;
        }
        set(f, Confidence.MEDIUM, null);
    }

    /** Chave usada em {@link RuleQualityService#byRule()}: mesma normalização do cálculo de qualidade. */
    static String ruleKey(Finding f) {
        if (f.getRuleId() == null || f.getRuleId().isBlank()) {
            return "";
        }
        return RuleQualityService.normalize(f);
    }

    static boolean looksLikeExample(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return false;
        }
        String s = snippet.toLowerCase(Locale.ROOT);
        for (String marker : PLACEHOLDERS) {
            if (s.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** Nome de outra ferramenta que apontou a mesma categoria a poucas linhas daqui, ou {@code null}. */
    static String corroboratedBy(Finding f, List<Finding> sameFile) {
        if (f.getToolSource() == null || f.getLineNumber() == null) {
            return null;
        }
        for (Finding o : sameFile) {
            if (o == f || o.getToolSource() == null || o.getLineNumber() == null) {
                continue;
            }
            if (!o.getToolSource().equalsIgnoreCase(f.getToolSource()) && o.getCategory() == f.getCategory()
                    && Math.abs(o.getLineNumber() - f.getLineNumber()) <= SAME_PLACE_LINES) {
                return o.getToolSource();
            }
        }
        return null;
    }

    private static void set(Finding f, Confidence c, String reason) {
        f.setConfidence(c);
        f.setConfidenceReason(reason);
    }
}
