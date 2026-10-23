package com.sentinel.audit.tuning;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SystemEngineConfigRepository implements PanacheRepository<SystemEngineConfig> {

    public SystemEngineConfig findBySystemId(Long systemId) {
        return find("systemId", systemId).firstResult();
    }
}
