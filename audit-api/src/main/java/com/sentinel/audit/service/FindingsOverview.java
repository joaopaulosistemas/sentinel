package com.sentinel.audit.service;

import com.sentinel.audit.dto.FindingMapper;
import com.sentinel.audit.dto.FindingsOverviewDto;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Junta os achados da última auditoria de cada sistema, aplica os filtros e pagina. */
public final class FindingsOverview {

    public static final int MAX_LIMIT = 200;

    private FindingsOverview() {}

    /** Achados de uma auditoria concluída, já ligados ao sistema dela. */
    public record Source(Long systemId, String systemName, Long auditId, List<Finding> findings,
                         String stack, java.time.LocalDateTime auditedAt) {
        public Source(Long systemId, String systemName, Long auditId, List<Finding> findings) {
            this(systemId, systemName, auditId, findings, null, null);
        }
    }

    /** @param severities vazio ou nulo = qualquer severidade */
    public record Filter(Long systemId, java.util.Set<Severity> severities, Category category, FindingStatus status,
                         boolean onlyNew, boolean includeSuppressed) {
    }

    public static FindingsOverviewDto build(List<Source> sources, Filter filter, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        int safeOffset = Math.max(0, offset);

        List<FindingsOverviewDto.Item> all = new java.util.ArrayList<>();
        Map<Long, FindingsOverviewDto.SystemCount> counts = new LinkedHashMap<>();

        for (Source s : sources) {
            long n = 0;
            long critical = 0;
            long high = 0;
            for (Finding f : s.findings()) {
                if (!matches(f, filter)) {
                    continue;
                }
                n++;
                if (f.getSeverity() == Severity.CRITICAL) critical++;
                if (f.getSeverity() == Severity.HIGH) high++;
                if (filter.systemId() == null || filter.systemId().equals(s.systemId())) {
                    all.add(new FindingsOverviewDto.Item(s.systemId(), s.systemName(), s.auditId(), FindingMapper.toResponse(f)));
                }
            }
            counts.put(s.systemId(), new FindingsOverviewDto.SystemCount(s.systemId(), s.systemName(), s.auditId(), n,
                    s.stack(), critical, high, s.auditedAt()));
        }

        // Os mais graves primeiro; empate resolvido por sistema e local, para a ordem não mudar entre chamadas.
        all.sort(Comparator
                .comparingInt((FindingsOverviewDto.Item i) -> i.finding().getSeverity().ordinal())
                .thenComparing(FindingsOverviewDto.Item::systemName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(i -> i.finding().getFilePath(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(i -> i.finding().getLineNumber(), Comparator.nullsLast(Comparator.naturalOrder())));

        int from = Math.min(safeOffset, all.size());
        int to = Math.min(from + safeLimit, all.size());
        return new FindingsOverviewDto(all.size(), List.copyOf(counts.values()), List.copyOf(all.subList(from, to)));
    }

    /** O resumo por sistema ignora o filtro de sistema (senão todos os outros apareceriam com zero). */
    private static boolean matches(Finding f, Filter filter) {
        return severityMatches(f, filter)
                && (filter.category() == null || f.getCategory() == filter.category())
                && (filter.status() == null || f.getStatus() == filter.status())
                && suppressedMatches(f, filter)
                && (!filter.onlyNew() || Boolean.TRUE.equals(f.getIsNew()));
    }

    private static boolean severityMatches(Finding f, Filter filter) {
        return filter.severities() == null || filter.severities().isEmpty() || filter.severities().contains(f.getSeverity());
    }

    private static boolean suppressedMatches(Finding f, Filter filter) {
        return filter.includeSuppressed() || filter.status() == FindingStatus.SUPPRESSED
                || f.getStatus() != FindingStatus.SUPPRESSED;
    }
}
