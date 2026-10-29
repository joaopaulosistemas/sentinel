package com.sentinel.audit.dto;

/**
 * DTO representando os scores de risco por dimensão.
 */
public class RiskScoreDto {

    private Double overall;
    private Double security;
    private Double quality;
    private Double architecture;
    private Double dependencies;
    private Double observability;
    private Double performance;

    public RiskScoreDto() {
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

    public Double getPerformance() {
        return performance;
    }

    public void setPerformance(Double performance) {
        this.performance = performance;
    }

    public Double getObservability() {
        return observability;
    }

    public void setObservability(Double observability) {
        this.observability = observability;
    }
}
