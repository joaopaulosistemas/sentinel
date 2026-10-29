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
        assessAll(findings, ruleQuality, null);
    }

    /**
     * @param globalAuth descrição da barreira de autenticação global do projeto (ex.: "filtro ApiKeyFilter"), ou nulo;
     *                   com ela, "endpoint sem anotação de autorização" perde confiança
     */
    public static void assessAll(List<Finding> findings, Map<String, RuleQuality> ruleQuality, String globalAuth) {
        assessAll(findings, ruleQuality, globalAuth, Set.of());
    }

    /**
     * @param generatedPaths arquivos com cabeçalho de código gerado (ver {@link GeneratedFiles}); contam como
     *                       {@link PathRole#GENERATED} mesmo fora de uma pasta com esse nome
     */
    public static void assessAll(List<Finding> findings, Map<String, RuleQuality> ruleQuality, String globalAuth,
                                 Set<String> generatedPaths) {
        Map<String, List<Finding>> byFile = new HashMap<>();
        for (Finding f : findings) {
            if (f.getFilePath() != null) {
                byFile.computeIfAbsent(f.getFilePath(), k -> new java.util.ArrayList<>()).add(f);
            }
        }
        for (Finding f : findings) {
            assess(f, ruleQuality, byFile.getOrDefault(f.getFilePath(), List.of()), globalAuth,
                    generatedPaths != null && generatedPaths.contains(f.getFilePath()));
        }
    }

    static void assess(Finding f, Map<String, RuleQuality> ruleQuality, List<Finding> sameFile) {
        assess(f, ruleQuality, sameFile, null);
    }

    /** Regra "endpoint sem anotação de autorização": só olha o recurso, não o filtro global. */
    static boolean isEndpointAuthorizationRule(Finding f) {
        return f.getRuleId() != null && f.getRuleId().toLowerCase(Locale.ROOT).contains("endpoint-sem-autorizacao");
    }

    static void assess(Finding f, Map<String, RuleQuality> ruleQuality, List<Finding> sameFile, String globalAuth) {
        assess(f, ruleQuality, sameFile, globalAuth, false);
    }

    static void assess(Finding f, Map<String, RuleQuality> ruleQuality, List<Finding> sameFile, String globalAuth,
                       boolean generatedByHeader) {
        Verdict v = lowConfidence(f, ruleQuality, globalAuth, generatedByHeader);
        if (v == null) {
            v = otherwise(f, sameFile);
        }
        set(f, v.level(), v.note());
    }

    /** Nível de confiança e a explicação mostrada ao usuário. */
    private record Verdict(Confidence level, String note) { }

    /** Os motivos para desconfiar do achado; nulo se nenhum se aplica. */
    private static Verdict lowConfidence(Finding f, Map<String, RuleQuality> ruleQuality, String globalAuth,
                                         boolean generatedByHeader) {
        PathRole role = generatedByHeader ? PathRole.GENERATED : PathRole.of(f.getFilePath());
        if (role.isNonProduction() && f.getCategory() != Category.DEPENDENCY) {
            return new Verdict(Confidence.LOW, "Em " + role.describe() + ": provavelmente não afeta o sistema em produção.");
        }
        if (globalAuth != null && isEndpointAuthorizationRule(f)) {
            return new Verdict(Confidence.LOW, "O projeto autentica as rotas num ponto só (" + globalAuth + "), que esta regra não enxerga. "
                    + "Confirme se há rota pública fora dessa barreira.");
        }
        if (f.getCategory() == Category.SECURITY && looksLikeExample(f.getCodeSnippet())) {
            return new Verdict(Confidence.LOW, "O valor parece de exemplo (texto como 'example', 'changeme' ou 'xxxx').");
        }
        return lowByHistory(f, ruleQuality);
    }

    private static Verdict lowByHistory(Finding f, Map<String, RuleQuality> ruleQuality) {
        RuleQuality quality = ruleQuality == null ? null : ruleQuality.get(ruleKey(f));
        if (quality != null && quality.total() >= RuleQualityService.MIN_SAMPLE && quality.falsePositiveRate() >= RULE_LOW_RATE) {
            return new Verdict(Confidence.LOW, "O time marcou " + Math.round(quality.falsePositiveRate() * 100) + "% dos achados desta regra como falso positivo.");
        }
        return null;
    }

    /** Sem motivo para desconfiar: sombra, CVE conhecido e confirmação por outra ferramenta decidem o nível. */
    private static Verdict otherwise(Finding f, List<Finding> sameFile) {
        if (Boolean.TRUE.equals(f.getShadow())) {
            return new Verdict(Confidence.MEDIUM, "Regra em modo sombra: ainda está sendo medida.");
        }
        if (f.getCveId() != null && !f.getCveId().isBlank()) {
            return new Verdict(Confidence.HIGH, "Vulnerabilidade conhecida (" + f.getCveId() + ").");
        }
        String other = corroboratedBy(f, sameFile);
        if (other != null) {
            return new Verdict(Confidence.HIGH, "Confirmado também por " + other + " no mesmo lugar.");
        }
        return new Verdict(Confidence.MEDIUM, null);
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
