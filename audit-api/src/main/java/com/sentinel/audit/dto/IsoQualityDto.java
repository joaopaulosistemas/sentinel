package com.sentinel.audit.dto;

import com.sentinel.audit.model.Severity;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** Qualidade do sistema segundo as características da ISO/IEC 25010 (alinhamento de vocabulário, não certificação). */
public record IsoQualityDto(
        @Schema(description = "Perfil de pesos usado: padrao, financeiro ou interno.") String perfil,
        @Schema(description = "Nota ponderada pelo perfil, só com as características avaliadas (0-100).") Double notaPonderada,
        @Schema(description = "Características, avaliadas e não avaliadas.") List<Caracteristica> caracteristicas,
        @Schema(description = "Ids das avaliadas, da que mais merece atenção para a que menos (peso x o que falta para 100).") List<String> prioridade,
        @Schema(description = "Verdadeiro se todas as metas pedidas foram atingidas; nulo quando não há metas.") Boolean metasAtingidas) {

    /** Uma característica. {@code nota}, {@code peso} e {@code porSeveridade} são nulos/zerados quando não avaliada. */
    public record Caracteristica(
            @Schema(description = "Identificador (security, maintainability...).") String id,
            @Schema(description = "Nome em português.") String nome,
            @Schema(description = "Falso quando o Sentinel não tem regras para esta característica.") boolean avaliada,
            @Schema(description = "Nota 0-100, nula se não avaliada.") Double nota,
            @Schema(description = "Quantidade de achados ativos.") long achados,
            @Schema(description = "Achados por severidade.") Map<Severity, Long> porSeveridade,
            @Schema(description = "Peso no perfil.") double peso,
            @Schema(description = "Meta pedida, se houver.") Double meta,
            @Schema(description = "Se a nota atingiu a meta; nulo sem meta.") Boolean atingiu) {}
}
