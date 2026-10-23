package com.sentinel.audit.mcp;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class McpAccessRepository implements PanacheRepository<McpAccess> {

    public McpAccess findBySystemId(Long systemId) {
        return find("systemId", systemId).firstResult();
    }
}
