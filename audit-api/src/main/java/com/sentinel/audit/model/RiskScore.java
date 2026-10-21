package com.sentinel.audit.model;

import jakarta.persistence.Embeddable;

/**
 * Objeto de valor incorporável que representa o score de risco
 * calculado em cada dimensão de auditoria.
 */
@Embeddable
public class RiskScore {

    /** Score geral ponderado entre todas as dimensões (0–100). */
    private Double overall;

    /** Score da dimensão de segurança (0–100). */
    private Double security;

    /** Score da dimensão de qualidade de código (0–100). */
    private Double quality;

    /** Score da dimensão de arquitetura (0–100). */
    private Double architecture;

    /** Score da dimensão de dependências (0–100). */
    private Double dependencies;

    /** Score da dimensão de observabilidade (0–100). */
    private Double observability;

    public RiskScore() {
    }

    public RiskScore(Double overall, Double security, Double quality,
                     Double architecture, Double dependencies, Double observability) {
        this.overall = overall;
        this.security = security;
        this.quality = quality;
        this.architecture = architecture;
        this.dependencies = dependencies;
        this.observability = observability;
    }

    public Double getOverall() {
        return overall;
    }

    public void setOverall(Double overall) {
        this.overall = overall;
    }

    public Double getSecurity() {
        return security;
    }

    public void setSecurity(Double security) {
        this.security = security;
    }

    public Double getQuality() {
        return quality;
    }

    public void setQuality(Double quality) {
        this.quality = quality;
    }

    public Double getArchitecture() {
        return architecture;
    }

    public void setArchitecture(Double architecture) {
        this.architecture = architecture;
    }

    public Double getDependencies() {
        return dependencies;
    }

    public void setDependencies(Double dependencies) {
        this.dependencies = dependencies;
    }

    public Double getObservability() {
        return observability;
    }

    public void setObservability(Double observability) {
        this.observability = observability;
    }
}
