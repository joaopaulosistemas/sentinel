package com.sentinel.audit.service;

import com.sentinel.audit.pack.AuditPolicy;
import com.sentinel.audit.pack.PackRegistry;
import com.sentinel.audit.pack.SystemTuning;
import com.sentinel.audit.review.LocalRuleMaterializer;
import com.sentinel.audit.tuning.SystemTuningService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Monta a política de uma auditoria: perfil do sistema, ajustes do próprio sistema por cima e, se houver,
 * as regras locais. Falha ao ler os ajustes vale como "sem ajustes", para nunca derrubar a auditoria.
 */
@ApplicationScoped
public class AuditPolicyResolver {

    private static final Logger LOG = Logger.getLogger(AuditPolicyResolver.class);

    @Inject
    PackRegistry packRegistry;

    @Inject
    SystemTuningService tuningService;

    @Inject
    LocalRuleMaterializer localRules;

    /** Política completa da auditoria; a pasta das regras locais deve ser liberada com {@link #cleanup}. */
    AuditPolicy forAudit(String profile, Long systemId) {
        AuditPolicy policy = resolve(profile, loadTuning(systemId));
        if (localRules != null) {
            policy = localRules.apply(policy, systemId);
        }
        return policy;
    }

    /** Apaga o que {@link #forAudit} criou em disco; aceita política nula (falha antes de montá-la). */
    void cleanup(AuditPolicy policy) {
        if (localRules != null) {
            localRules.cleanup(policy);
        }
    }

    /** Perfil do sistema com os ajustes do próprio sistema por cima; sem ajustes é exatamente o perfil. */
    AuditPolicy resolve(String profile, SystemTuning tuning) {
        AuditPolicy base;
        try {
            base = packRegistry.policyFor(profile);
        } catch (IllegalArgumentException e) {
            LOG.warnf("%s; usando o perfil padrão", e.getMessage());
            base = packRegistry.policyFor(null);
        }
        return base.withTuning(tuning, packRegistry.snapshot().packs());
    }

    private SystemTuning loadTuning(Long systemId) {
        if (tuningService == null || systemId == null) {
            return SystemTuning.EMPTY;
        }
        try {
            return tuningService.load(systemId);
        } catch (RuntimeException e) {
            LOG.warnf("Não foi possível ler os ajustes do sistema %d: %s", systemId, e.getMessage());
            return SystemTuning.EMPTY;
        }
    }
}
