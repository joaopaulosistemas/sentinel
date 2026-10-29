package com.sentinel.audit.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Endpoint de verificação de saúde da aplicação.
 */
// Acesso: público por desenho (health check).
@PermitAll
@Path("/api/v1/health")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health", description = "Verificação de saúde da API")
public class HealthResource {

    private static final String VERSION = "1.0.0";

    /**
     * Retorna o status de saúde da aplicação, a versão e o timestamp atual.
     */
    @GET
    @Operation(summary = "Health check", description = "Retorna o status de saúde da API")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", VERSION,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
