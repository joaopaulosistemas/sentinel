package com.sentinel.audit.mcp;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class McpCallLogRepository implements PanacheRepository<McpCallLog> {

    /** As últimas chamadas do sistema, da mais nova para a mais antiga. */
    public List<McpCallLog> recent(Long systemId, int limit) {
        return find("systemId = ?1 ORDER BY id DESC", systemId).page(0, limit).list();
    }

    public long deleteBySystemId(Long systemId) {
        return delete("systemId", systemId);
    }
}
