package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.pack.ArchitecturePolicy.Convention;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Aplica uma convenção do pacote de regras (`require` ou `forbid`) a um arquivo, com limite de tempo para a expressão regular. */
final class ArchConventions {

    private static final Logger LOG = Logger.getLogger(ArchConventions.class);
    private static final int SNIPPET_MAX_CHARS = 200;
    private static final int MAX_MATCHES_PER_CONVENTION_FILE = 20;
    private static final long REGEX_BUDGET_NANOS = 2_000_000_000L;

    private ArchConventions() {
    }

    static void apply(Convention cv, String rel, String content, AuditRun run, List<Finding> out) {
        ConvCtx ctx = new ConvCtx(cv, rel, run);
        try {
            requireCheck(ctx, content, out);
            forbidCheck(ctx, content, out);
        } catch (RegexBudgetExceeded e) {
            LOG.warnf("Convenção '%s' abortada em %s: expressão regular muito lenta", cv.id(), rel);
        }
    }

    /** Uma convenção aplicada a um arquivo: de onde vêm o id, a gravidade, a categoria e o título dos findings. */
    private record ConvCtx(Convention cv, String rel, AuditRun run) {
        ArchitecturePolicyAnalyzer.ArchRule rule(String message) {
            return new ArchitecturePolicyAnalyzer.ArchRule("sentinel-arch-conv-" + cv.id(),
                    cv.severity() == null ? Severity.MEDIUM : cv.severity(),
                    cv.category() == null ? Category.ARCHITECTURE : cv.category(),
                    cv.title() != null ? cv.title() : cv.id(), message);
        }
    }

    /** `require`: o arquivo precisa conter o padrão. */
    private static void requireCheck(ConvCtx x, String content, List<Finding> out) {
        Convention cv = x.cv();
        if (cv.require() != null && !Pattern.compile(cv.require(), Pattern.MULTILINE).matcher(limited(content)).find()) {
            out.add(ArchitecturePolicyAnalyzer.archFinding(x.run(), new ArchitecturePolicyAnalyzer.ArchPlace(x.rel(), 1, ""),
                    x.rule(cv.message() == null ? "O arquivo deveria conter /" + cv.require() + "/." : cv.message())));
        }
    }

    /** `forbid`: cada trecho que casa com o padrão vira um finding (até o limite por arquivo). */
    private static void forbidCheck(ConvCtx x, String content, List<Finding> out) {
        Convention cv = x.cv();
        if (cv.forbid() == null) {
            return;
        }
        Matcher m = Pattern.compile(cv.forbid(), Pattern.MULTILINE).matcher(limited(content));
        int n = 0;
        while (m.find() && n < MAX_MATCHES_PER_CONVENTION_FILE) {
            n++;
            String text = m.group().strip();
            String snippet = text.length() > SNIPPET_MAX_CHARS ? text.substring(0, SNIPPET_MAX_CHARS) : text;
            out.add(ArchitecturePolicyAnalyzer.archFinding(x.run(), new ArchitecturePolicyAnalyzer.ArchPlace(x.rel(), lineOf(content, m.start()), snippet),
                    x.rule(cv.message() == null ? "Trecho proibido pela convenção: /" + cv.forbid() + "/." : cv.message())));
        }
    }

    /** Aborta a busca se a regex consumir tempo demais (proteção contra backtracking catastrófico em pacote de terceiros). */
    private static CharSequence limited(CharSequence s) {
        long deadline = System.nanoTime() + REGEX_BUDGET_NANOS;
        return new CharSequence() {
            @Override public int length() { return s.length(); }
            @Override public char charAt(int i) {
                if (System.nanoTime() > deadline) {
                    throw new RegexBudgetExceeded();
                }
                return s.charAt(i);
            }
            @Override public CharSequence subSequence(int a, int b) { return limited(s.subSequence(a, b)); }
            @Override public String toString() { return s.toString(); }
        };
    }

    private static final class RegexBudgetExceeded extends RuntimeException {
        RegexBudgetExceeded() { super(null, null, false, false); }
    }

    private static int lineOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
