package com.sentinel.audit.resource;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Ajustes de esquema que o modo "update" do Hibernate não faz sozinho.
 * <p>
 * Bancos criados antes do status CANCELLED têm uma restrição CHECK que só aceita os valores antigos de
 * {@code audit_run.status}. Ela é removida (se existir) para que o novo valor possa ser gravado. O ajuste é
 * idempotente e, se falhar, só registra um aviso: não impede a aplicação de subir.
 */
@ApplicationScoped
public class SchemaFixups {

    private static final Logger LOG = Logger.getLogger(SchemaFixups.class);

    @Inject
    EntityManager em;

    void onStart(@Observes StartupEvent event) {
        try {
            dropStatusCheck();
        } catch (RuntimeException e) {
            LOG.warnf("Não foi possível ajustar a restrição de status de audit_run (%s); "
                    + "se o cancelamento falhar, execute: ALTER TABLE audit_run DROP CONSTRAINT audit_run_status_check", e.getMessage());
        }
    }

    @Transactional
    void dropStatusCheck() {
        em.createNativeQuery("ALTER TABLE IF EXISTS audit_run DROP CONSTRAINT IF EXISTS audit_run_status_check").executeUpdate();
    }
}
