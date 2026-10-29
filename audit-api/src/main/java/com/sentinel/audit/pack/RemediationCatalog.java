package com.sentinel.audit.pack;

import com.sentinel.audit.model.Finding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orientações de correção, indexadas pelo id da regra: basta o id <i>conter</i> a chave (os ids do Semgrep vêm
 * prefixados pelo caminho do arquivo de regras) e vale a chave <b>mais específica</b> (a mais longa).
 * <p>
 * O conteúdo não está mais no código: vem de {@code remediation/*.yml} dos pacotes de regras e é instalado pelo
 * {@link com.sentinel.audit.pack.PackRegistry} a cada (re)carga. Para orientar uma regra nova, basta incluí-la no pacote.
 */
public final class RemediationCatalog {

    private static final String CWE_PREFIX = "CWE-";


    private static volatile List<RemediationEntry> entries = List.of();

    private RemediationCatalog() {}

    /** Substitui o catálogo. Em chaves repetidas entre pacotes, vale a do que vem depois. */
    public static void install(List<RemediationEntry> list) {
        Map<String, RemediationEntry> byKey = new LinkedHashMap<>();
        for (RemediationEntry e : list) {
            byKey.put(e.key(), e);
        }
        entries = List.copyOf(byKey.values());
    }

    /** Orientação para o finding, ou {@code null} se nenhuma regra do catálogo se aplica. */
    public static RemediationDto forFinding(Finding f) {
        String ruleId = f.getRuleId() == null ? "" : f.getRuleId().toLowerCase(Locale.ROOT);
        RemediationEntry best = null;
        for (RemediationEntry e : entries) {
            if (ruleId.contains(e.key()) && (best == null || e.key().length() > best.key().length())) {
                best = e;
            }
        }
        String own = referenceFor(f.getCveId());
        if (best != null) {
            List<String> refs = new ArrayList<>(best.refs());
            if (own != null && !refs.contains(own)) {
                refs.add(0, own);
            }
            return new RemediationDto(best.summary(), best.before(), best.after(), refs, best.effort(), best.regressionRisk());
        }
        return own == null ? null : new RemediationDto(null, null, null, List.of(own));
    }

    /** Link oficial do identificador (CWE, CVE ou GHSA). */
    static String referenceFor(String id) {
        if (id == null) {
            return null;
        }
        String v = id.trim().toUpperCase(Locale.ROOT);
        if (v.matches("CWE-\\d+")) {
            return "https://cwe.mitre.org/data/definitions/" + v.substring(CWE_PREFIX.length()) + ".html";
        }
        if (v.matches("CVE-\\d{4}-\\d+")) {
            return "https://nvd.nist.gov/vuln/detail/" + v;
        }
        if (v.startsWith("GHSA-")) {
            return "https://github.com/advisories/" + v;
        }
        return null;
    }
}
