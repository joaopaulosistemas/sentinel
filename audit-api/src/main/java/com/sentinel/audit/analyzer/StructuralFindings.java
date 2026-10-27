package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;

/** Monta os achados dos analisadores estruturais (ciclos, duplicação, pontos quentes, código morto). */
final class StructuralFindings {

    private StructuralFindings() {}

    static Finding make(AuditRun run, String tool, String ruleId, Severity severity, Category category,
                        String file, int line, String title, String description, String snippet) {
        Finding f = new Finding();
        f.setAuditRun(run);
        f.setToolSource(tool);
        f.setCategory(category);
        f.setSeverity(severity);
        f.setRuleId(ruleId);
        f.setTitle(title);
        f.setDescription(description);
        f.setFilePath(file);
        f.setLineNumber(Math.max(1, line));
        f.setCodeSnippet(snippet == null ? null : (snippet.length() > 300 ? snippet.substring(0, 300) : snippet));
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
