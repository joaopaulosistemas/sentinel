package com.sentinel.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.repository.AuditedSystemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Trata webhooks de push do GitHub e do GitLab: valida a autenticidade, descobre qual sistema cadastrado
 * é o repositório e dispara a auditoria quando o push é na branch configurada.
 */
@ApplicationScoped
public class WebhookService {

    private static final Logger LOG = Logger.getLogger(WebhookService.class);
    private static final String ZERO_SHA = "0000000000000000000000000000000000000000";

    /** Push já interpretado: URLs possíveis do repositório e a branch atualizada ({@code null} se não for branch). */
    public record Push(List<String> repoUrls, String branch) {}

    @Inject
    AuditedSystemRepository systemRepository;

    @Inject
    AuditRequestService requestService;

    // ------------------------------------------------------------------ autenticidade

    /** GitHub: cabeçalho {@code X-Hub-Signature-256: sha256=<hmac hex do corpo>}. */
    public static boolean validGithubSignature(String secret, byte[] body, String header) {
        if (secret == null || secret.isBlank() || header == null || !header.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /** GitLab: cabeçalho {@code X-Gitlab-Token} igual ao segredo. */
    public static boolean validGitlabToken(String secret, String token) {
        return secret != null && !secret.isBlank() && token != null
                && MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ interpretação do payload

    /** @return o push, ou {@code null} se o evento não deve disparar auditoria (tag, branch apagada, formato desconhecido) */
    public static Push parsePush(JsonNode payload) {
        String ref = payload.path("ref").asText("");
        if (!ref.startsWith("refs/heads/")) {
            return null;                                     // tag ou evento que não é push
        }
        if (payload.path("deleted").asBoolean(false) || ZERO_SHA.equals(payload.path("after").asText(""))) {
            return null;                                     // branch apagada
        }
        List<String> urls = new ArrayList<>();
        for (String field : List.of("clone_url", "html_url", "git_http_url", "web_url")) {
            for (String parent : List.of("repository", "project")) {
                String v = payload.path(parent).path(field).asText("");
                if (!v.isBlank()) {
                    urls.add(v);
                }
            }
        }
        return urls.isEmpty() ? null : new Push(urls, ref.substring("refs/heads/".length()));
    }

    /** Remove esquema, credenciais, ".git" e "/" final e usa minúsculas: formas diferentes da mesma URL ficam iguais. */
    public static String normalizeRepo(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim().toLowerCase(Locale.ROOT).replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        u = u.replaceFirst("^[^/@]*@", "");                  // user:token@ ou git@
        u = u.replaceFirst("^([^/:]+):(?!\\d+/)", "$1/");     // scp-like host:org/repo (porta numérica é preservada)
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith(".git")) {
            u = u.substring(0, u.length() - 4);
        }
        return u;
    }

    // ------------------------------------------------------------------ disparo

    /** @return quantas auditorias foram disparadas */
    public int trigger(Push push, String source) {
        List<String> wanted = push.repoUrls().stream().map(WebhookService::normalizeRepo).toList();
        int started = 0;
        for (AuditedSystem system : systemRepository.listAll()) {
            if (!Boolean.TRUE.equals(system.getAuditOnPush())
                    || !wanted.contains(normalizeRepo(system.getRepoUrl()))
                    || !system.effectiveScheduledBranch().equals(push.branch())) {
                continue;
            }
            if (requestService.requestIfIdle(system.getId(), push.branch(), source).isPresent()) {
                started++;
                LOG.infof("Auditoria disparada por push (%s) para o sistema %s, branch %s", source, system.getName(), push.branch());
            }
        }
        return started;
    }
}
