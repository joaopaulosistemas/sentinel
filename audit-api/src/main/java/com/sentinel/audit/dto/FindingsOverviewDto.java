package com.sentinel.audit.dto;

import java.util.List;

/**
 * Achados de todos os sistemas juntos (última auditoria concluída de cada um), já filtrados e paginados.
 *
 * @param total        quantidade de achados que atendem aos filtros (antes da paginação)
 * @param bySystem     quantidade por sistema, para o resumo no topo da tela
 */
public record FindingsOverviewDto(long total, List<SystemCount> bySystem, List<Item> items) {

    /** @param stack stack do sistema; critical/high contam entre os achados que passam pelos filtros; auditedAt é a data da auditoria usada */
    public record SystemCount(Long systemId, String systemName, Long auditId, long count,
                              String stack, long critical, long high, java.time.LocalDateTime auditedAt) {
        public SystemCount(Long systemId, String systemName, Long auditId, long count) {
            this(systemId, systemName, auditId, count, null, 0, 0, null);
        }
    }

    public record Item(Long systemId, String systemName, Long auditId, FindingResponse finding) {}
}
