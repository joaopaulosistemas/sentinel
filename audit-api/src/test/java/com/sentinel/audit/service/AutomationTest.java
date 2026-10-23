package com.sentinel.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.AuditStatus;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.repository.AuditRunRepository;
import com.sentinel.audit.repository.AuditedSystemRepository;
import com.sentinel.audit.service.NotificationService.Notice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Webhook de push, agendamento e notificações — tudo sem rede nem banco. */
class AutomationTest {

    private static final ObjectMapper M = new ObjectMapper();

    private final List<AuditedSystem> systems = new ArrayList<>();
    private final List<AuditRun> runs = new ArrayList<>();
    private final List<String> requested = new ArrayList<>();   // "id|branch|origem"

    private AuditRequestService requestService;
    private WebhookService webhook;

    private AuditedSystem system(long id, String repo, Boolean onPush, Integer hours, String branch) {
        AuditedSystem s = new AuditedSystem();
        s.setId(id);
        s.setName("Sistema " + id);
        s.setRepoUrl(repo);
        s.setAuditOnPush(onPush);
        s.setAuditIntervalHours(hours);
        s.setScheduledBranch(branch);
        systems.add(s);
        return s;
    }

    private AuditRun run(AuditedSystem s, String branch, AuditStatus status, LocalDateTime started) {
        AuditRun r = new AuditRun();
        r.setId((long) (runs.size() + 1));
        r.setSystem(s);
        r.setBranch(branch);
        r.setStatus(status);
        r.setStartedAt(started);
        runs.add(r);
        return r;
    }

    @BeforeEach
    void setUp() {
        AuditedSystemRepository systemRepo = new AuditedSystemRepository() {
            @Override public List<AuditedSystem> listAll() { return systems; }
        };
        AuditRunRepository runRepo = new AuditRunRepository() {
            @Override public List<AuditRun> findActive() {
                return runs.stream().filter(r -> r.getStatus() == AuditStatus.PENDING || r.getStatus() == AuditStatus.RUNNING).toList();
            }
            @Override public List<AuditRun> findBySystemId(Long id) {
                return runs.stream().filter(r -> r.getSystem().getId().equals(id))
                        .sorted(Comparator.comparing(AuditRun::getStartedAt).reversed()).toList();
            }
        };
        // request() de verdade exigiria banco/CDI: aqui só registra o pedido; requestIfIdle() é o real
        requestService = new AuditRequestService() {
            @Override public AuditRun request(Long systemId, String branch, String by) {
                requested.add(systemId + "|" + branch + "|" + by);
                AuditRun r = new AuditRun();
                r.setSystem(systems.stream().filter(s -> s.getId().equals(systemId)).findFirst().get());
                r.setBranch(branch);
                r.setStatus(AuditStatus.PENDING);
                r.setStartedAt(LocalDateTime.now());
                runs.add(r);
                return r;
            }
        };
        requestService.systemRepository = systemRepo;
        requestService.auditRunRepository = runRepo;

        webhook = new WebhookService();
        webhook.systemRepository = systemRepo;
        webhook.requestService = requestService;
        webhook.secretBox = new SecretBox("chave-de-teste");
    }

    private static String hmac(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------ webhook: autenticidade

    @Test
    void assinaturaDoGithubPrecisaBater() throws Exception {
        String body = "{\"ref\":\"refs/heads/main\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        assertTrue(WebhookService.validGithubSignature("s3gredo", bytes, hmac("s3gredo", body)));
        assertFalse(WebhookService.validGithubSignature("s3gredo", bytes, hmac("outro", body)));
        assertFalse(WebhookService.validGithubSignature("s3gredo", (body + " ").getBytes(StandardCharsets.UTF_8), hmac("s3gredo", body)));
        assertFalse(WebhookService.validGithubSignature("s3gredo", bytes, null));
        assertFalse(WebhookService.validGithubSignature("s3gredo", bytes, "sha1=abc"));
        assertFalse(WebhookService.validGithubSignature("", bytes, "sha256=" + "0".repeat(64)));   // segredo vazio nunca valida
        assertFalse(WebhookService.validGithubSignature(null, bytes, hmac("s3gredo", body)));
    }

    @Test
    void tokenDoGitlabPrecisaBater() {
        assertTrue(WebhookService.validGitlabToken("abc", "abc"));
        assertFalse(WebhookService.validGitlabToken("abc", "abd"));
        assertFalse(WebhookService.validGitlabToken("abc", null));
        assertFalse(WebhookService.validGitlabToken("", ""));
    }

    // ------------------------------------------------------------------ webhook: payload

    @Test
    void interpretaPushDeBranchDoGithubEDoGitlab() throws Exception {
        WebhookService.Push gh = WebhookService.parsePush(M.readTree(
                "{\"ref\":\"refs/heads/feature/x\",\"after\":\"abc\",\"repository\":{\"clone_url\":\"https://github.com/Org/Repo.git\",\"html_url\":\"https://github.com/Org/Repo\"}}"));
        assertEquals("feature/x", gh.branch());
        assertEquals(2, gh.repoUrls().size());

        WebhookService.Push gl = WebhookService.parsePush(M.readTree(
                "{\"ref\":\"refs/heads/main\",\"project\":{\"git_http_url\":\"https://gitlab.com/org/repo.git\"}}"));
        assertEquals("main", gl.branch());
    }

    @Test
    void ignoraTagBranchApagadaEPayloadSemRepositorio() throws Exception {
        assertNull(WebhookService.parsePush(M.readTree("{\"ref\":\"refs/tags/v1\",\"repository\":{\"clone_url\":\"https://x/y.git\"}}")));
        assertNull(WebhookService.parsePush(M.readTree("{\"ref\":\"refs/heads/main\",\"deleted\":true,\"repository\":{\"clone_url\":\"https://x/y.git\"}}")));
        assertNull(WebhookService.parsePush(M.readTree("{\"ref\":\"refs/heads/main\",\"after\":\"0000000000000000000000000000000000000000\",\"repository\":{\"clone_url\":\"https://x/y.git\"}}")));
        assertNull(WebhookService.parsePush(M.readTree("{\"ref\":\"refs/heads/main\"}")));
        assertNull(WebhookService.parsePush(M.readTree("{}")));
    }

    @Test
    void normalizaFormasDiferentesDaMesmaUrl() {
        String esperado = "github.com/org/repo";
        for (String u : List.of("https://github.com/Org/Repo.git", "https://github.com/org/repo/", "http://github.com/ORG/repo",
                "https://user:tok@github.com/org/repo.git", "git@github.com:org/repo.git", "  https://GITHUB.com/org/repo  ")) {
            assertEquals(esperado, WebhookService.normalizeRepo(u), u);
        }
        assertNotEquals(WebhookService.normalizeRepo("https://github.com/org/repo2"), esperado);
    }

    // ------------------------------------------------------------------ webhook: disparo

    @Test
    void pushDisparaSoNoSistemaCertoNaBranchCertaECOmOptIn() {
        system(1, "https://github.com/org/repo.git", true, null, null);         // main, com push
        system(2, "https://github.com/org/repo.git", false, null, null);        // sem opt-in
        system(3, "https://github.com/org/repo.git", true, null, "develop");    // outra branch
        system(4, "https://github.com/org/outro.git", true, null, null);        // outro repositório

        int n = webhook.trigger(new WebhookService.Push(List.of("https://github.com/Org/Repo"), "main"), "webhook-github");

        assertEquals(1, n);
        assertEquals(List.of("1|main|webhook-github"), requested);
    }

    @Test
    void segredoEPorSistemaESoQuemTemSegredoRecebeWebhook() throws Exception {
        AuditedSystem a = system(1, "https://github.com/org/repo.git", true, null, null);
        a.setWebhookSecretEnc(webhook.secretBox.encrypt("segredo-do-a"));
        system(2, "https://github.com/org/repo.git", true, null, null);          // mesmo repositório, sem segredo
        system(3, "https://github.com/org/outro.git", true, null, null);

        List<AuditedSystem> withSecret = webhook.withSecret(List.of("https://github.com/Org/Repo"));
        assertEquals(List.of(a), withSecret);
        assertEquals("segredo-do-a", webhook.secretOf(a));
        assertTrue(WebhookService.validGithubSignature(webhook.secretOf(a), "{}".getBytes(StandardCharsets.UTF_8), hmac("segredo-do-a", "{}")));
        assertFalse(WebhookService.validGithubSignature(webhook.secretOf(a), "{}".getBytes(StandardCharsets.UTF_8), hmac("outro", "{}")));
        assertEquals(1, webhook.trigger(new WebhookService.Push(List.of("https://github.com/org/repo"), "main"), "webhook-github",
                java.util.Set.of(1L)));
        assertEquals(List.of("1|main|webhook-github"), requested);
    }

    @Test
    void pushNaoEmpilhaAuditoriaSeJaHaUmaEmAndamento() {
        AuditedSystem s = system(1, "https://github.com/org/repo.git", true, null, null);
        run(s, "main", AuditStatus.RUNNING, LocalDateTime.now().minusMinutes(5));

        assertEquals(0, webhook.trigger(new WebhookService.Push(List.of("https://github.com/org/repo"), "main"), "webhook-github"));
        assertTrue(requested.isEmpty());
    }

    @Test
    void auditoriaTravadaHaMuitoTempoNaoBloqueiaNovaAuditoria() {
        AuditedSystem s = system(1, "https://github.com/org/repo.git", true, null, null);
        run(s, "main", AuditStatus.RUNNING, LocalDateTime.now().minusHours(5));    // ficou presa

        assertEquals(1, webhook.trigger(new WebhookService.Push(List.of("https://github.com/org/repo"), "main"), "webhook-github"));
    }

    // ------------------------------------------------------------------ agendador

    @Test
    void venceOIntervaloDesdeAUltimaAuditoria() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 17, 12, 0);
        assertTrue(AuditScheduler.isDue(24, null, now));                                   // nunca auditado
        assertFalse(AuditScheduler.isDue(24, now.minusHours(23), now));
        assertTrue(AuditScheduler.isDue(24, now.minusHours(24), now));                     // no limite: já vence
        assertTrue(AuditScheduler.isDue(24, now.minusDays(3), now));
    }

    @Test
    void agendadorDisparaApenasOsVencidosEUsaABranchConfigurada() {
        LocalDateTime now = LocalDateTime.now();
        AuditedSystem due = system(1, "https://g/a.git", false, 24, "release");
        run(due, "release", AuditStatus.COMPLETED, now.minusHours(30));
        AuditedSystem recent = system(2, "https://g/b.git", false, 24, null);
        run(recent, "main", AuditStatus.COMPLETED, now.minusHours(2));
        system(3, "https://g/c.git", false, null, null);                                     // sem agendamento
        system(4, "https://g/d.git", false, 0, null);                                        // 0 = desligado
        AuditedSystem busy = system(5, "https://g/e.git", false, 1, null);
        run(busy, "main", AuditStatus.RUNNING, now.minusMinutes(10));                        // já rodando
        AuditedSystem never = system(6, "https://g/f.git", false, 12, null);                 // nunca auditado

        AuditScheduler scheduler = new AuditScheduler();
        scheduler.systemRepository = webhook.systemRepository;
        scheduler.auditRunRepository = requestService.auditRunRepository;
        scheduler.requestService = requestService;

        assertEquals(2, scheduler.runDue(now));
        assertEquals(List.of("1|release|scheduler", "6|main|scheduler"), requested);
    }

    @Test
    void falhaEmUmSistemaNaoImpedeOsDemais() {
        system(1, "https://g/a.git", false, 1, null);
        system(2, "https://g/b.git", false, 1, null);
        requestService = new AuditRequestService() {
            @Override public java.util.Optional<AuditRun> requestIfIdle(Long id, String b, String by) {
                if (id == 1L) throw new IllegalStateException("banco caiu");
                requested.add(id + "|" + b + "|" + by);
                return java.util.Optional.of(new AuditRun());
            }
        };
        AuditScheduler scheduler = new AuditScheduler();
        scheduler.systemRepository = webhook.systemRepository;
        scheduler.auditRunRepository = new AuditRunRepository() {
            @Override public List<AuditRun> findBySystemId(Long id) { return List.of(); }
        };
        scheduler.requestService = requestService;

        assertEquals(1, scheduler.runDue(LocalDateTime.now()));
        assertEquals(List.of("2|main|scheduler"), requested);
    }

    @Test
    void reinicioMarcaAuditoriasEmAndamentoComoFalhas() {
        AuditedSystem s = system(1, "https://g/a.git", false, null, null);
        AuditRun a = run(s, "main", AuditStatus.RUNNING, LocalDateTime.now().minusMinutes(3));
        AuditRun b = run(s, "main", AuditStatus.PENDING, LocalDateTime.now());
        AuditRun ok = run(s, "main", AuditStatus.COMPLETED, LocalDateTime.now().minusDays(1));

        AuditScheduler scheduler = new AuditScheduler();
        scheduler.auditRunRepository = requestService.auditRunRepository;

        assertEquals(2, scheduler.recoverInterruptedRuns());
        assertEquals(AuditStatus.FAILED, a.getStatus());
        assertEquals(AuditStatus.FAILED, b.getStatus());
        assertTrue(a.getErrorMessage().contains("reiniciada"));
        assertEquals(AuditStatus.COMPLETED, ok.getStatus());
    }

    @Test
    void comVariasReplicasSoRecuperaQuemNaoTemBatimento() {
        AuditedSystem s = system(1, "https://g/a.git", false, null, null);
        AuditRun viva = run(s, "main", AuditStatus.RUNNING, LocalDateTime.now().minusMinutes(10));
        AuditRun orfa = run(s, "main", AuditStatus.RUNNING, LocalDateTime.now().minusMinutes(10));
        AuditRun recem = run(s, "main", AuditStatus.PENDING, LocalDateTime.now());

        AuditScheduler scheduler = new AuditScheduler();
        scheduler.auditRunRepository = requestService.auditRunRepository;
        scheduler.progressStore = new ProgressStore() {
            @Override
            public java.util.Set<Long> aliveSince(LocalDateTime since) {
                return java.util.Set.of(viva.getId());
            }
        };

        assertEquals(1, scheduler.recoverInterruptedRuns());
        assertEquals(AuditStatus.RUNNING, viva.getStatus());
        assertEquals(AuditStatus.FAILED, orfa.getStatus());
        assertEquals(AuditStatus.PENDING, recem.getStatus());
    }

    // ------------------------------------------------------------------ notificação

    private static Notice completed(Double delta, boolean baseline, int newAtLeastMin) {
        return new Notice(9L, 1L, "Portal", "main", AuditStatus.COMPLETED, null, 78.5, delta, baseline,
                newAtLeastMin + 1, newAtLeastMin, 2, "SQL Injection");
    }

    @Test
    void soAvisaQuandoHaMotivo() {
        assertTrue(NotificationService.shouldNotify(
                new Notice(9L, 1L, "P", "main", AuditStatus.FAILED, "erro", null, null, false, 0, 0, 0, null), 5));
        assertFalse(NotificationService.shouldNotify(completed(-2.0, true, 0), 5));     // queda pequena, nada grave
        assertTrue(NotificationService.shouldNotify(completed(-5.0, true, 0), 5));      // queda no limite
        assertTrue(NotificationService.shouldNotify(completed(1.0, true, 1), 5));       // score subiu, mas surgiu algo grave
        assertFalse(NotificationService.shouldNotify(completed(null, false, 3), 5));    // primeira auditoria: não avisa
        assertFalse(NotificationService.shouldNotify(completed(3.0, true, 0), 5));
    }

    @Test
    void montaPayloadParaCadaCanal() throws Exception {
        Notice n = completed(-7.5, true, 2);

        JsonNode slack = M.readTree(NotificationService.buildPayload("slack", n, "https://audit.exemplo.com/"));
        assertTrue(slack.path("text").asText().contains("Portal"));
        assertTrue(slack.path("text").asText().contains("-7.5"));
        assertTrue(slack.path("text").asText().contains("https://audit.exemplo.com/systems/1"));   // sem barra duplicada

        JsonNode teams = M.readTree(NotificationService.buildPayload("TEAMS", n, "https://audit.exemplo.com"));
        assertEquals("message", teams.path("type").asText());
        JsonNode card = teams.path("attachments").get(0).path("content");
        assertEquals("AdaptiveCard", card.path("type").asText());
        assertEquals("https://audit.exemplo.com/systems/1", card.path("actions").get(0).path("url").asText());

        JsonNode generic = M.readTree(NotificationService.buildPayload("generic", n, null));
        assertEquals("audit.completed", generic.path("event").asText());
        assertEquals(2, generic.path("resolvedFindings").asInt());
        assertTrue(generic.path("url").isMissingNode());
    }

    @Test
    void mensagemDeFalhaTrazOMotivo() throws Exception {
        Notice failed = new Notice(9L, 1L, "Portal", "main", AuditStatus.FAILED, "IllegalStateException: repo não encontrado",
                null, null, false, 0, 0, 0, null);
        assertTrue(NotificationService.buildText(failed).contains("repo não encontrado"));
        assertTrue(NotificationService.title(failed).startsWith("Auditoria FALHOU"));
        assertEquals("audit.failed", M.readTree(NotificationService.buildPayload("generic", failed, null)).path("event").asText());
    }

    @Test
    void notifyFinishedNuncaLancaEEnviaSoQuandoConfigurado() {
        List<String> sent = new ArrayList<>();
        NotificationService.Settings[] settings = {null};
        NotificationService svc = new NotificationService() {
            @Override Settings settingsFor(Long id) { return settings[0]; }
            @Override Notice loadNotice(Long id, String minSeverity) {
                return new Notice(id, 1L, "Portal", "main", AuditStatus.FAILED, "x", null, null, false, 0, 0, 0, null);
            }
            @Override protected void send(String url, String json) { sent.add(url); }
        };

        svc.notifyFinished(9L);
        assertTrue(sent.isEmpty());                                   // sistema sem URL: não faz nada

        settings[0] = new NotificationService.Settings("https://8.8.8.8/abc", "slack", 5, "HIGH", null);
        svc.notifyFinished(9L);
        assertEquals(List.of("https://8.8.8.8/abc"), sent);

        settings[0] = new NotificationService.Settings("https://127.0.0.1/abc", "slack", 5, "HIGH", null);
        sent.clear();
        svc.notifyFinished(9L);
        assertTrue(sent.isEmpty(), "destino local é recusado");

        NotificationService quebrado = new NotificationService() {
            @Override Settings settingsFor(Long id) { return new Settings("https://8.8.8.8/abc", "slack", 5, "HIGH", null); }
            @Override protected void send(String url, String json) throws java.io.IOException { throw new java.io.IOException("500"); }
            @Override Notice loadNotice(Long id, String minSeverity) {
                return new Notice(id, 1L, "P", "main", AuditStatus.FAILED, "x", null, null, false, 0, 0, 0, null);
            }
        };
        quebrado.notifyFinished(9L);                                  // não lança
    }

    @Test
    void destinoDaNotificacaoSoAceitaHttpsPublico() {
        assertDoesNotThrow(() -> NotificationService.validateTarget("https://8.8.8.8/hook"));
        for (String bad : List.of("http://8.8.8.8/hook", "https://localhost/x", "https://127.0.0.1/x", "https://10.0.0.5/x",
                "https://192.168.1.2/x", "https://169.254.169.254/latest", "https://[::1]/x", "https://u:p@8.8.8.8/x", "ftp://8.8.8.8/x")) {
            assertThrows(IllegalArgumentException.class, () -> NotificationService.validateTarget(bad), bad);
        }
    }
}
