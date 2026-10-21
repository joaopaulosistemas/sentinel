package com.sentinel.audit.resource;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Avisos de configuração insegura ou destrutiva, emitidos na inicialização.
 */
@ApplicationScoped
public class StartupChecks {

    private static final Logger LOG = Logger.getLogger(StartupChecks.class);

    @ConfigProperty(name = "sentinel.audit.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "quarkus.hibernate-orm.database.generation")
    String schemaMode;

    void onStart(@Observes StartupEvent event) {
        if (apiKey.isEmpty() || apiKey.get().isBlank()) {
            LOG.warn("API_KEY não definida: a API está ABERTA, sem autenticação. Use apenas em desenvolvimento local.");
        }
        if (schemaMode.startsWith("drop-and-create") || "create".equals(schemaMode)) {
            LOG.warnf("DB_SCHEMA_MODE=%s: o banco será APAGADO a cada inicialização (histórico de auditorias perdido).", schemaMode);
        }
    }
}
