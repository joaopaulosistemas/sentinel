package com.sentinel.audit.dto;

import java.util.List;

/**
 * Achados de todos os sistemas juntos (última auditoria concluída de cada um), já filtrados e paginados.
 *
 * @param total        quantidade de achados que atendem aos filtros (antes da paginação)
 * @param bySystem     quantidade por sistema, para o resumo no topo da tela
 */
public record FindingsOverviewDto(long total, List<SystemCount> bySystem, List<Item> items) {

    public record SystemCount(Long systemId, String systemName, Long auditId, long count) {}

    public record Item(Long systemId, String systemName, Long auditId, FindingResponse finding) {}
}
