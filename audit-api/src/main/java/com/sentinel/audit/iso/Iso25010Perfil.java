package com.sentinel.audit.iso;

import com.sentinel.audit.service.InvalidInputException;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pesos das características para a nota ISO ponderada e para a ordem de prioridade. O perfil muda só esse resumo; a nota
 * de cada característica e a nota geral do Sentinel não mudam.
 */
public enum Iso25010Perfil {
    /** Mesmo peso para as quatro características avaliadas. */
    PADRAO(0.25, 0.25, 0.25, 0.25),
    /** Pagamentos e sistemas financeiros: segurança e confiabilidade pesam mais. */
    FINANCEIRO(0.35, 0.30, 0.20, 0.15),
    /** Sistemas internos: o custo de manter pesa mais. */
    INTERNO(0.25, 0.15, 0.40, 0.20);

    private final Map<Iso25010, Double> pesos = new EnumMap<>(Iso25010.class);

    Iso25010Perfil(double seguranca, double confiabilidade, double manutenibilidade, double desempenho) {
        pesos.put(Iso25010.SEGURANCA, seguranca);
        pesos.put(Iso25010.CONFIABILIDADE, confiabilidade);
        pesos.put(Iso25010.MANUTENIBILIDADE, manutenibilidade);
        pesos.put(Iso25010.EFICIENCIA_DESEMPENHO, desempenho);
    }

    /** Peso da característica neste perfil (0 para as não avaliadas). */
    public double peso(Iso25010 c) {
        return pesos.getOrDefault(c, 0.0);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Perfil pelo nome (sem diferenciar maiúsculas); vazio = padrão; desconhecido = erro 400. */
    public static Iso25010Perfil parse(String text) {
        if (text == null || text.isBlank()) {
            return PADRAO;
        }
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Perfil desconhecido: " + text + ". Use padrao, financeiro ou interno.", e);
        }
    }
}
