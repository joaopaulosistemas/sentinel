package com.sentinel.audit.actions;

import com.sentinel.audit.model.SuppressionReason;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PendingActionRulesTest {

    @Test
    void branchValidoPassaEOpcaoDeLinhaDeComandoNao() {
        assertTrue(PendingActionRules.checkBranch("main").isEmpty());
        assertTrue(PendingActionRules.checkBranch("feature/nova-tela_2").isEmpty());
        assertTrue(PendingActionRules.checkBranch("release-1.2.3").isEmpty());
        assertFalse(PendingActionRules.checkBranch("--upload-pack=x").isEmpty(), "começa com hífen: seria lido como opção do git");
        assertFalse(PendingActionRules.checkBranch("a..b").isEmpty());
        assertFalse(PendingActionRules.checkBranch("main; rm -rf /").isEmpty());
        assertFalse(PendingActionRules.checkBranch("").isEmpty());
        assertFalse(PendingActionRules.checkBranch(null).isEmpty());
        assertFalse(PendingActionRules.checkBranch("x".repeat(201)).isEmpty());
        assertFalse(PendingActionRules.checkBranch("pasta/").isEmpty());
        assertFalse(PendingActionRules.checkBranch("ref.lock").isEmpty());
    }

    @Test
    void claudeSoPodePedirFalsoPositivoComJustificativa() {
        assertTrue(PendingActionRules.checkSuppression(SuppressionReason.FALSE_POSITIVE, "Constante de teste, não é segredo").isEmpty());
        assertFalse(PendingActionRules.checkSuppression(SuppressionReason.ACCEPTED_RISK, "Aceitamos o risco por ora").isEmpty());
        assertFalse(PendingActionRules.checkSuppression(SuppressionReason.WONT_FIX, "Não vamos corrigir isso").isEmpty());
        assertFalse(PendingActionRules.checkSuppression(null, "Constante de teste, não é segredo").isEmpty());
        assertFalse(PendingActionRules.checkSuppression(SuppressionReason.FALSE_POSITIVE, "curto").isEmpty());
        assertFalse(PendingActionRules.checkSuppression(SuppressionReason.FALSE_POSITIVE, null).isEmpty());
        assertFalse(PendingActionRules.checkSuppression(SuppressionReason.FALSE_POSITIVE, "x".repeat(1001)).isEmpty());
    }

    @Test
    void pedidoVenceEmUmaHoraEOVencidoNaoSeAprova() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 24, 10, 0);
        LocalDateTime exp = PendingActionRules.expiresAt(created);
        assertEquals(created.plusHours(1), exp);
        assertNull(PendingActionRules.cannotDecide(PendingStatus.PENDING, exp, created.plusMinutes(59)));
        assertNotNull(PendingActionRules.cannotDecide(PendingStatus.PENDING, exp, created.plusMinutes(60)), "no limite já venceu");
        assertNotNull(PendingActionRules.cannotDecide(PendingStatus.PENDING, exp, created.plusHours(2)));
    }

    @Test
    void pedidoJaDecididoNaoMudaDeNovo() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 10, 0);
        for (PendingStatus s : PendingStatus.values()) {
            boolean podeDecidir = PendingActionRules.cannotDecide(s, now.plusMinutes(5), now) == null;
            assertEquals(s == PendingStatus.PENDING, podeDecidir, s.name());
        }
    }

    @Test
    void limitesDaFase() {
        assertEquals(5, PendingActionRules.MAX_PENDING_PER_SYSTEM);
        assertEquals(90, PendingActionRules.SUPPRESSION_DAYS);
        assertEquals(java.util.Set.of(SuppressionReason.FALSE_POSITIVE), PendingActionRules.ALLOWED_FOR_CLAUDE);
    }
}
