package com.sentinel.audit.actions;

import com.sentinel.audit.model.SuppressionReason;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras dos pedidos que o Claude faz e uma pessoa aprova: o que pode pedir, por quanto tempo o pedido vale
 * e o que é recusado de cara. Sem acesso a banco.
 */
public final class PendingActionRules {

    /** Um pedido não aprovado em 1 hora perde a validade. */
    public static final Duration TTL = Duration.ofHours(1);
    public static final int MAX_PENDING_PER_SYSTEM = 5;
    /** Supressão pedida pelo Claude e aprovada vence sozinha: o achado volta se ninguém renovar. */
    public static final int SUPPRESSION_DAYS = 90;
    /** O Claude só pode pedir falso positivo; aceitar risco ou não corrigir é decisão de uma pessoa. */
    public static final Set<SuppressionReason> ALLOWED_FOR_CLAUDE = Collections.unmodifiableSet(EnumSet.of(SuppressionReason.FALSE_POSITIVE));

    private static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9._/-]{1,200}");

    private PendingActionRules() {}

    /** @return problemas encontrados; vazia quando o branch serve */
    public static List<String> checkBranch(String branch) {
        List<String> problems = new ArrayList<>();
        if (branch == null || !BRANCH.matcher(branch).matches()) {
            problems.add("Branch inválido: use letras, números, ponto, hífen, barra e sublinhado.");
        } else if (branch.startsWith("-") || branch.contains("..") || branch.endsWith("/") || branch.endsWith(".lock")) {
            problems.add("Branch inválido.");
        }
        return problems;
    }

    public static List<String> checkSuppression(SuppressionReason reason, String justification) {
        List<String> problems = new ArrayList<>();
        if (reason == null || !ALLOWED_FOR_CLAUDE.contains(reason)) {
            problems.add("O Claude só pode pedir supressão como FALSE_POSITIVE.");
        }
        if (justification == null || justification.strip().length() < 10) {
            problems.add("Explique o motivo em pelo menos 10 caracteres.");
        } else if (justification.strip().length() > 1000) {
            problems.add("A justificativa passa de 1000 caracteres.");
        }
        return problems;
    }

    public static LocalDateTime expiresAt(LocalDateTime createdAt) {
        return createdAt.plus(TTL);
    }

    public static boolean expired(LocalDateTime expiresAt, LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /** Só um pedido ainda pendente e dentro da validade pode ser aprovado. */
    public static String cannotDecide(PendingStatus status, LocalDateTime expiresAt, LocalDateTime now) {
        if (status != PendingStatus.PENDING) {
            return "Este pedido já foi decidido.";
        }
        if (expired(expiresAt, now)) {
            return "Este pedido perdeu a validade. Peça de novo.";
        }
        return null;
    }
}
