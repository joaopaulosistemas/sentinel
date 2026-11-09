package com.sentinel.audit.insights;

import java.util.List;
import java.util.Locale;

/** Diz se a nota de uma auditoria foi calculada com todos os analisadores, ou se algum falhou ou ficou pela metade. */
public final class AuditCompleteness {

    /** @param reasons uma linha por analisador que comprometeu o resultado, ex.: {@code trivy: falhou} */
    public record Result(boolean incomplete, List<String> reasons) {
        static final Result COMPLETE = new Result(false, List.of());
    }

    private AuditCompleteness() {
    }

    /** Contexto ausente (auditoria antiga) não é tratado como incompleto: não há como saber. */
    public static Result of(AuditContextData data) {
        if (data == null || data.analyzerRuns() == null) {
            return Result.COMPLETE;
        }
        List<String> reasons = data.analyzerRuns().stream()
                .filter(run -> AnalyzerStatus.parse(run.status()).incomplete())
                .map(run -> run.name() + ": " + String.valueOf(run.status()).toLowerCase(Locale.ROOT))
                .toList();
        return reasons.isEmpty() ? Result.COMPLETE : new Result(true, reasons);
    }
}
