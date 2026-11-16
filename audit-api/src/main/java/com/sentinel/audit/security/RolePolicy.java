package com.sentinel.audit.security;

import java.util.Locale;
import java.util.Set;

/**
 * Quem pode o quê quando o login corporativo (OIDC/Keycloak) está ligado. Lógica pura, sem framework, para ser testada
 * sem subir nada. Dois papéis do Keycloak:
 * <ul>
 *   <li>{@code sentinel-admin}: tudo (cadastra sistemas, mexe em regras, pacotes, supressões e configurações);</li>
 *   <li>{@code sentinel-executor}: lê tudo (menos as configurações) e dispara/cancela auditorias.</li>
 * </ul>
 */
public final class RolePolicy {

    public static final String ADMIN = "sentinel-admin";
    public static final String EXECUTOR = "sentinel-executor";

    /** O que a rota exige. */
    public enum Need { PUBLIC, ANY_AUTHENTICATED, READ, EXECUTE, ADMIN }

    private RolePolicy() {
    }

    public static Need need(String method, String path) {
        String p = normalize(path);
        if (!p.startsWith("api/")) {
            return Need.PUBLIC; // fora de /api (MCP, /q/...) a proteção é de outra camada
        }
        if (p.equals("api/v1/health") || p.startsWith("api/v1/health/") || p.startsWith("api/v1/webhooks/")) {
            return Need.PUBLIC;
        }
        if (p.equals("api/v1/me")) {
            return Need.ANY_AUTHENTICATED;
        }
        if (p.equals("api/v1/settings") || p.startsWith("api/v1/settings/")) {
            return Need.ADMIN; // as configurações revelam e mudam o ambiente do servidor
        }
        String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if (m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS")) {
            return Need.READ;
        }
        if (m.equals("POST") && (p.equals("api/v1/audits") || p.startsWith("api/v1/audits/"))) {
            return Need.EXECUTE;
        }
        return Need.ADMIN;
    }

    public static boolean allows(Need need, Set<String> roles) {
        return switch (need) {
            case PUBLIC, ANY_AUTHENTICATED -> true;
            case READ, EXECUTE -> roles.contains(ADMIN) || roles.contains(EXECUTOR);
            case ADMIN -> roles.contains(ADMIN);
        };
    }

    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String p = path.replaceAll("^/+", "").replaceAll("/+$", "");
        int query = p.indexOf('?');
        return query >= 0 ? p.substring(0, query) : p;
    }
}
