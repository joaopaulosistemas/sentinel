package com.sentinel.audit.analyzer.evidence;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;

/** Montagem dos findings das evidências (todos levam o analisador "Evidências" como origem). */
final class Ev {

    private static final int TITLE_MAX_CHARS = 480;
    private static final int TEXT_MAX_CHARS = 1900;
    private static final double BYTES_PER_UNIT = 1024;


    static final String TOOL = "Evidências";

    private Ev() {
    }

    static Finding finding(AuditRun run, Category category, Severity severity, String ruleId, String title,
                           String description, String file, Integer line, String snippet) {
        Finding f = new Finding();
        f.setAuditRun(run);
        f.setCategory(category);
        f.setSeverity(severity);
        f.setRuleId(ruleId);
        f.setTitle(limit(title, TITLE_MAX_CHARS));
        f.setDescription(limit(description, TEXT_MAX_CHARS));
        f.setFilePath(file);
        f.setLineNumber(line);
        f.setCodeSnippet(snippet == null ? null : limit(snippet.strip(), TEXT_MAX_CHARS));
        f.setToolSource(TOOL);
        return f;
    }

    static String limit(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    /** Texto de consulta numa linha só, para título e trecho. */
    static String oneLine(String sql, int max) {
        return limit(sql.replaceAll("\\s+", " ").strip(), max);
    }

    static String fmt(double v) {
        if (v == Math.rint(v)) {
            return String.format(java.util.Locale.ROOT, "%d", (long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    static String bytes(double b) {
        String[] unit = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (b >= BYTES_PER_UNIT && i < unit.length - 1) {
            b /= BYTES_PER_UNIT;
            i++;
        }
        return String.format(java.util.Locale.ROOT, i == 0 ? "%.0f %s" : "%.1f %s", b, unit[i]);
    }
}
