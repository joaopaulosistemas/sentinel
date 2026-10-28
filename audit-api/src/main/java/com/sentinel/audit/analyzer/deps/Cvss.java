package com.sentinel.audit.analyzer.deps;

import java.util.HashMap;
import java.util.Map;

/** Cálculo da nota base CVSS v3.x a partir do vetor (suficiente para classificar severidade). */
final class Cvss {

    // Coeficientes da especificação CVSS v3.1 (FIRST), seção 7.4.
    private static final double AV_NETWORK = 0.85;
    private static final double AV_ADJACENT = 0.62;
    private static final double AV_LOCAL = 0.55;
    private static final double AV_PHYSICAL = 0.2;
    private static final double AC_LOW = 0.77;
    private static final double AC_HIGH = 0.44;
    private static final double PR_NONE = 0.85;
    private static final double PR_LOW_CHANGED = 0.68;
    private static final double PR_LOW_UNCHANGED = 0.62;
    private static final double PR_HIGH_CHANGED = 0.5;
    private static final double PR_HIGH_UNCHANGED = 0.27;
    private static final double UI_NONE = 0.85;
    private static final double UI_REQUIRED = 0.62;
    private static final double CIA_HIGH = 0.56;
    private static final double CIA_LOW = 0.22;
    private static final double IMPACT_CHANGED_FACTOR = 7.52;
    private static final double IMPACT_CHANGED_OFFSET = 0.029;
    private static final double IMPACT_CHANGED_SUBTRACT = 3.25;
    private static final double IMPACT_CHANGED_BASE = 0.02;
    private static final int IMPACT_CHANGED_EXPONENT = 15;
    private static final double IMPACT_UNCHANGED_FACTOR = 6.42;
    private static final double EXPLOITABILITY_FACTOR = 8.22;
    private static final double SCOPE_CHANGED_FACTOR = 1.08;


    private Cvss() {}

    /** @return nota base 0.0–10.0, ou -1 se o vetor não for CVSS v3 válido */
    static double baseScore(String vector) {
        if (vector == null || !vector.startsWith("CVSS:3")) {
            return -1;
        }
        Map<String, String> m = new HashMap<>();
        for (String part : vector.split("/")) {
            int i = part.indexOf(':');
            if (i > 0) {
                m.put(part.substring(0, i), part.substring(i + 1));
            }
        }
        try {
            boolean changed = "C".equals(m.get("S"));
            double av = switch (m.get("AV")) { case "N" -> AV_NETWORK; case "A" -> AV_ADJACENT; case "L" -> AV_LOCAL; default -> AV_PHYSICAL; };
            double ac = "L".equals(m.get("AC")) ? AC_LOW : AC_HIGH;
            String pr = m.get("PR");
            double prv = switch (pr) {
                case "N" -> PR_NONE;
                case "L" -> changed ? PR_LOW_CHANGED : PR_LOW_UNCHANGED;
                default -> changed ? PR_HIGH_CHANGED : PR_HIGH_UNCHANGED;
            };
            double ui = "N".equals(m.get("UI")) ? UI_NONE : UI_REQUIRED;
            double c = cia(m.get("C")), i = cia(m.get("I")), a = cia(m.get("A"));
            double iss = 1 - (1 - c) * (1 - i) * (1 - a);
            double impact = changed
                    ? IMPACT_CHANGED_FACTOR * (iss - IMPACT_CHANGED_OFFSET)
                            - IMPACT_CHANGED_SUBTRACT * Math.pow(iss - IMPACT_CHANGED_BASE, IMPACT_CHANGED_EXPONENT)
                    : IMPACT_UNCHANGED_FACTOR * iss;
            double exploit = EXPLOITABILITY_FACTOR * av * ac * prv * ui;
            if (impact <= 0) {
                return 0;
            }
            double raw = changed ? Math.min(SCOPE_CHANGED_FACTOR * (impact + exploit), 10) : Math.min(impact + exploit, 10);
            return Math.ceil(raw * 10) / 10.0;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static double cia(String v) {
        return switch (v) { case "H" -> CIA_HIGH; case "L" -> CIA_LOW; default -> 0.0; };
    }
}
