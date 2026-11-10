package com.sentinel.audit.service;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Regra de retenção: quais auditorias de um sistema já podem ser apagadas. */
final class AuditRetention {

    private AuditRetention() {
    }

    /**
     * @param runs         auditorias de um sistema, em qualquer ordem
     * @param keepLatest   quantas das mais recentes ficam sempre, mesmo velhas (preserva a base de comparação e a tendência)
     * @param maxAgeDays   idade máxima em dias; zero ou negativo desliga a retenção
     * @return as encerradas (concluídas, falhas ou canceladas) que passaram da idade e não estão entre as preservadas
     */
    static List<AuditRun> expired(List<AuditRun> runs, int keepLatest, int maxAgeDays, LocalDateTime now) {
        if (maxAgeDays <= 0) {
            return List.of();
        }
        LocalDateTime limit = now.minusDays(maxAgeDays);
        List<AuditRun> finished = new ArrayList<>(runs.stream()
                .filter(r -> r.getStatus() != AuditStatus.PENDING && r.getStatus() != AuditStatus.RUNNING)
                .sorted(Comparator.comparing(AuditRun::getStartedAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList());
        List<AuditRun> candidates = finished.subList(Math.min(Math.max(keepLatest, 1), finished.size()), finished.size());
        return candidates.stream().filter(r -> r.getStartedAt() != null && r.getStartedAt().isBefore(limit)).toList();
    }
}
