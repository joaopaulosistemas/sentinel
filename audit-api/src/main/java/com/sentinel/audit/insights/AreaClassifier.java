package com.sentinel.audit.insights;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mapeia um finding para uma {@link AuditArea}. Determinístico: primeiro prefixos de
 * ruleId (mais específicos antes), depois a categoria do finding.
 */
public final class AreaClassifier {

    private static final Map<String, AuditArea> PREFIXES = new LinkedHashMap<>();

    static {
        PREFIXES.put("sentinel-proj-no-tests", AuditArea.TESTING);
        PREFIXES.put("sentinel-proj-no-ci", AuditArea.DEVOPS);
        PREFIXES.put("sentinel-proj-docker", AuditArea.DEVOPS);
        PREFIXES.put("sentinel-proj-no-readme", AuditArea.DOCUMENTATION);
        PREFIXES.put("sentinel-proj-readme", AuditArea.DOCUMENTATION);
        PREFIXES.put("sentinel-proj-no-gitignore", AuditArea.DOCUMENTATION);
        PREFIXES.put("sentinel-proj-committed-env", AuditArea.SECURITY);
        PREFIXES.put("sentinel-proj-committed-key", AuditArea.SECURITY);
        PREFIXES.put("sentinel-proj-committed-artifacts", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-proj-no-lockfile", AuditArea.DEPENDENCIES);
        PREFIXES.put("sentinel-proj", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-ng-bypass", AuditArea.SECURITY);
        PREFIXES.put("sentinel-ng-nativeelement", AuditArea.SECURITY);
        PREFIXES.put("sentinel-react-dangerously", AuditArea.SECURITY);
        PREFIXES.put("sentinel-html-script-", AuditArea.SECURITY);
        PREFIXES.put("sentinel-html-handler", AuditArea.SECURITY);
        PREFIXES.put("sentinel-spring-fetch-eager", AuditArea.PERFORMANCE);
        PREFIXES.put("sentinel-jpa-", AuditArea.PERFORMANCE);
        PREFIXES.put("sentinel-sql-delete", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-sql-update", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-sql-", AuditArea.PERFORMANCE);
        PREFIXES.put("sentinel-mem-", AuditArea.PERFORMANCE);
        PREFIXES.put("sentinel-camadas-", AuditArea.ARCHITECTURE);
        PREFIXES.put("sentinel-arq-", AuditArea.ARCHITECTURE);
        PREFIXES.put("sentinel-dup-", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-hotspot", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-morto-", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-taint-", AuditArea.SECURITY);
        PREFIXES.put("sentinel-spring-open-in-view", AuditArea.PERFORMANCE);
        PREFIXES.put("sentinel-ng-ngfor", AuditArea.PERFORMANCE);
        PREFIXES.put("sentinel-ng-chamada", AuditArea.PERFORMANCE);
        PREFIXES.put("sentinel-ng-", AuditArea.FRONTEND);
        PREFIXES.put("sentinel-react-", AuditArea.FRONTEND);
        PREFIXES.put("sentinel-css-", AuditArea.FRONTEND);
        PREFIXES.put("sentinel-scss-", AuditArea.FRONTEND);
        PREFIXES.put("sentinel-html-", AuditArea.FRONTEND);
        PREFIXES.put("stylelint-", AuditArea.FRONTEND);
        PREFIXES.put("@angular-eslint/", AuditArea.FRONTEND);
        PREFIXES.put("eslint-@angular-eslint/", AuditArea.FRONTEND);
        PREFIXES.put("eslint-jsx-a11y/", AuditArea.FRONTEND);
        PREFIXES.put("eslint-@html-eslint/", AuditArea.FRONTEND);
        PREFIXES.put("eslint-react", AuditArea.FRONTEND);
        PREFIXES.put("sentinel-fe-", AuditArea.FRONTEND);
        PREFIXES.put("sentinel-perf-", AuditArea.PERFORMANCE);
        PREFIXES.put("sentinel-int-", AuditArea.PERFORMANCE);
        PREFIXES.put("hadolint-", AuditArea.DEVOPS);
        PREFIXES.put("checkov-", AuditArea.DEVOPS);
        PREFIXES.put("kube-linter-", AuditArea.DEVOPS);
        PREFIXES.put("kube-score-", AuditArea.DEVOPS);
        PREFIXES.put("jscpd-", AuditArea.MAINTAINABILITY);
        PREFIXES.put("checkstyle-", AuditArea.CODE_QUALITY);
        PREFIXES.put("squawk-", AuditArea.PERFORMANCE);
        PREFIXES.put("sqlfluff-", AuditArea.CODE_QUALITY);
        PREFIXES.put("dependency-cruiser-", AuditArea.ARCHITECTURE);
        PREFIXES.put("sentinel-docker-", AuditArea.DEVOPS);
        PREFIXES.put("sentinel-compose-", AuditArea.DEVOPS);
        PREFIXES.put("sentinel-k8s-", AuditArea.DEVOPS);
        PREFIXES.put("sentinel-dockerfile", AuditArea.DEVOPS);
        PREFIXES.put("sentinel-tf-", AuditArea.DEVOPS);
        PREFIXES.put("sentinel-arch-", AuditArea.ARCHITECTURE);
        PREFIXES.put("sentinel-comp-", AuditArea.ARCHITECTURE);
        PREFIXES.put("sentinel-god-object", AuditArea.ARCHITECTURE);
        PREFIXES.put("sentinel-too-many-injections", AuditArea.ARCHITECTURE);
        PREFIXES.put("sentinel-cc-", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-codigo-comentado", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-comentario-pendencia", AuditArea.MAINTAINABILITY);
        PREFIXES.put("sentinel-java-log-", AuditArea.OBSERVABILITY);
        PREFIXES.put("sentinel-ts-console-log", AuditArea.OBSERVABILITY);
        PREFIXES.put("sentinel-java-thread-manual", AuditArea.PERFORMANCE);
        PREFIXES.put("sentinel-java-", AuditArea.CODE_QUALITY);
        PREFIXES.put("sentinel-ts-", AuditArea.CODE_QUALITY);
        PREFIXES.put("sentinel-api-", AuditArea.SECURITY);
        PREFIXES.put("sentinel-xss", AuditArea.SECURITY);
        PREFIXES.put("sentinel-crypto", AuditArea.SECURITY);
        PREFIXES.put("sentinel-sql", AuditArea.SECURITY);
        PREFIXES.put("sentinel-owasp", AuditArea.SECURITY);
    }

    private AreaClassifier() {
    }

    /** A área do primeiro prefixo conhecido do id da regra; nulo se o id for nulo ou não casar. */
    private static AuditArea byPrefix(String ruleId) {
        if (ruleId == null) {
            return null;
        }
        String id = ruleId.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, AuditArea> e : PREFIXES.entrySet()) {
            if (id.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    public static AuditArea classify(Finding f) {
        return classify(f.getRuleId(), f.getCategory());
    }

    public static AuditArea classify(String ruleId, Category category) {
        AuditArea byRule = byPrefix(ruleId);
        if (byRule != null) {
            return byRule;
        }
        if (category == null) {
            return AuditArea.MAINTAINABILITY;
        }
        return switch (category) {
            case SECURITY -> AuditArea.SECURITY;
            case QUALITY -> AuditArea.CODE_QUALITY;
            case ARCHITECTURE -> AuditArea.ARCHITECTURE;
            case DEPENDENCY -> AuditArea.DEPENDENCIES;
            case OBSERVABILITY -> AuditArea.OBSERVABILITY;
            case PERFORMANCE -> AuditArea.PERFORMANCE;
        };
    }
}
