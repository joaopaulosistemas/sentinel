package com.sentinel.audit.insights;

/**
 * Áreas de uma auditoria completa. Cada finding pertence a exatamente uma área.
 */
public enum AuditArea {
    ARCHITECTURE("Arquitetura"),
    CODE_QUALITY("Qualidade de código"),
    SECURITY("Segurança"),
    TESTING("Testes"),
    PERFORMANCE("Performance e resiliência"),
    FRONTEND("Frontend, UX e acessibilidade"),
    DEVOPS("CI/CD e infraestrutura"),
    DOCUMENTATION("Documentação e git"),
    DEPENDENCIES("Dependências"),
    OBSERVABILITY("Logging e observabilidade"),
    MAINTAINABILITY("Manutenibilidade");

    private final String label;

    AuditArea(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
