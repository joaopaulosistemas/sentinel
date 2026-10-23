package com.sentinel.audit.review;

/**
 * Regras do funil de promoção, sem acesso a banco: dado o estado e a evidência, diz se o passo é permitido.
 * Proposta → revisão humana → teste de amostra → um ciclo de observação (fora da nota) → promovida ou descartada.
 */
public final class RuleFunnel {

    public static final double DEFAULT_MIN_PRECISION = 0.90;
    public static final int DEFAULT_MIN_HITS = 5;

    private RuleFunnel() {}

    /** Resultado de uma tentativa de passo: {@code ok} ou o motivo da recusa. */
    public record Verdict(boolean ok, String reason) {
        static Verdict yes() { return new Verdict(true, null); }
        static Verdict no(String reason) { return new Verdict(false, reason); }
    }

    /** Evidência colhida durante a observação. */
    public record Evidence(int hits, int falsePositives, int auditsSinceStart) {
        public double precision() {
            return hits == 0 ? 0.0 : (double) (hits - falsePositives) / hits;
        }
    }

    public static Verdict review(RuleStage from) {
        return from == RuleStage.PROPOSED ? Verdict.yes() : Verdict.no("Só uma proposta nova pode ser revisada.");
    }

    public static Verdict sampleTest(RuleStage from) {
        return from == RuleStage.REVIEWED ? Verdict.yes() : Verdict.no("O teste de amostra vem depois da revisão por uma pessoa.");
    }

    public static Verdict startObservation(RuleStage from) {
        return from == RuleStage.SAMPLE_PASSED ? Verdict.yes() : Verdict.no("A observação só começa depois de a regra passar no teste de amostra.");
    }

    /** Promover exige um ciclo de observação completo, achados suficientes e precisão acima do mínimo. */
    public static Verdict promote(RuleStage from, Evidence e, double minPrecision, int minHits) {
        if (from != RuleStage.OBSERVING) {
            return Verdict.no("Só uma regra em observação pode ser promovida.");
        }
        if (e.auditsSinceStart() < 1) {
            return Verdict.no("Falta pelo menos uma auditoria concluída depois do início da observação.");
        }
        if (e.hits() < minHits) {
            return Verdict.no("Poucos achados para decidir: " + e.hits() + " de pelo menos " + minHits + ".");
        }
        if (e.precision() < minPrecision) {
            return Verdict.no(String.format("Precisão de %.0f%%, abaixo do mínimo de %.0f%%.", e.precision() * 100, minPrecision * 100));
        }
        return Verdict.yes();
    }

    public static Verdict discard(RuleStage from) {
        return from.terminal() ? Verdict.no("A regra já está encerrada.") : Verdict.yes();
    }

    /**
     * Uma regra nascida num sistema só vai para o pacote compartilhado depois de ter funcionado em outro:
     * pelo menos uma cópia dela, em outro sistema, promovida.
     */
    public static boolean sharedEligible(RuleStage own, int promotedCopiesInOtherSystems) {
        return own == RuleStage.PROMOTED && promotedCopiesInOtherSystems >= 1;
    }
}
