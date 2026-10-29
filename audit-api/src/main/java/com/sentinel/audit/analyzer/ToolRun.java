package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.Finding;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Esqueleto comum dos analisadores que rodam uma ferramenta externa e leem o relatório que ela grava: cria os arquivos
 * temporários (relatório e erro), cronometra, transforma falha ou interrupção em resultado parcial e apaga tudo no fim.
 */
final class ToolRun {

    /** O que o corpo do analisador produziu: os achados e, se o resultado pode estar incompleto, o motivo. */
    record Outcome(List<Finding> findings, String partial) {
    }

    @FunctionalInterface
    interface Body {
        Outcome run(Path report, Path stderr) throws Exception;
    }

    private ToolRun() {
    }

    /** Resultado vazio de uma ferramenta que não está instalada ou está desligada. */
    static AnalyzerResult unavailable(String analyzer, String message, Logger log) {
        log.warn(message);
        return new AnalyzerResult(new ArrayList<>(), analyzer, Duration.ZERO);
    }

    static AnalyzerResult run(String analyzer, String tag, Logger log, Body body) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        String partial;
        Path report = null;
        Path stderr = null;
        try {
            stderr = Files.createTempFile(tag + "-err-", ".txt");
            report = Files.createTempFile(tag + "-", ".json");   // 0600 em sistemas POSIX
            Outcome outcome = body.run(report, stderr);
            findings = outcome.findings();
            partial = outcome.partial();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            partial = "execução interrompida";
        } catch (Exception e) {
            log.errorf("Erro ao executar o %s: %s", analyzer, e.getMessage());
            partial = "erro ao executar: " + e.getMessage();
        } finally {
            ExternalTools.deleteQuietly(stderr);
            ExternalTools.deleteQuietly(report);
        }
        return new AnalyzerResult(findings, analyzer, Duration.between(start, Instant.now()), partial);
    }
}
