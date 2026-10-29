package com.sentinel.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinel.audit.dto.AuditDiffResponse;
import com.sentinel.audit.dto.FindingResponse;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.repository.AuditRunRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Avisa um canal (Teams, Slack ou um webhook genérico) quando algo relevante acontece numa auditoria:
 * falha, queda de score ou findings graves <b>novos</b>. Auditorias sem novidade não geram mensagem,
 * para o canal não virar ruído.
 * <p>
 * A configuração é de cada sistema (cadastro do sistema); a URL do webhook fica cifrada e só aceita https para
 * endereços públicos (nada de localhost nem rede interna). Falha ao notificar nunca afeta a auditoria.
 */
@ApplicationScoped
public class NotificationService {

    private static final int DEFAULT_MIN_SCORE_DROP = 5;
    /** IPv6 tem 16 bytes; endereço local único (fc00::/7) tem o primeiro byte 0xfc ou 0xfd. */
    private static final int IPV6_BYTES = 16;
    private static final int IPV6_ULA_MASK = 0xfe;
    private static final int IPV6_ULA_PREFIX = 0xfc;
    private static final int RANK_CRITICAL = 5;
    private static final int RANK_HIGH = 4;
    private static final int RANK_MEDIUM = 3;
    private static final int HTTP_TIMEOUT_SECONDS = 15;


    private static final Logger LOG = Logger.getLogger(NotificationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    SecretBox secretBox;

    /** Configuração de aviso de um sistema (a URL já decifrada). */
    public record Settings(String url, String format, double minScoreDrop, String minSeverity, String publicUrl) {}

    @Inject
    AuditRunRepository auditRunRepository;

    @Inject
    FindingLifecycleService lifecycle;

    private volatile HttpClient http;

    /** O que importa saber de uma auditoria para decidir e montar o aviso. */
    public record Notice(Long auditId, Long systemId, String systemName, String branch, AuditStatus status,
                         String errorMessage, Double score, Double scoreDelta, boolean hasBaseline,
                         int newTotal, int newAtLeastMin, int resolved, String topNewTitle) {}

    /** Chamado ao fim da auditoria (concluída ou falha). Nunca lança exceção. */
    public void notifyFinished(Long auditRunId) {
        try {
            Settings settings = settingsFor(auditRunId);
            if (settings == null || settings.url() == null || settings.url().isBlank()) {
                return;
            }
            Notice notice = loadNotice(auditRunId, settings.minSeverity());
            if (notice != null && shouldNotify(notice, settings.minScoreDrop())) {
                validateTarget(settings.url());
                send(settings.url(), buildPayload(settings.format(), notice, settings.publicUrl()));
            }
        } catch (Exception e) {
            LOG.warnf("Não foi possível enviar a notificação da auditoria %d: %s", auditRunId, e.getMessage());
        }
    }

    @Transactional
    Settings settingsFor(Long auditRunId) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null || !run.getSystem().hasNotifyUrl()) {
            return null;
        }
        var system = run.getSystem();
        String url = secretBox.decrypt(system.getNotifyUrlEnc());
        return new Settings(url,
                system.getNotifyFormat() == null ? "slack" : system.getNotifyFormat(),
                system.getNotifyMinScoreDrop() == null ? DEFAULT_MIN_SCORE_DROP : system.getNotifyMinScoreDrop(),
                system.getNotifyMinSeverity() == null ? "HIGH" : system.getNotifyMinSeverity(),
                system.getNotifyPublicUrl());
    }

    /**
     * Aceita só https para endereço público: o destino é digitado por quem cadastra o sistema, então não pode apontar
     * para a própria máquina nem para a rede interna (SSRF).
     */
    public static void validateTarget(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL de notificação inválida", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("A URL de notificação deve ser https://, sem usuário e senha");
        }
        requirePublicAddress(uri);
    }

    private static void requirePublicAddress(URI uri) {
        try {
            for (java.net.InetAddress a : java.net.InetAddress.getAllByName(uri.getHost())) {
                if (isInternal(a)) {
                    throw new IllegalArgumentException("A URL de notificação não pode apontar para rede interna ou local");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Não foi possível resolver o endereço da notificação: " + uri.getHost(), e);
        }
    }

    private static boolean isInternal(java.net.InetAddress a) {
        return a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress() || isUniqueLocalV6(a);
    }

    private static boolean isUniqueLocalV6(java.net.InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == IPV6_BYTES && (b[0] & IPV6_ULA_MASK) == IPV6_ULA_PREFIX;
    }

    @Transactional
    Notice loadNotice(Long auditRunId, String minNewSeverity) {
        AuditRun run = auditRunRepository.findById(auditRunId);
        if (run == null) {
            return null;
        }
        Double score = run.getRiskScore() == null ? null : run.getRiskScore().getOverall();
        if (run.getStatus() != AuditStatus.COMPLETED) {
            return new Notice(run.getId(), run.getSystem().getId(), run.getSystem().getName(), run.getBranch(),
                    run.getStatus(), run.getErrorMessage(), score, null, false, 0, 0, 0, null);
        }
        AuditDiffResponse diff = lifecycle.diff(auditRunId);
        int min = rank(minNewSeverity);
        int atLeast = 0;
        FindingResponse top = null;
        for (FindingResponse f : diff.newFindings()) {
            if (rank(f.getSeverity().name()) >= min) {
                atLeast++;
            }
            if (top == null || rank(f.getSeverity().name()) > rank(top.getSeverity().name())) {
                top = f;
            }
        }
        return new Notice(run.getId(), run.getSystem().getId(), run.getSystem().getName(), run.getBranch(),
                run.getStatus(), null, score, diff.scoreDelta(), diff.hasBaseline(),
                diff.newFindings().size(), atLeast, diff.resolvedFindings().size(), top == null ? null : top.getTitle());
    }

    /** Falha sempre avisa; auditoria concluída só se o score caiu o bastante ou surgiu algo grave novo. */
    static boolean shouldNotify(Notice n, double minScoreDrop) {
        if (n.status() == AuditStatus.FAILED) {
            return true;
        }
        if (!n.hasBaseline()) {
            return false;                       // primeira auditoria: nada a comparar
        }
        boolean dropped = n.scoreDelta() != null && n.scoreDelta() <= -minScoreDrop;
        return dropped || n.newAtLeastMin() > 0;
    }

    // ------------------------------------------------------------------ mensagem

    static String buildPayload(String format, Notice n, String publicUrl) throws IOException {
        String text = buildText(n);
        String link = publicUrl == null || publicUrl.isBlank() ? null
                : publicUrl.replaceAll("/+$", "") + "/systems/" + n.systemId();
        ObjectNode root = MAPPER.createObjectNode();
        switch (format.toLowerCase(Locale.ROOT)) {
            case "teams" -> teamsPayload(root, n, text, link);
            case "generic" -> genericPayload(root, n, text, link);
            default -> root.put("text", "*" + title(n) + "*\n" + text + (link != null ? "\n<" + link + "|Abrir no dashboard>" : ""));
        }
        return MAPPER.writeValueAsString(root);
    }

    /** Formato aceito pelo conector "Workflows" do Teams (Adaptive Card em attachments). */
    private static void teamsPayload(ObjectNode root, Notice n, String text, String link) {
        root.put("type", "message");
        ObjectNode attachment = ((ArrayNode) root.putArray("attachments")).addObject();
        attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
        ObjectNode card = attachment.putObject("content");
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        ArrayNode body = card.putArray("body");
        body.addObject().put("type", "TextBlock").put("text", title(n)).put("weight", "Bolder").put("size", "Medium").put("wrap", true);
        body.addObject().put("type", "TextBlock").put("text", text).put("wrap", true);
        if (link != null) {
            ObjectNode action = card.putArray("actions").addObject();
            action.put("type", "Action.OpenUrl").put("title", "Abrir no dashboard").put("url", link);
        }
    }

    private static void genericPayload(ObjectNode root, Notice n, String text, String link) {
        root.put("event", n.status() == AuditStatus.FAILED ? "audit.failed" : "audit.completed");
        root.put("auditId", n.auditId());
        root.put("systemId", n.systemId());
        root.put("system", n.systemName());
        root.put("branch", n.branch());
        root.put("status", n.status().name());
        if (n.score() != null) root.put("score", n.score());
        if (n.scoreDelta() != null) root.put("scoreDelta", n.scoreDelta());
        root.put("newFindings", n.newTotal());
        root.put("resolvedFindings", n.resolved());
        if (n.errorMessage() != null) root.put("error", n.errorMessage());
        if (link != null) root.put("url", link);
        root.put("text", text);
    }

    static String title(Notice n) {
        return (n.status() == AuditStatus.FAILED ? "Auditoria FALHOU: " : "Auditoria: ") + n.systemName() + " (" + n.branch() + ")";
    }

    static String buildText(Notice n) {
        if (n.status() == AuditStatus.FAILED) {
            return "A auditoria #" + n.auditId() + " não terminou. Motivo: "
                    + (n.errorMessage() == null || n.errorMessage().isBlank() ? "não informado" : n.errorMessage());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Score ").append(n.score() == null ? "—" : fmt(n.score()));
        if (n.scoreDelta() != null) {
            sb.append(" (").append(n.scoreDelta() > 0 ? "+" : "").append(fmt(n.scoreDelta())).append(" desde a auditoria anterior)");
        }
        sb.append(". ").append(n.newTotal()).append(" finding(s) novo(s)");
        if (n.newAtLeastMin() > 0) {
            sb.append(", ").append(n.newAtLeastMin()).append(" deles grave(s)");
        }
        sb.append("; ").append(n.resolved()).append(" corrigido(s).");
        if (n.topNewTitle() != null) {
            sb.append(" Mais grave dos novos: ").append(n.topNewTitle()).append('.');
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static int rank(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> RANK_CRITICAL;
            case "HIGH" -> RANK_HIGH;
            case "MEDIUM" -> RANK_MEDIUM;
            case "LOW" -> 2;
            default -> 1;
        };
    }

    // ------------------------------------------------------------------ HTTP (sobrescrevível em testes)

    protected void send(String url, String json) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("URL de notificação inválida");
        }
        if (http == null) {
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        }
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Webhook respondeu HTTP " + response.statusCode());
        }
    }
}
