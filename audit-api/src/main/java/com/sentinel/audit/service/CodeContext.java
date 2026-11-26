package com.sentinel.audit.service;

import com.sentinel.audit.model.Finding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Guarda no achado as linhas reais do arquivo ao redor de {@code arquivo:linha}, para a tela mostrar o código de verdade.
 *
 * <p>Vai num campo à parte ({@code codeContext}) porque {@code codeSnippet} também é a identidade do achado entre
 * auditorias, e várias ferramentas o usam para outra coisa (assinatura da função, lista de CVEs, hash).
 *
 * <p>Nunca copia arquivo para achado de segredo: as linhas vizinhas podem ter outras credenciais.
 */
final class CodeContext {

    private static final Logger LOG = Logger.getLogger(CodeContext.class.getName());

    static final int LINES_BEFORE = 2;
    static final int LINES_AFTER = 4;
    static final int MAX_LINE_CHARS = 300;
    static final int MAX_CHARS = 4000;
    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    /** Trechos de ferramenta ou regra que indicam credencial: nesses achados o arquivo não é copiado. */
    private static final List<String> SECRET_MARKERS = List.of(
            "secret", "gitleaks", "trufflehog", "credential", "password", "passwd", "token", "api-key", "apikey", "api_key",
            "private-key", "private_key");

    private CodeContext() {
    }

    /** Preenche o contexto de cada achado cujo arquivo e linha existem dentro do projeto; falha de leitura nunca derruba a auditoria. */
    static void attach(List<Finding> findings, Path projectRoot) {
        if (findings == null || projectRoot == null) {
            return;
        }
        Path root;
        try {
            root = projectRoot.toRealPath();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Projeto ilegível; achados ficam sem contexto de código", e);
            return;
        }
        for (Finding f : findings) {
            try {
                attachOne(f, root);
            } catch (RuntimeException | IOException e) {
                LOG.log(Level.FINE, () -> "Sem contexto de código para " + f.getFilePath());
            }
        }
    }

    private static void attachOne(Finding f, Path root) throws IOException {
        Integer line = f.getLineNumber();
        if (line == null || line < 1 || f.getFilePath() == null || f.getFilePath().isBlank() || isSecret(f)) {
            return;
        }
        Path file = root.resolve(f.getFilePath()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
            return;
        }
        if (!file.toRealPath().startsWith(root)) {
            return;                                   // atalho (symlink) que aponta para fora do projeto
        }
        Excerpt e = read(file, line);
        if (e != null) {
            f.setCodeContext(e.text());
            f.setCodeContextStart(e.start());
        }
    }

    static boolean isSecret(Finding f) {
        String haystack = ((f.getToolSource() == null ? "" : f.getToolSource()) + " " + (f.getRuleId() == null ? "" : f.getRuleId()))
                .toLowerCase(Locale.ROOT);
        return SECRET_MARKERS.stream().anyMatch(haystack::contains);
    }

    record Excerpt(int start, String text) { }

    /** Janela de linhas ao redor da linha do achado; {@code null} se o arquivo não é texto UTF-8 ou a linha não existe. */
    static Excerpt read(Path file, int line) throws IOException {
        List<String> all;
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), decoder))) {
            all = reader.lines().toList();
        } catch (CharacterCodingException | java.io.UncheckedIOException e) {
            return null;                              // binário ou outra codificação
        }
        if (line > all.size()) {
            return null;
        }
        int from = Math.max(1, line - LINES_BEFORE);
        int to = Math.min(all.size(), line + LINES_AFTER);
        List<String> window = new ArrayList<>();
        int chars = 0;
        for (int n = from; n <= to; n++) {
            String text = all.get(n - 1);
            if (text.length() > MAX_LINE_CHARS) {
                text = text.substring(0, MAX_LINE_CHARS) + "…";
            }
            chars += text.length() + 1;
            if (chars > MAX_CHARS && n > line) {
                break;                                // estourou o tamanho: mantém ao menos até a linha do achado
            }
            window.add(text);
        }
        return new Excerpt(from, String.join("\n", window));
    }
}
