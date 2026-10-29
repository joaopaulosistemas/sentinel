package com.sentinel.audit.analyzer.quality;

/**
 * Apaga comentários e conteúdo de strings/caracteres do código-fonte, trocando-os por espaços.
 * <p>
 * O resultado tem <b>o mesmo tamanho e as mesmas quebras de linha</b> do original, então índices e números de
 * linha continuam valendo. Serve para que chaves, parênteses e palavras como {@code if} dentro de textos ou
 * comentários não confundam a contagem de métricas.
 */
public final class SourceSanitizer {

    private static final int TEXT_BLOCK_DELIMITER_LENGTH = 3;


    private SourceSanitizer() {}

    public static String strip(String src) {
        Blanker b = new Blanker(src);
        while (b.i < b.n) {
            b.step();
        }
        return new String(b.out);
    }

    /** Percorre o texto e apaga, em {@code out}, o que está em comentários e textos. */
    private static final class Blanker {
        final char[] in;
        final char[] out;
        final int n;
        int i;

        Blanker(String src) {
            in = src.toCharArray();
            out = in.clone();
            n = in.length;
        }

        char at(int k) {
            return k < n ? in[k] : '\0';
        }

        /** Consome o próximo trecho: um comentário, um texto ou um caractere comum. */
        void step() {
            char c = in[i];
            char next = at(i + 1);
            if (c == '/' && next == '/') {
                lineComment();
            } else if (c == '/' && next == '*') {
                blockComment();
            } else if (startsTextBlock(c, next)) {
                textBlock();
            } else if (isQuote(c)) {
                quoted(c);
            } else {
                i++;
            }
        }

        private boolean startsTextBlock(char c, char next) {
            return c == '"' && next == '"' && at(i + 2) == '"';
        }

        private static boolean isQuote(char c) {
            return c == '"' || c == '\'' || c == '`';
        }

        private void lineComment() {
            while (i < n && in[i] != '\n') {
                out[i++] = ' ';
            }
        }

        private void blockComment() {
            out[i++] = ' ';
            out[i++] = ' ';
            while (i < n && !(in[i] == '*' && at(i + 1) == '/')) {
                blank(i);
                i++;
            }
            if (i < n) {                                         // fecha "*/"
                out[i++] = ' ';
                if (i < n) {
                    out[i++] = ' ';
                }
            }
        }

        /** Text block do Java: três aspas até as três seguintes. */
        private void textBlock() {
            i += TEXT_BLOCK_DELIMITER_LENGTH;
            while (i < n && !(in[i] == '"' && at(i + 1) == '"' && at(i + 2) == '"')) {
                skipEscape();
                blank(i);
                i++;
            }
            i = Math.min(n, i + TEXT_BLOCK_DELIMITER_LENGTH);
        }

        /** String, caractere ou template literal. */
        private void quoted(char quote) {
            i++;
            while (i < n && in[i] != quote) {
                if (in[i] == '\n' && quote != '`') {             // string não fechada: não engole o resto do arquivo
                    break;
                }
                skipEscape();
                blank(i);
                i++;
            }
            if (i < n && in[i] == quote) {
                i++;
            }
        }

        /** Barra invertida: apaga ela e deixa o caractere seguinte para o laço apagar. */
        private void skipEscape() {
            if (in[i] == '\\' && i + 1 < n) {
                out[i++] = ' ';
            }
        }

        private void blank(int k) {
            if (in[k] != '\n') {
                out[k] = ' ';
            }
        }
    }
}
