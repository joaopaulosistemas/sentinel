package com.sentinel.audit.mcp;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Acesso MCP de um sistema: se está ligado, a chave (só o hash), quanto do resultado pode sair e quais ferramentas
 * estão liberadas. Um sistema sem linha aqui não aceita nenhuma chamada MCP.
 */
@Entity
@Table(name = "mcp_access", uniqueConstraints = @UniqueConstraint(name = "uk_mcp_access_system", columnNames = "system_id"))
public class McpAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mcp_access_seq")
    @SequenceGenerator(name = "mcp_access_seq", sequenceName = "mcp_access_seq", allocationSize = 1)
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(nullable = false)
    private boolean enabled;

    /** SHA-256 da chave, em hexadecimal. A chave em si nunca é guardada. */
    @Column(name = "key_hash", length = 64)
    private String keyHash;

    /** Começo da chave, só para a pessoa reconhecer qual é. */
    @Column(name = "key_prefix", length = 16)
    private String keyPrefix;

    @Column(name = "key_created_at")
    private LocalDateTime keyCreatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_level", nullable = false, length = 20)
    private DataLevel dataLevel = DataLevel.TITLES;

    /** Nomes das ferramentas liberadas, separados por vírgula; vazio = todas as de leitura. */
    @Column(name = "tools", length = 500)
    private String tools;

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
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public LocalDateTime getKeyCreatedAt() { return keyCreatedAt; }
    public void setKeyCreatedAt(LocalDateTime keyCreatedAt) { this.keyCreatedAt = keyCreatedAt; }
    public DataLevel getDataLevel() { return dataLevel; }
    public void setDataLevel(DataLevel dataLevel) { this.dataLevel = dataLevel; }
    public String getTools() { return tools; }
    public void setTools(String tools) { this.tools = tools; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
