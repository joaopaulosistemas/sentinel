package com.sentinel.audit.pack;

import com.sentinel.audit.model.Severity;

/**
 * Ajuste de uma regra feito por um perfil.
 *
 * @param severity nova severidade, ou {@code null} para manter a da regra
 * @param disabled a regra não gera findings
 * @param shadow   {@code true} = aparece mas não pesa no score; {@code false} = promove uma regra experimental;
 *                 {@code null} = não mexe
 */
public record RuleOverride(Severity severity, boolean disabled, Boolean shadow) {}
