package com.sentinel.audit.settings;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entrega à API, na subida, os valores definidos na tela (tabela {@code app_setting}). Tem prioridade sobre as
 * variáveis de ambiente (ordinal 450 contra 300), que passam a ser só o valor inicial. Roda antes do CDI e do
 * Hibernate, então lê o banco direto por JDBC com as mesmas variáveis de conexão do application.properties.
 * Sem banco, sem tabela ou com {@code SENTINEL_SETTINGS_FROM_DB=false}, não entrega nada e a API sobe como antes.
 */
public class DbConfigSource implements ConfigSource {

    private static final Logger LOG = Logger.getLogger(DbConfigSource.class.getName());
    static final int ORDINAL = 450;
    private static final int LOGIN_TIMEOUT_SECONDS = 3;
    private static final String POSTGRES_DRIVER = "org.postgresql.Driver";

    private volatile Map<String, String> cache;

    @Override
    public Map<String, String> getProperties() {
        return load();
    }

    @Override
    public Set<String> getPropertyNames() {
        return load().keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return load().get(propertyName);
    }

    @Override
    public String getName() {
        return "sentinel-settings-db";
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    private Map<String, String> load() {
        Map<String, String> c = cache;
        if (c == null) {
            synchronized (this) {
                c = cache;
                if (c == null) {
                    c = Collections.unmodifiableMap(read());
                    cache = c;
                }
            }
        }
        return c;
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v;
    }

    private static Map<String, String> read() {
        Map<String, String> out = new HashMap<>();
        if ("false".equalsIgnoreCase(env("SENTINEL_SETTINGS_FROM_DB", "true"))
                || "false".equalsIgnoreCase(System.getProperty("sentinel.settings.from-db", "true"))) {
            return out;
        }
        String url = env("DB_URL", "jdbc:postgresql://127.0.0.1:5432/sentinel_audit");
        String user = env("DB_USER", "sentinel_audit");
        String password = env("DB_PASSWORD", "sentinel_audit");
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        try {
            // No modo fast-jar o driver fica fora do classpath do sistema e o DriverManager não o descobre sozinho.
            Class.forName(POSTGRES_DRIVER, true, DbConfigSource.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            LOG.log(Level.FINE, "Driver do PostgreSQL ausente: {0}", e.toString());
            return out;
        }
        try (Connection c = DriverManager.getConnection(url, user, password);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select setting_key, setting_value from app_setting")) {
            while (rs.next()) {
                String value = rs.getString(2);
                SettingsCatalog.find(rs.getString(1)).filter(SettingDef::editable).ifPresent(d -> put(out, d, value));
            }
        } catch (Exception e) {
            // Primeira subida (tabela ainda não existe) ou banco fora do ar: a API segue com o ambiente.
            LOG.log(Level.FINE, "Configurações da tela não carregadas: {0}", e.toString());
        }
        return out;
    }

    private static void put(Map<String, String> out, SettingDef d, String value) {
        if (value != null && SettingsCatalog.validate(d, value) == null) {
            out.put(d.property(), value.trim());
        }
    }
}
