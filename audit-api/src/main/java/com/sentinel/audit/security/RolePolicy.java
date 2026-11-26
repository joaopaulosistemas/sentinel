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
        if (!p.startsWith("api/") || isOpenPath(p)) {
            return Need.PUBLIC; // fora de /api (MCP, /q/...) a proteção é de outra camada
        }
        if (p.equals("api/v1/me")) {
            return Need.ANY_AUTHENTICATED;
        }
        if (isUnder(p, "api/v1/settings")) {
            return Need.ADMIN; // as configurações revelam e mudam o ambiente do servidor
        }
        if (isReadMethod(method)) {
            return Need.READ;
        }
        return isExecuteRoute(method, p) ? Need.EXECUTE : Need.ADMIN;
    }

    private static boolean isReadMethod(String method) {
        String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
        return m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS");
    }

    /** Disparar uma auditoria (POST em /audits) é o que o papel de executor pode fazer além de ler. */
    private static boolean isExecuteRoute(String method, String p) {
        return "POST".equalsIgnoreCase(method) && isUnder(p, "api/v1/audits");
    }

    /** Saúde e webhooks não pedem login (webhooks se autenticam por assinatura própria). */
    private static boolean isOpenPath(String p) {
        return isUnder(p, "api/v1/health") || p.startsWith("api/v1/webhooks/");
    }

    /** O caminho é {@code base} ou algo dentro dela. */
    private static boolean isUnder(String p, String base) {
        return p.equals(base) || p.startsWith(base + "/");
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
