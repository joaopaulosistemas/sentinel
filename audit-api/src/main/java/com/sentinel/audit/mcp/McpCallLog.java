package com.sentinel.audit.mcp;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Registro de uma chamada de ferramenta MCP: quando, qual, se deu certo e quanto demorou. Nunca guarda o resultado. */
@Entity
@Table(name = "mcp_call_log", indexes = @Index(name = "idx_mcp_call_system", columnList = "system_id"))
public class McpCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mcp_call_log_seq")
    @SequenceGenerator(name = "mcp_call_log_seq", sequenceName = "mcp_call_log_seq", allocationSize = 1)
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(nullable = false, length = 60)
    private String tool;

    @Column(name = "called_at", nullable = false)
    private LocalDateTime calledAt;

    @Column(nullable = false)
    private boolean ok;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(length = 300)
    private String detail;

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public LocalDateTime getCalledAt() { return calledAt; }
    public void setCalledAt(LocalDateTime calledAt) { this.calledAt = calledAt; }
    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
