package com.sentinel.audit.analyzer.deps;

import java.util.HashMap;
import java.util.Map;

/** Cálculo da nota base CVSS v3.x a partir do vetor (suficiente para classificar severidade). */
final class Cvss {

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
            double av = switch (m.get("AV")) { case "N" -> 0.85; case "A" -> 0.62; case "L" -> 0.55; default -> 0.2; };
            double ac = "L".equals(m.get("AC")) ? 0.77 : 0.44;
            String pr = m.get("PR");
            double prv = switch (pr) {
                case "N" -> 0.85;
                case "L" -> changed ? 0.68 : 0.62;
                default -> changed ? 0.5 : 0.27;
            };
            double ui = "N".equals(m.get("UI")) ? 0.85 : 0.62;
            double c = cia(m.get("C")), i = cia(m.get("I")), a = cia(m.get("A"));
            double iss = 1 - (1 - c) * (1 - i) * (1 - a);
            double impact = changed ? 7.52 * (iss - 0.029) - 3.25 * Math.pow(iss - 0.02, 15) : 6.42 * iss;
            double exploit = 8.22 * av * ac * prv * ui;
            if (impact <= 0) {
                return 0;
            }
            double raw = changed ? Math.min(1.08 * (impact + exploit), 10) : Math.min(impact + exploit, 10);
            return Math.ceil(raw * 10) / 10.0;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static double cia(String v) {
        return switch (v) { case "H" -> 0.56; case "L" -> 0.22; default -> 0.0; };
    }
}
