package com.sentinel.audit.mcp;

/**
 * Quanto do resultado de um sistema o Claude (ou outro cliente MCP) pode ver. É escolhido sistema por sistema;
 * cada sistema começa em {@link #TITLES}.
 */
public enum DataLevel {
    /** Só números e títulos: nenhum caminho de arquivo, nenhuma linha, nenhum trecho de código. */
    TITLES,
    /** Acrescenta o arquivo e a linha de cada achado. O trecho de código nunca sai em nenhum nível. */
    FILE_LINE,
    /** Só números: contagens por regra e severidade, sem títulos nem locais. Para sistemas sensíveis. */
    SENSITIVE;

    public static DataLevel parse(String raw, DataLevel fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
