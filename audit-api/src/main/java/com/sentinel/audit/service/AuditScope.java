package com.sentinel.audit.service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** O escopo de uma auditoria: quais lados (back-end, front-end) estavam desligados quando ela começou. */
public final class AuditScope {

    private AuditScope() { }

    /** Texto gravado em {@code audit_run.disabled_sides}: lados ordenados e separados por vírgula; vazio se nenhum. */
    public static String encode(Set<String> disabledSides) {
        return disabledSides == null ? "" : String.join(",", new TreeSet<>(disabledSides));
    }

    /** Lista ordenada e nunca nula; texto nulo (auditoria antiga, escopo desconhecido) e vazio dão lista vazia. */
    public static List<String> decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(",")).map(String::strip).filter(s -> !s.isEmpty()).sorted().toList();
    }

    /** Selo ao lado da nota: "front-end desligado", "back-end desligado" (ou os dois, separados por " e "); vazio se nenhum. */
    public static String badge(List<String> disabledSides) {
        if (disabledSides == null || disabledSides.isEmpty()) {
            return "";
        }
        return String.join(" e ", disabledSides.stream().map(AuditScope::sideName).toList()) + " desligado";
    }

    private static String sideName(String side) {
        return "front".equals(side) ? "front-end" : "back".equals(side) ? "back-end" : side;
    }
}
