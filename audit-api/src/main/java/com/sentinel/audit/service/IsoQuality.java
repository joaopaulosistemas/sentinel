package com.sentinel.audit.service;

import com.sentinel.audit.dto.IsoQualityDto;
import com.sentinel.audit.iso.Iso25010;
import com.sentinel.audit.iso.Iso25010Perfil;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nota por característica da ISO/IEC 25010, calculada com a mesma regra de penalidade das notas por dimensão
 * ({@link ScoringService#scoreOf}): só muda o agrupamento dos achados.
 */
public final class IsoQuality {

    private static final double MAX_SCORE = 100.0;
    private static final double ROUND = 10.0;

    private IsoQuality() {}

    /** Calcula sem metas. */
    public static IsoQualityDto calculate(ScoringService scoring, List<Finding> active, Long linesOfCode, Iso25010Perfil perfil) {
        return calculate(scoring, active, linesOfCode, perfil, Map.of());
    }

    /**
     * @param active      achados que contam para a nota (sem suprimidos nem em observação)
     * @param linesOfCode linhas de código analisadas; nulo = sem ajuste por tamanho
     * @param metas       nota mínima por característica; vazio = sem metas
     */
    public static IsoQualityDto calculate(ScoringService scoring, List<Finding> active, Long linesOfCode,
                                          Iso25010Perfil perfil, Map<Iso25010, Double> metas) {
        double factor = scoring.sizeFactor(linesOfCode);
        Map<Iso25010, List<Finding>> grouped = new EnumMap<>(Iso25010.class);
        for (Finding f : active) {
            grouped.computeIfAbsent(Iso25010.of(f), k -> new ArrayList<>()).add(f);
        }
        List<IsoQualityDto.Caracteristica> list = new ArrayList<>();
        double weighted = 0;
        boolean allMet = true;
        Map<String, Double> gap = new LinkedHashMap<>();
        for (Iso25010 c : Iso25010.values()) {
            List<Finding> subset = grouped.getOrDefault(c, List.of());
            Double meta = metas.get(c);
            if (!c.avaliada()) {
                list.add(new IsoQualityDto.Caracteristica(c.id(), c.nome(), false, null, 0, Map.of(), 0, null, null));
                continue;
            }
            double nota = round(scoring.scoreOf(subset, factor));
            Boolean atingiu = meta == null ? null : nota >= meta;
            allMet &= atingiu == null || atingiu;
            double peso = perfil.peso(c);
            weighted += nota * peso;
            gap.put(c.id(), peso * (MAX_SCORE - nota));
            list.add(new IsoQualityDto.Caracteristica(c.id(), c.nome(), true, nota, subset.size(), bySeverity(subset),
                    peso, meta, atingiu));
        }
        List<String> prioridade = gap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey).toList();
        return new IsoQualityDto(perfil.id(), round(weighted), list, prioridade, metas.isEmpty() ? null : allMet);
    }

    /**
     * Lê metas no formato {@code security:90,maintainability:75} (aceita {@code =} no lugar de {@code :}).
     * Erro 400 para id desconhecido, característica não avaliada, valor fora de 0 a 100 ou texto malformado.
     */
    public static Map<Iso25010, Double> parseMetas(String text) {
        Map<Iso25010, Double> metas = new EnumMap<>(Iso25010.class);
        if (text == null || text.isBlank()) {
            return metas;
        }
        for (String part : text.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            Map.Entry<Iso25010, Double> meta = parseMeta(part);
            metas.put(meta.getKey(), meta.getValue());
        }
        return metas;
    }

    /** Uma meta no formato {@code característica:nota}; qualquer defeito vira erro 400 com o trecho que falhou. */
    private static Map.Entry<Iso25010, Double> parseMeta(String part) {
        String[] kv = part.split("[:=]", 2);
        Iso25010 c = Iso25010.byId(kv[0]);
        if (kv.length != 2 || c == null) {
            throw new InvalidInputException("Meta inválida: \"" + part.strip() + "\". Use característica:nota, por exemplo security:90.");
        }
        if (!c.avaliada()) {
            throw new InvalidInputException("O Sentinel não avalia \"" + c.id() + "\"; não dá para pôr meta nela.");
        }
        double value;
        try {
            value = Double.parseDouble(kv[1].strip());
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Nota da meta inválida em \"" + part.strip() + "\".", e);
        }
        if (value < 0 || value > MAX_SCORE) {
            throw new InvalidInputException("A nota da meta deve estar entre 0 e 100 (\"" + part.strip() + "\").");
        }
        return Map.entry(c, value);
    }

    private static Map<Severity, Long> bySeverity(List<Finding> list) {
        Map<Severity, Long> m = new EnumMap<>(Severity.class);
        for (Finding f : list) {
            m.merge(f.getSeverity(), 1L, Long::sum);
        }
        return m;
    }

    private static double round(double v) {
        return Math.round(v * ROUND) / ROUND;
    }
}
