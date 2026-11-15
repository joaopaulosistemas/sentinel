package com.sentinel.audit.resource;

import io.quarkus.runtime.LaunchMode;
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
    private static final String AVISO_SEM_CHAVE_PROPRIA =
            "SECRETS_KEY não definida: os tokens salvos são cifrados com a chave derivada da API_KEY; "
                    + "trocar a API_KEY torna esses segredos ilegíveis. Defina uma SECRETS_KEY própria.";

    @ConfigProperty(name = "sentinel.audit.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "sentinel.audit.secrets-key")
    Optional<String> secretsKey;

    @ConfigProperty(name = "quarkus.hibernate-orm.database.generation")
    String schemaMode;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    boolean oidcEnabled;

    @ConfigProperty(name = "sentinel.audit.allow-open-api", defaultValue = "false")
    boolean allowOpenApi;

    /**
     * A API sem autenticação só sobe em produção se alguém pedir de forma explícita (ALLOW_OPEN_API=true).
     * Sem API_KEY e sem OIDC, o padrão é recusar a subida em vez de expor auditorias e segredos.
     */
    static boolean deveRecusarApiAberta(boolean semChave, boolean oidc, boolean permitirAberta, boolean producao) {
        return producao && semChave && !oidc && !permitirAberta;
    }

    void onStart(@Observes StartupEvent event) {
        boolean semChave = apiKey.isEmpty() || apiKey.get().isBlank();
        if (deveRecusarApiAberta(semChave, oidcEnabled, allowOpenApi, LaunchMode.current() == LaunchMode.NORMAL)) {
            throw new IllegalStateException("API_KEY não definida e OIDC desligado: a API ficaria ABERTA. "
                    + "Defina API_KEY (ou use o perfil oidc). Só para desenvolvimento local: ALLOW_OPEN_API=true.");
        }
        if (semChave && !oidcEnabled) {
            LOG.warn("API_KEY não definida: a API está ABERTA, sem autenticação. Use apenas em desenvolvimento local.");
        }
        boolean hasSecretsKey = secretsKey.isPresent() && !secretsKey.get().isBlank();
        if (!hasSecretsKey && apiKey.isPresent() && !apiKey.get().isBlank()) {
            LOG.warn(AVISO_SEM_CHAVE_PROPRIA);
        }
        if (schemaMode.startsWith("drop-and-create") || "create".equals(schemaMode)) {
            LOG.warnf("DB_SCHEMA_MODE=%s: o banco será APAGADO a cada inicialização (histórico de auditorias perdido).", schemaMode);
        }
    }
}
