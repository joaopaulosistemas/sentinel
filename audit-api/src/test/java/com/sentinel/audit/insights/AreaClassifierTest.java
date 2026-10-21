package com.sentinel.audit.insights;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sentinel.audit.model.Category;
import org.junit.jupiter.api.Test;

class AreaClassifierTest {

    @Test
    void prefixosEspecificosGanham() {
        assertEquals(AuditArea.TESTING, AreaClassifier.classify("sentinel-proj-no-tests", Category.QUALITY));
        assertEquals(AuditArea.DEVOPS, AreaClassifier.classify("sentinel-proj-no-ci", Category.QUALITY));
        assertEquals(AuditArea.SECURITY, AreaClassifier.classify("sentinel-proj-committed-env", Category.QUALITY));
        assertEquals(AuditArea.OBSERVABILITY, AreaClassifier.classify("sentinel-java-log-concatenado", Category.QUALITY));
        assertEquals(AuditArea.CODE_QUALITY, AreaClassifier.classify("sentinel-java-catch-vazio", Category.QUALITY));
        assertEquals(AuditArea.PERFORMANCE, AreaClassifier.classify("sentinel-int-fetch-sem-timeout", Category.QUALITY));
        assertEquals(AuditArea.FRONTEND, AreaClassifier.classify("sentinel-fe-img-sem-alt", Category.QUALITY));
        assertEquals(AuditArea.ARCHITECTURE, AreaClassifier.classify("sentinel-arch-layer-a-b", Category.QUALITY));
        assertEquals(AuditArea.DEVOPS, AreaClassifier.classify("sentinel-dockerfile-user", Category.SECURITY));
        assertEquals(AuditArea.MAINTAINABILITY, AreaClassifier.classify("sentinel-cc-complexidade", Category.QUALITY));
        assertEquals(AuditArea.DEVOPS, AreaClassifier.classify("hadolint-DL3007", Category.QUALITY));
        assertEquals(AuditArea.DEVOPS, AreaClassifier.classify("checkov-CKV_K8S_8", Category.SECURITY));
        assertEquals(AuditArea.DEVOPS, AreaClassifier.classify("kube-linter-latest-tag", Category.SECURITY));
        assertEquals(AuditArea.DEVOPS, AreaClassifier.classify("kube-score-container-resources", Category.QUALITY));
        assertEquals(AuditArea.MAINTAINABILITY, AreaClassifier.classify("jscpd-codigo-duplicado", Category.QUALITY));
        assertEquals(AuditArea.CODE_QUALITY, AreaClassifier.classify("checkstyle-maxParam", Category.QUALITY));
        assertEquals(AuditArea.FRONTEND, AreaClassifier.classify("stylelint-declaration-no-important", Category.QUALITY));
        assertEquals(AuditArea.FRONTEND, AreaClassifier.classify("eslint-@angular-eslint/template/alt-text", Category.QUALITY));
        assertEquals(AuditArea.FRONTEND, AreaClassifier.classify("sentinel-ng-deep", Category.QUALITY));
        assertEquals(AuditArea.SECURITY, AreaClassifier.classify("sentinel-ng-bypass-security", Category.SECURITY));
        assertEquals(AuditArea.SECURITY, AreaClassifier.classify("sentinel-spring-permitall-geral", Category.SECURITY));
        assertEquals(AuditArea.PERFORMANCE, AreaClassifier.classify("sentinel-spring-fetch-eager", Category.QUALITY));
        assertEquals(AuditArea.SECURITY, AreaClassifier.classify("bandit-B602", Category.SECURITY));
    }

    @Test
    void semPrefixoUsaCategoria() {
        assertEquals(AuditArea.DEPENDENCIES, AreaClassifier.classify("CVE-2024-1", Category.DEPENDENCY));
        assertEquals(AuditArea.SECURITY, AreaClassifier.classify("outra", Category.SECURITY));
        assertEquals(AuditArea.OBSERVABILITY, AreaClassifier.classify(null, Category.OBSERVABILITY));
        assertEquals(AuditArea.MAINTAINABILITY, AreaClassifier.classify(null, null));
    }
}
