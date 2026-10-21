package com.sentinel.audit.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

/** Sobe um PostgreSQL real (Testcontainers) e aponta a aplicação para ele. Exige Docker. */
public class PostgresResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        return Map.of(
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword(),
                "sentinel.audit.scheduler.enabled", "false",
                "sentinel.audit.osv.enabled", "false",
                "sentinel.audit.api-key", "");
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}
