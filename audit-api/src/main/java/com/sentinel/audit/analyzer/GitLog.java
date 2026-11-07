package com.sentinel.audit.analyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Roda {@code git log} no clone e devolve a saída; usado pelos analisadores que leem o histórico. */
final class GitLog {

    /** Quanto o git pode demorar antes de a leitura do histórico ser abandonada. */
    static final int TIMEOUT_SECONDS = 30;

    private GitLog() {}

    /**
     * Saída de {@code git log <args>} na pasta, ou texto vazio se não houver Git, histórico ou se estourar o tempo.
     * A saída vai para um arquivo temporário (apagado no fim), não para a memória do processo.
     */
    static String read(Path root, List<String> logArgs) throws IOException, InterruptedException {
        Path out = Files.createTempFile("sentinel-gitlog-", ".txt");
        try {
            List<String> command = new ArrayList<>(List.of("git", "-C", root.toString(), "log"));
            command.addAll(logArgs);
            Process p = new ProcessBuilder(command)
                    .redirectErrorStream(true).redirectOutput(out.toFile()).start();
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            return p.exitValue() == 0 ? Files.readString(out, StandardCharsets.UTF_8) : "";
        } finally {
            Files.deleteIfExists(out);
        }
    }
}
