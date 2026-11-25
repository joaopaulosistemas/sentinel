package com.sentinel.audit.iso;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;

/**
 * Características de qualidade de produto da ISO/IEC 25010 (versão de 2023). O Sentinel mede quatro delas; as outras
 * cinco aparecem como "não avaliadas" para que a ausência de achado não passe por qualidade.
 *
 * <p>É um alinhamento de vocabulário, não uma certificação nem um atestado de conformidade.
 */
public enum Iso25010 {
    ADEQUACAO_FUNCIONAL("functional-suitability", "Adequação funcional", false),
    EFICIENCIA_DESEMPENHO("performance-efficiency", "Eficiência de desempenho", true),
    COMPATIBILIDADE("compatibility", "Compatibilidade", false),
    CAPACIDADE_INTERACAO("interaction-capability", "Capacidade de interação", false),
    CONFIABILIDADE("reliability", "Confiabilidade", true),
    SEGURANCA("security", "Segurança", true),
    MANUTENIBILIDADE("maintainability", "Manutenibilidade", true),
    FLEXIBILIDADE("flexibility", "Flexibilidade", false),
    SEGURANCA_OPERACIONAL("safety", "Segurança operacional (safety)", false);

    private final String id;
    private final String nome;
    private final boolean avaliada;

    Iso25010(String id, String nome, boolean avaliada) {
        this.id = id;
        this.nome = nome;
        this.avaliada = avaliada;
    }

    /** Identificador estável usado na API, nos relatórios e nas metas (em inglês, como na norma). */
    public String id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    /** Verdadeiro quando o Sentinel tem regras que alimentam esta característica. */
    public boolean avaliada() {
        return avaliada;
    }

    /** Característica pelo identificador da API; {@code null} se não existir. */
    public static Iso25010 byId(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.strip().toLowerCase(java.util.Locale.ROOT);
        for (Iso25010 c : values()) {
            if (c.id.equals(wanted)) {
                return c;
            }
        }
        return null;
    }

    /**
     * A característica que um achado afeta. Pela categoria; dependências vulneráveis (com CVE) contam como segurança e
     * as demais (versão velha, licença) como manutenibilidade.
     */
    public static Iso25010 of(Finding f) {
        Category category = f.getCategory();
        if (category == null) {
            return MANUTENIBILIDADE;
        }
        return switch (category) {
            case SECURITY -> SEGURANCA;
            case QUALITY, ARCHITECTURE -> MANUTENIBILIDADE;
            case DEPENDENCY -> f.getCveId() != null && !f.getCveId().isBlank() ? SEGURANCA : MANUTENIBILIDADE;
            case OBSERVABILITY -> CONFIABILIDADE;
            case PERFORMANCE -> EFICIENCIA_DESEMPENHO;
        };
    }
}
