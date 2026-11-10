package com.sentinel.audit.insights;

/** Situação de um analisador ao fim da auditoria; o nome é o valor gravado no contexto da auditoria. */
public enum AnalyzerStatus {
    OK(false),
    /** Terminou, mas com tempo esgotado, saída anormal ou erros: o resultado pode estar incompleto. */
    PARCIAL(true),
    /** Lançou exceção: nenhum resultado. */
    FALHOU(true),
    /** Ferramenta ausente no ambiente (já aparece em "não foi possível verificar"). */
    INDISPONIVEL(false),
    /** Outro analisador cobre o mesmo. */
    SUBSTITUIDO(false),
    /** Desligado por ajuste do sistema. */
    DESLIGADO(false),
    /** O analisador precisa de arquivos de evidência que o projeto não tem. */
    SEM_EVIDENCIA(false),
    /** Não se aplica à stack do projeto. */
    NAO_SUPORTADO(false),
    /** Valor não reconhecido: tratado como incompleto por segurança. */
    DESCONHECIDO(true);

    private final boolean incomplete;

    AnalyzerStatus(boolean incomplete) {
        this.incomplete = incomplete;
    }

    /** Verdadeiro quando o analisador pode ter deixado de ver problemas que existem. */
    public boolean incomplete() {
        return incomplete;
    }

    public static AnalyzerStatus parse(String value) {
        if (value == null) {
            return DESCONHECIDO;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return DESCONHECIDO;
        }
    }
}
