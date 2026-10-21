package com.sentinel.audit.repository;

import com.sentinel.audit.model.AuditedSystem;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositório Panache para a entidade {@link AuditedSystem}.
 */
@ApplicationScoped
public class AuditedSystemRepository implements PanacheRepository<AuditedSystem> {
}
