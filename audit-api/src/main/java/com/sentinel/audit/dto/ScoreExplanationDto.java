package com.sentinel.audit.dto;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Severity;

import java.util.List;

/**
 * De onde vem a nota: por dimensão, quanto cada regra tirou e onde o teto por regra cortou.
 *
 * @param curve         curva da nota em uso ({@code linear} ou {@code suave})
 * @param sizeFactor    fator por tamanho do projeto aplicado às penalidades que não são CRITICAL (1,0 = sem ajuste)
 * @param linesOfCode   linhas de código analisadas (nulo = desconhecido)
 * @param overallBefore nota geral com todos os achados que contam
 * @param overall       nota geral desta explicação (igual à anterior, ou a simulada quando há {@code removedRules})
 * @param removedRules  regras retiradas na simulação "e se"; vazio quando não há simulação
 * @param dimensions    seis dimensões com as regras que mais pesam em cada uma
 */
public record ScoreExplanationDto(String curve, double sizeFactor, Long linesOfCode, double overallBefore, double overall,
                                  List<String> removedRules, List<Dimension> dimensions) {

    /**
     * @param weight  peso da dimensão na nota geral
     * @param score   nota da dimensão (0 a 100)
     * @param penalty penalidade total antes da curva (CRITICAL inteiro + demais já com teto e fator de tamanho)
     * @param hidden  quantas regras menores ficaram fora da lista {@code groups}
     */
    public record Dimension(Category category, double weight, double score, double penalty, int hidden, List<Group> groups) {}

    /**
     * @param raw     penalidade somada de todas as ocorrências da regra nesta severidade
     * @param counted penalidade que de fato entra (depois do teto por regra); antes do fator de tamanho
     * @param capped  {@code true} quando o teto cortou: corrigir só parte das ocorrências não muda a nota
     */
    public record Group(String ruleId, Severity severity, int count, double raw, double counted, boolean capped) {}
}
