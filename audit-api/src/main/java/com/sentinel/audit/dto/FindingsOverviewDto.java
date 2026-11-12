package com.sentinel.audit.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

/**
 * Achados de todos os sistemas juntos (última auditoria concluída de cada um), já filtrados e paginados.
 *
 * @param total        quantidade de achados que atendem aos filtros (antes da paginação)
 * @param bySystem     quantidade por sistema, para o resumo no topo da tela
 */
public record FindingsOverviewDto(@Schema(description = "Quantidade de achados que atendem aos filtros, antes da paginação.") long total, @Schema(description = "Quantidade por sistema, para o resumo no topo da tela.") List<SystemCount> bySystem, @Schema(description = "Achados da página pedida.") List<Item> items) {

    /** @param stack stack do sistema; critical/high contam entre os achados que passam pelos filtros; auditedAt é a data da auditoria usada */
    public record SystemCount(@Schema(description = "Sistema.") Long systemId, @Schema(description = "Nome do sistema.") String systemName, @Schema(description = "Auditoria usada (a última concluída).") Long auditId, @Schema(description = "Achados que passam pelos filtros.") long count,
                              @Schema(description = "Tecnologia do sistema.") String stack, @Schema(description = "Achados críticos entre os que passam pelos filtros.") long critical, @Schema(description = "Achados de severidade alta entre os que passam pelos filtros.") long high, @Schema(description = "Data da auditoria usada.") java.time.LocalDateTime auditedAt) {
        public SystemCount(Long systemId, String systemName, Long auditId, long count) {
            this(systemId, systemName, auditId, count, null, 0, 0, null);
        }
    }

    public record Item(@Schema(description = "Sistema do achado.") Long systemId, @Schema(description = "Nome do sistema.") String systemName, @Schema(description = "Auditoria do achado.") Long auditId, @Schema(description = "O achado.") FindingResponse finding) {}
}
