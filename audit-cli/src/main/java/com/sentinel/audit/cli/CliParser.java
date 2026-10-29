package com.sentinel.audit.cli;

import java.util.Map;
import java.util.function.BiConsumer;

/** Lê e valida as opções da linha de comando do {@code sentinel-audit-cli}. */
final class CliParser {
    static final String USAGE =
            "Uso: java -jar sentinel-audit-cli.jar --system-id <id> [--branch <branch>] [--api-url <url>]\n"
          + "       [--api-key <chave> | env AUDIT_API_KEY] [--fail-below <score>]\n"
          + "       [--fail-on <CRITICAL|HIGH|MEDIUM|LOW>] [--fail-on-new <CRITICAL|HIGH|MEDIUM|LOW>]\n"
          + "       [--fail-on-score-drop <pontos>  reprova se a nota cair mais que isso desde a auditoria anterior]\n"
          + "       [--sarif <arquivo>] [--junit <arquivo>] [--json <arquivo>] [--repo <url> (informativo)]\n"
          + "       [--base-ref <branch-base>  audita só o que mudou em relação a ele (modo PR)]\n"
          + "       [--pr-comment <arquivo.md>  grava o resumo em Markdown para comentar no PR]";


    private CliParser() {
    }

    /** Lê as opções da linha de comando; lança {@link IllegalArgumentException} em vez de encerrar o processo. */
    static AuditCli.CliArgs parse(String[] args) {
        AuditCli.CliArgs result = new AuditCli.CliArgs();
        result.branch  = "main";
        result.apiUrl  = "http://localhost:8080";

        for (int i = 0; i < args.length; i += 2) {
            String flag = args[i];
            if (flag.equals("--help") || flag.equals("-h")) {
                throw new IllegalArgumentException("Ajuda:");
            }
            if (!flag.startsWith("--")) {
                throw new IllegalArgumentException("Argumento inesperado: " + flag);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("O parâmetro " + flag + " precisa de um valor");
            }
            applyFlag(result, flag, args[i + 1]);
        }
        return validated(result);
    }

    /** Completa o token pela variável de ambiente e confere o id do sistema. */
    private static AuditCli.CliArgs validated(AuditCli.CliArgs result) {
        if (result.token == null || result.token.isBlank()) {
            result.token = System.getenv("AUDIT_API_KEY");
        }
        if (result.systemId == null || result.systemId.isBlank()) {
            throw new IllegalArgumentException("Parâmetro obrigatório ausente: --system-id");
        }
        try {
            Long.parseLong(result.systemId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--system-id deve ser um número: " + result.systemId, e);
        }
        return result;
    }

    /** Como cada opção grava seu valor nos argumentos. */
    private static final Map<String, BiConsumer<AuditCli.CliArgs, String>> FLAGS = Map.ofEntries(
            Map.entry("--repo", (a, v) -> a.repo = v),
            Map.entry("--branch", (a, v) -> a.branch = v),
            Map.entry("--system-id", (a, v) -> a.systemId = v),
            Map.entry("--api-url", (a, v) -> a.apiUrl = v.endsWith("/") ? v.substring(0, v.length() - 1) : v),
            Map.entry("--token", (a, v) -> a.token = v),
            Map.entry("--api-key", (a, v) -> a.token = v),
            Map.entry("--fail-below", (a, v) -> a.failBelow = parseNumber("--fail-below", v)),
            Map.entry("--fail-on", (a, v) -> a.failOn = parseSeverity("--fail-on", v)),
            Map.entry("--fail-on-new", (a, v) -> a.failOnNew = parseSeverity("--fail-on-new", v)),
            Map.entry("--fail-on-score-drop", (a, v) -> a.failOnScoreDrop = parseNumber("--fail-on-score-drop", v)),
            Map.entry("--sarif", (a, v) -> a.sarifFile = v),
            Map.entry("--junit", (a, v) -> a.junitFile = v),
            Map.entry("--json", (a, v) -> a.jsonFile = v),
            Map.entry("--base-ref", (a, v) -> a.baseRef = v),
            Map.entry("--pr-comment", (a, v) -> a.prCommentFile = v));

    private static void applyFlag(AuditCli.CliArgs result, String flag, String value) {
        BiConsumer<AuditCli.CliArgs, String> setter = FLAGS.get(flag);
        if (setter == null) {
            throw new IllegalArgumentException("Parâmetro desconhecido: " + flag);
        }
        setter.accept(result, value);
    }

    private static double parseNumber(String flag, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " deve ser um número: " + value, e);
        }
    }

    private static String parseSeverity(String flag, String value) {
        String v = value.toUpperCase(java.util.Locale.ROOT);
        if (AuditCli.severityRank(v) < 2) { // INFO não faz sentido como limite de falha
            throw new IllegalArgumentException(flag + " deve ser CRITICAL, HIGH, MEDIUM ou LOW: " + value);
        }
        return v;
    }
}
