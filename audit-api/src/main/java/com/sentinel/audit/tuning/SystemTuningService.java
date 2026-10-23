package com.sentinel.audit.tuning;

import com.sentinel.audit.pack.SystemTuning;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/** Guarda e lê os ajustes do motor de cada sistema. */
@ApplicationScoped
public class SystemTuningService {

    private static final Logger LOG = Logger.getLogger(SystemTuningService.class);

    @Inject
    SystemEngineConfigRepository repository;

    /** Ajustes do sistema; ilegíveis ou ausentes valem como "igual ao perfil" (nunca derrubam a auditoria). */
    @Transactional
    public SystemTuning load(Long systemId) {
        SystemEngineConfig row = repository.findBySystemId(systemId);
        if (row == null) {
            return SystemTuning.EMPTY;
        }
        try {
            return TuningJson.read(row.getConfig());
        } catch (IllegalArgumentException e) {
            LOG.warnf("Ajustes do sistema %d ilegíveis; usando o perfil puro", systemId);
            return SystemTuning.EMPTY;
        }
    }

    @Transactional
    public SystemTuning save(Long systemId, SystemTuning tuning) {
        SystemEngineConfig row = repository.findBySystemId(systemId);
        if (tuning.isEmpty()) {
            if (row != null) {
                repository.delete(row);
            }
            return SystemTuning.EMPTY;
        }
        if (row == null) {
            row = new SystemEngineConfig();
            row.setSystemId(systemId);
        }
        row.setConfig(TuningJson.write(tuning));
        repository.persist(row);
        return tuning;
    }

    @Transactional
    public void purge(Long systemId) {
        SystemEngineConfig row = repository.findBySystemId(systemId);
        if (row != null) {
            repository.delete(row);
        }
    }
}
