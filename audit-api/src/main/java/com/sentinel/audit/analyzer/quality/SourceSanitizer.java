package com.sentinel.audit.analyzer.quality;

/**
 * Apaga comentários e conteúdo de strings/caracteres do código-fonte, trocando-os por espaços.
 * <p>
 * O resultado tem <b>o mesmo tamanho e as mesmas quebras de linha</b> do original, então índices e números de
 * linha continuam valendo. Serve para que chaves, parênteses e palavras como {@code if} dentro de textos ou
 * comentários não confundam a contagem de métricas.
 */
public final class SourceSanitizer {

    private SourceSanitizer() {}

    public static String strip(String src) {
        char[] in = src.toCharArray();
        char[] out = in.clone();
        int n = in.length;
        int i = 0;
        while (i < n) {
            char c = in[i];
            char next = i + 1 < n ? in[i + 1] : '\0';
            if (c == '/' && next == '/') {                       // comentário de linha
                while (i < n && in[i] != '\n') {
                    out[i++] = ' ';
                }
            } else if (c == '/' && next == '*') {                // comentário de bloco
                out[i++] = ' ';
                out[i++] = ' ';
                while (i < n && !(in[i] == '*' && i + 1 < n && in[i + 1] == '/')) {
                    if (in[i] != '\n') {
                        out[i] = ' ';
                    }
                    i++;
                }
                if (i < n) {                                     // fecha "*/"
                    out[i++] = ' ';
                    if (i < n) {
                        out[i++] = ' ';
                    }
                }
            } else if (c == '"' && next == '"' && i + 2 < n && in[i + 2] == '"') {   // text block do Java
                i += 3;
                while (i < n && !(in[i] == '"' && i + 2 < n && in[i + 1] == '"' && in[i + 2] == '"')) {
                    if (in[i] == '\\' && i + 1 < n) {
                        out[i++] = ' ';
                    }
                    if (in[i] != '\n') {
                        out[i] = ' ';
                    }
                    i++;
                }
                i = Math.min(n, i + 3);
            } else if (c == '"' || c == '\'' || c == '`') {      // string, caractere ou template literal
                char quote = c;
                i++;
                while (i < n && in[i] != quote) {
                    if (in[i] == '\n' && quote != '`') {         // string não fechada: não engole o resto do arquivo
                        break;
                    }
                    if (in[i] == '\\' && i + 1 < n) {
                        out[i++] = ' ';
                    }
                    if (in[i] != '\n') {
                        out[i] = ' ';
                    }
                    i++;
                }
                if (i < n && in[i] == quote) {
                    i++;
                }
            } else {
                i++;
            }
        }
        return new String(out);
    }
}
