package com.sentinel.audit.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * CLI para disparar e acompanhar auditorias do Sentinel Audit Tool.
 *
 * <p>Uso:
 * <pre>
 *   java -jar sentinel-audit-cli.jar \
 *     --system-id 1 \
 *     --branch main \
 *     --api-url http://localhost:8080 \
 *     --api-key $AUDIT_API_KEY \
 *     --fail-below 70 \
 *     --fail-on-new HIGH \
 *     --fail-on-score-drop 0 \
 *     --sarif resultado.sarif --junit resultado.xml
 * </pre>
 *
 * <p>{@code --api-key} é a chave da API do Audit Tool (também lida de {@code AUDIT_API_KEY}),
 * e não o token do Git. O repositório é o cadastrado no sistema ({@code --repo} é apenas informativo).
 *
 * <p>Códigos de saída:
 * <ul>
 *   <li>{@code 0} — Auditoria concluída com sucesso (score >= threshold ou threshold não definido)</li>
 *   <li>{@code 1} — Score abaixo de {@code --fail-below}, ou findings que violam {@code --fail-on} /
 *       {@code --fail-on-new}</li>
 *   <li>{@code 2} — Auditoria falhou ou foi cancelada (status FAILED/CANCELLED) ou erro de comunicação</li>
 *   <li>{@code 3} — Timeout aguardando conclusão da auditoria</li>
 * </ul>
 */
public class AuditCli {

    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int EXIT_TIMEOUT = 3;
    private static final int HTTP_OK = 200;
    private static final int HTTP_ACCEPTED = 202;
    static final int RANK_CRITICAL = 5;
    static final int RANK_HIGH = 4;
    private static final int RANK_MEDIUM = 3;


    private static final int POLL_INTERVAL_SECONDS = 10;
    private static final int MAX_WAIT_MINUTES      = 15;
    private static final int MAX_POLLS             = (MAX_WAIT_MINUTES * 60) / POLL_INTERVAL_SECONDS;

    /** Intervalo entre consultas de status (os testes usam 0). */
    int pollSeconds = POLL_INTERVAL_SECONDS;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AuditCli() {
        this.httpClient    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        this.objectMapper  = new ObjectMapper();
    }

    /**
     * Ponto de entrada principal.
     *
     * @param args argumentos da linha de comando
     */
    public static void main(String[] args) {
        CliArgs cliArgs = parseArgs(args);
        AuditCli cli    = new AuditCli();
        int exitCode    = cli.run(cliArgs);
        System.exit(exitCode);
    }

    /**
     * Executa o fluxo completo: cria auditoria → polling → imprime resumo → retorna exit code.
     *
     * @param args argumentos parseados
     * @return exit code (0 = sucesso, 1 = abaixo do threshold, 2 = falha, 3 = timeout)
     */
    public int run(CliArgs args) {
        try {
            printHeader(args);

            long auditRunId = createAudit(args);
            System.out.printf("Auditoria criada com ID: %d%n", auditRunId);
            System.out.println("Aguardando conclusão...");

            JsonNode finalResult = pollUntilComplete(args.apiUrl, auditRunId, args.token);

            if (finalResult == null) {
                System.err.println("Timeout: auditoria não concluída em " + MAX_WAIT_MINUTES + " minutos.");
                return EXIT_TIMEOUT;
            }
            Integer abnormalExit = exitForAbnormalEnd(finalResult);
            if (abnormalExit != null) {
                return abnormalExit;
            }

            printSummary(finalResult);

            JsonNode findings = null;
            if (needsFindings(args)) {
                findings = fetchFindings(args, auditRunId);
                writeReports(args, findings, auditRunId);
            }

            // Todas as verificações rodam (cada uma explica sua falha); basta uma para reprovar.
            boolean failed = failedBelowScore(args, finalResult);
            failed |= failedOnFindings(args, findings);
            failed |= failedOnNewFindings(args, auditRunId);
            failed |= failedOnScoreDrop(args, auditRunId);
            if (failed) {
                return 1;
            }

            System.out.println("\nAuditoria concluída com sucesso.");
            return 0;

        } catch (Exception e) {
            System.err.println("Erro ao executar auditoria: " + e.getMessage());
            return 2;
        }
    }

    private static void printHeader(CliArgs args) {
        System.out.println("=== Sentinel Audit CLI ===");
        System.out.printf("API:    %s%n", args.apiUrl);
        System.out.printf("Repo:   %s%n", args.repo != null ? args.repo : "(cadastrado no sistema)");
        System.out.printf("Branch: %s%n", args.branch);
        System.out.printf("System: %s%n", args.systemId);
        System.out.println();
    }

    /** Auditoria cancelada ou que falhou: avisa e devolve o código de saída; {@code null} se terminou normalmente. */
    private static Integer exitForAbnormalEnd(JsonNode finalResult) {
        String status = finalResult.path("status").asText();
        if ("CANCELLED".equals(status)) {
            System.err.println("Auditoria CANCELADA antes de terminar.");
            return 2;
        }
        if ("FAILED".equals(status)) {
            String reason = finalResult.path("errorMessage").asText("");
            System.err.println("Auditoria FALHOU." + (reason.isBlank() ? "" : " Motivo: " + reason));
            return 2;
        }
        return null;
    }

    /** Relatórios e --fail-on precisam da lista de achados. */
    private static boolean needsFindings(CliArgs args) {
        return args.sarifFile != null || args.junitFile != null || args.jsonFile != null || args.failOn != null
                || args.prCommentFile != null;
    }

    private static boolean failedBelowScore(CliArgs args, JsonNode finalResult) {
        if (args.failBelow == null) {
            return false;
        }
        double overall = finalResult.path("riskScore").path("overall").asDouble(-1);
        if (overall >= 0 && overall < args.failBelow) {
            System.err.printf("%nFALHA: score %.2f está abaixo do threshold %.2f%n", overall, args.failBelow);
            return true;
        }
        return false;
    }

    private static boolean failedOnFindings(CliArgs args, JsonNode findings) {
        if (args.failOn == null) {
            return false;
        }
        long count = countAtLeast(findings, args.failOn);
        if (count > 0) {
            System.err.printf("%nFALHA: %d finding(s) com severidade %s ou maior (--fail-on %s)%n",
                    count, args.failOn, args.failOn);
            return true;
        }
        return false;
    }

    private boolean failedOnNewFindings(CliArgs args, long auditRunId) throws Exception {
        if (args.failOnNew == null) {
            return false;
        }
        JsonNode diff = fetchJson(args, "/api/v1/audits/" + auditRunId + "/diff");
        if (!diff.path("hasBaseline").asBoolean(false)) {
            System.out.println("\n--fail-on-new ignorado: primeira auditoria desta branch (sem base de comparação).");
            return false;
        }
        long count = countAtLeast(diff.path("newFindings"), args.failOnNew);
        if (count > 0) {
            System.err.printf("%nFALHA: %d finding(s) NOVO(S) com severidade %s ou maior desde a auditoria #%d%n",
                    count, args.failOnNew, diff.path("baseAuditId").asLong());
            return true;
        }
        return false;
    }

    /** Catraca da nota: reprova se ela caiu mais que o permitido em relação à auditoria anterior. */
    private boolean failedOnScoreDrop(CliArgs args, long auditRunId) throws Exception {
        if (args.failOnScoreDrop == null) {
            return false;
        }
        JsonNode diff = fetchJson(args, "/api/v1/audits/" + auditRunId + "/diff");
        JsonNode delta = diff.path("scoreDelta");
        if (!diff.path("hasBaseline").asBoolean(false) || !delta.isNumber()) {
            System.out.println("\n--fail-on-score-drop ignorado: sem auditoria anterior para comparar.");
            return false;
        }
        double drop = -delta.asDouble();
        if (drop > args.failOnScoreDrop) {
            System.err.printf("%nFALHA: a nota caiu %.2f ponto(s) desde a auditoria #%d (permitido: %.2f)%n",
                    drop, diff.path("baseAuditId").asLong(), args.failOnScoreDrop);
            return true;
        }
        return false;
    }

    private JsonNode fetchJson(CliArgs args, String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(args.apiUrl + path)).GET();
        addAuth(builder, args.token);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HTTP_OK) {
            throw new IllegalStateException("Falha ao consultar " + path + " (HTTP " + response.statusCode() + ")");
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode fetchFindings(CliArgs args, long auditRunId) throws Exception {
        return fetchJson(args, "/api/v1/audits/" + auditRunId + "/findings");
    }

    private void writeReports(CliArgs args, JsonNode findings, long auditRunId) throws Exception {
        if (args.sarifFile != null) {
            write(args.sarifFile, SarifWriter.write(findings, "1.0.0"));
        }
        if (args.junitFile != null) {
            write(args.junitFile, JUnitWriter.write(findings, "Sentinel Audit #" + auditRunId));
        }
        if (args.jsonFile != null) {
            write(args.jsonFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(findings));
        }
        if (args.prCommentFile != null) {
            write(args.prCommentFile, MarkdownWriter.write(findings, "Sentinel Audit #" + auditRunId, args.baseRef));
        }
    }

    private static void write(String file, String content) throws Exception {
        Path path = Path.of(file).toAbsolutePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
        System.out.println("Relatório gravado em " + path);
    }

    /** Quantos findings têm severidade igual ou maior que {@code minimum}. */
    static long countAtLeast(JsonNode findings, String minimum) {
        int min = severityRank(minimum);
        long count = 0;
        if (findings != null) {
            for (JsonNode f : findings) {
                // modo sombra e suprimidos não derrubam o pipeline (também não pesam no score)
                if (f.path("shadow").asBoolean(false) || "SUPPRESSED".equals(f.path("status").asText(""))) {
                    continue;
                }
                if (severityRank(f.path("severity").asText("")) >= min) {
                    count++;
                }
            }
        }
        return count;
    }

    static int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> RANK_CRITICAL;
            case "HIGH" -> RANK_HIGH;
            case "MEDIUM" -> RANK_MEDIUM;
            case "LOW" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
    }

    /** Envia a chave em X-API-Key e Authorization: Bearer (a API aceita qualquer um dos dois). */
    private static void addAuth(HttpRequest.Builder builder, String token) {
        if (token != null && !token.isBlank()) {
            builder.header("X-API-Key", token);
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private long createAudit(CliArgs args) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("systemId",    Long.parseLong(args.systemId));
        body.put("branch",      args.branch);
        if (args.baseRef != null) {
            body.put("baseRef", args.baseRef);
        }
        body.put("triggeredBy", "sentinel-audit-cli");

        String json = objectMapper.writeValueAsString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(args.apiUrl + "/api/v1/audits"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));

        addAuth(builder, args.token);

        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != HTTP_ACCEPTED) {
            throw new IllegalStateException("Falha ao criar auditoria (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonNode responseBody = objectMapper.readTree(response.body());
        return responseBody.path("id").asLong();
    }

    private JsonNode pollUntilComplete(String apiUrl, long auditRunId, String token) throws Exception {
        for (int poll = 0; poll < MAX_POLLS; poll++) {
            Thread.sleep(Duration.ofSeconds(pollSeconds));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/api/v1/audits/" + auditRunId))
                    .GET();
            addAuth(requestBuilder, token);
            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HTTP_OK) {
                System.err.printf("Erro ao consultar auditoria (HTTP %d)%n", response.statusCode());
                continue;
            }

            JsonNode result = objectMapper.readTree(response.body());
            String status   = result.path("status").asText();

            System.out.printf("[%d/%d] Status: %s%n", poll + 1, MAX_POLLS, status);

            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                return result;
            }
        }

        return null; // timeout
    }

    private void printSummary(JsonNode result) {
        System.out.println("\n========== RESUMO DA AUDITORIA ==========");
        System.out.printf("Sistema:     %s%n", result.path("systemName").asText());
        System.out.printf("Branch:      %s%n", result.path("branch").asText());
        System.out.printf("Status:      %s%n", result.path("status").asText());
        System.out.printf("Início:      %s%n", result.path("startedAt").asText());
        System.out.printf("Conclusão:   %s%n", result.path("completedAt").asText());

        JsonNode riskScore = result.path("riskScore");
        if (!riskScore.isMissingNode()) {
            System.out.println("\n--- Score de Risco ---");
            System.out.printf("  Geral:          %.2f%n", riskScore.path("overall").asDouble());
            System.out.printf("  Segurança:      %.2f%n", riskScore.path("security").asDouble());
            System.out.printf("  Qualidade:      %.2f%n", riskScore.path("quality").asDouble());
            System.out.printf("  Arquitetura:    %.2f%n", riskScore.path("architecture").asDouble());
            System.out.printf("  Dependências:   %.2f%n", riskScore.path("dependencies").asDouble());
            System.out.printf("  Observabilidade:%.2f%n", riskScore.path("observability").asDouble());
            if (riskScore.hasNonNull("performance")) {
                System.out.printf("  Desempenho:     %.2f%n", riskScore.path("performance").asDouble());
            }
        }

        JsonNode summary = result.path("findingsSummary");
        if (!summary.isMissingNode()) {
            System.out.println("\n--- Findings por Severidade ---");
            printSeverityCount(summary, "CRITICAL");
            printSeverityCount(summary, "HIGH");
            printSeverityCount(summary, "MEDIUM");
            printSeverityCount(summary, "LOW");
            printSeverityCount(summary, "INFO");
        }
        System.out.println("=========================================");
    }

    private void printSeverityCount(JsonNode summary, String severity) {
        long count = summary.path(severity).asLong(0);
        System.out.printf("  %-10s %d%n", severity + ":", count);
    }

    /**
     * Parseia os argumentos da linha de comando.
     *
     * @param args array de strings da linha de comando
     * @return objeto {@link CliArgs} com os valores parseados
     */
    static CliArgs parseArgs(String[] args) {
        try {
            return parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(USAGE);
            System.exit(2);
            return null; // inalcançável
        }
    }

    private static final String USAGE =
            "Uso: java -jar sentinel-audit-cli.jar --system-id <id> [--branch <branch>] [--api-url <url>]\n"
          + "       [--api-key <chave> | env AUDIT_API_KEY] [--fail-below <score>]\n"
          + "       [--fail-on <CRITICAL|HIGH|MEDIUM|LOW>] [--fail-on-new <CRITICAL|HIGH|MEDIUM|LOW>]\n"
          + "       [--fail-on-score-drop <pontos>  reprova se a nota cair mais que isso desde a auditoria anterior]\n"
          + "       [--sarif <arquivo>] [--junit <arquivo>] [--json <arquivo>] [--repo <url> (informativo)]\n"
          + "       [--base-ref <branch-base>  audita só o que mudou em relação a ele (modo PR)]\n"
          + "       [--pr-comment <arquivo.md>  grava o resumo em Markdown para comentar no PR]";

    /** Versão testável de {@link #parseArgs}: lança {@link IllegalArgumentException} em vez de encerrar o processo. */
    static CliArgs parse(String[] args) {
        CliArgs result = new CliArgs();
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
    private static CliArgs validated(CliArgs result) {
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
    private static final Map<String, BiConsumer<CliArgs, String>> FLAGS = Map.ofEntries(
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

    private static void applyFlag(CliArgs result, String flag, String value) {
        BiConsumer<CliArgs, String> setter = FLAGS.get(flag);
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
        if (severityRank(v) < 2) { // INFO não faz sentido como limite de falha
            throw new IllegalArgumentException(flag + " deve ser CRITICAL, HIGH, MEDIUM ou LOW: " + value);
        }
        return v;
    }

    /**
     * Contêiner para os argumentos CLI parseados.
     */
    static class CliArgs {
        String repo;
        String branch;
        String systemId;
        String apiUrl;
        String token;
        Double failBelow;
        String failOn;
        String failOnNew;
        Double failOnScoreDrop;
        String sarifFile;
        String junitFile;
        String jsonFile;
        String baseRef;
        String prCommentFile;
    }
}
