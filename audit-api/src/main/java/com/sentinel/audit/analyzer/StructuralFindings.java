package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;

/** Monta os achados dos analisadores estruturais (ciclos, duplicação, pontos quentes, código morto). */
final class StructuralFindings {

    private static final int SNIPPET_MAX_CHARS = 300;


    private StructuralFindings() {}

    /** Quem achou e que regra: ferramenta, id, gravidade e categoria. */
    record Kind(String tool, String ruleId, Severity severity, Category category) { }

    /** Onde: arquivo, linha (mínimo 1) e, opcionalmente, o trecho de código. */
    record Spot(String file, int line, String snippet) { }

    static Finding make(AuditRun run, Kind kind, Spot spot, String title, String description) {
        String snippet = spot.snippet();
        Finding f = new Finding();
        f.setAuditRun(run);
        f.setToolSource(kind.tool());
        f.setCategory(kind.category());
        f.setSeverity(kind.severity());
        f.setRuleId(kind.ruleId());
        f.setTitle(title);
        f.setDescription(description);
        f.setFilePath(spot.file());
        f.setLineNumber(Math.max(1, spot.line()));
        f.setCodeSnippet(snippet == null ? null : (snippet.length() > SNIPPET_MAX_CHARS ? snippet.substring(0, SNIPPET_MAX_CHARS) : snippet));
        return f;
    }

    /** Lista curta para texto: "a, b, c e mais N". */
    static String summarize(java.util.List<String> items, int max) {
        if (items.size() <= max) {
            return String.join(", ", items);
        }
        return String.join(", ", items.subList(0, max)) + " e mais " + (items.size() - max);
    }
}
