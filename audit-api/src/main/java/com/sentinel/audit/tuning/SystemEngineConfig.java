package com.sentinel.audit.tuning;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Ajustes do motor de um sistema (JSON). Sem linha = sistema igual ao perfil. */
@Entity
@Table(name = "system_engine_config", uniqueConstraints = @UniqueConstraint(name = "uk_engine_config_system", columnNames = "system_id"))
public class SystemEngineConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "engine_config_seq")
    @SequenceGenerator(name = "engine_config_seq", sequenceName = "engine_config_seq", allocationSize = 1)
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(name = "config", nullable = false, length = 20000)
    private String config;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
