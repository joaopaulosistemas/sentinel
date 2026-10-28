package com.sentinel.audit.pack;

import com.sentinel.audit.service.FindingDeduplicator;
import com.sentinel.audit.service.RemediationCatalog;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Descobre, valida e mantém os pacotes de regras e os perfis. Fontes, em ordem (a posterior vence com a mesma versão):
 * pastas embutidas ({@code audit-rules/packs}), pastas extras ({@code sentinel.audit.packs.dirs}) e um repositório Git
 * ({@code sentinel.audit.packs.git-url}). O que for inválido é <b>rejeitado com o motivo</b> (ver {@link Snapshot#rejected()})
 * e nunca derruba a API nem os pacotes bons.
 */
@ApplicationScoped
public class PackRegistry {

    private static final Logger LOG = Logger.getLogger(PackRegistry.class);

    static final List<String> BUILTIN_DIRS = List.of("../audit-rules/packs", "audit-rules/packs", "/app/audit-rules/packs");

    @ConfigProperty(name = "sentinel.audit.packs.dirs")
    Optional<String> extraDirs = Optional.empty();

    @ConfigProperty(name = "sentinel.audit.packs.git-url")
    Optional<String> gitUrl = Optional.empty();

    @ConfigProperty(name = "sentinel.audit.packs.git-ref", defaultValue = "main")
    String gitRef = "main";

    @ConfigProperty(name = "sentinel.audit.packs.git-path")
    Optional<String> gitPath = Optional.empty();

    @ConfigProperty(name = "sentinel.audit.packs.cache-dir", defaultValue = "/tmp/sentinel-packs")
    String cacheDir = "/tmp/sentinel-packs";

    /** Chave pública Ed25519 (Base64, X.509) usada para conferir {@code pack.sig}. */
    @ConfigProperty(name = "sentinel.audit.packs.public-key")
    Optional<String> publicKey = Optional.empty();

    /** Quando ligado, só carrega pacote com assinatura válida (exige a chave pública). */
    @ConfigProperty(name = "sentinel.audit.packs.require-signature", defaultValue = "false")
    boolean requireSignature = false;

    /** Ids de pacotes embutidos cujas ferramentas de linha de comando rodam mesmo com {@code allow-tools} desligado. */
    @ConfigProperty(name = "sentinel.audit.packs.trusted-tool-packs")
    Optional<String> trustedToolPacks = Optional.empty();

    @ConfigProperty(name = "sentinel.audit.packs.disabled")
    Optional<String> disabled = Optional.empty();

    /** Quando preenchido, são as <b>únicas</b> raízes (sem pastas embutidas nem Git): uso em testes e na ferramenta de linha de comando. */
    private List<Path> fixedRoots;

    /** Registro independente do CDI, lendo só as raízes informadas. */
    public static PackRegistry standalone(Path... roots) {
        PackRegistry r = new PackRegistry();
        r.fixedRoots = List.of(roots);
        return r;
    }

    /** Só para testes: permite {@code file://} na URL do Git. */
    boolean allowFileUrl = false;

    /** Visão consistente (imutável) de tudo que foi carregado. */
    public record Snapshot(List<RulePack> packs, Map<String, Profile> profiles, Map<String, List<String>> rejected,
                           List<String> warnings, List<String> roots, Instant loadedAt,
                           Map<String, String> integrity) {}

    private volatile Snapshot snapshot;

    void onStart(@Observes StartupEvent event) {
        reload();
    }

    /** Atualiza do Git (se configurado) a cada intervalo; sem {@code git-url} não faz nada. */
    @Scheduled(every = "${sentinel.audit.packs.refresh-interval:10m}", delayed = "2m",
            identity = "sentinel-packs-refresh", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void refresh() {
        if (gitUrl.isPresent() && !gitUrl.get().isBlank()) {
            reload();
        }
    }

    public Snapshot snapshot() {
        Snapshot s = snapshot;
        return s != null ? s : reload();
    }

    public synchronized Snapshot reload() {
        List<String> warnings = new ArrayList<>();
        Map<String, List<String>> rejected = new LinkedHashMap<>();
        List<Path> roots = collectRoots(warnings);

        java.security.PublicKey key = configuredKey(warnings);
        if (requireSignature && key == null) {
            warnings.add("require-signature ligado sem public-key válida: nenhum pacote será carregado");
        }
        Map<String, String> integrity = new LinkedHashMap<>();
        Map<String, RulePack> byId = new LinkedHashMap<>();
        Map<String, Profile> profiles = new LinkedHashMap<>();
        Set<String> off = disabledIds();

        for (Path root : roots) {
            PackLoader.Discovery d = PackLoader.discover(root);
            d.rejected().forEach((dir, errs) -> rejected.put(root.getFileName() + "/" + dir, errs));
            warnings.addAll(d.warnings());
            for (RulePack p : d.packs()) {
                acceptPack(p, root, key, byId, integrity, rejected, warnings);
            }
            profiles.putAll(ProfileLoader.loadAll(root, rejected));
        }
        for (String id : off) {
            if (byId.remove(id) != null) {
                warnings.add("pacote '" + id + "' desabilitado por configuração");
            }
        }
        validateProfiles(profiles, byId, rejected, warnings);

        List<RulePack> packs = List.copyOf(byId.values());
        RemediationCatalog.install(packs.stream().flatMap(p -> p.remediation().stream()).toList());
        FindingDeduplicator.install(packs.stream().flatMap(p -> p.dedup().stream()).toList());

        Snapshot s = new Snapshot(packs, Map.copyOf(profiles), Map.copyOf(rejected), List.copyOf(warnings),
                roots.stream().map(Path::toString).toList(), Instant.now(), Map.copyOf(integrity));
        snapshot = s;
        LOG.infof("Pacotes carregados: %d (%s); perfis: %d; rejeitados: %d",
                packs.size(), packs.stream().map(RulePack::id).toList(), profiles.size(), rejected.size());
        rejected.forEach((k, v) -> LOG.warnf("Pacote/perfil rejeitado %s: %s", k, v));
        return s;
    }

    /** Pastas de pacotes, sem repetição: as fixas (testes) ou as embutidas, as configuradas e o checkout do Git. */
    private List<Path> collectRoots(List<String> warnings) {
        List<Path> roots = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();
        if (fixedRoots != null) {
            for (Path r : fixedRoots) {
                addRoot(roots, seen, r.toAbsolutePath().normalize());
            }
            return roots;
        }
        for (String c : BUILTIN_DIRS) {
            addRoot(roots, seen, Paths.get(c).toAbsolutePath().normalize());
        }
        extraDirs.ifPresent(v -> addConfiguredDirs(v, roots, seen, warnings));
        if (gitUrl.isPresent() && !gitUrl.get().isBlank()) {
            Path root = gitRoot(warnings);
            if (root != null) {
                roots.add(root);
            }
        }
        return roots;
    }

    private static void addRoot(List<Path> roots, Set<Path> seen, Path dir) {
        if (Files.isDirectory(dir) && seen.add(dir)) {
            roots.add(dir);
        }
    }

    private static void addConfiguredDirs(String csv, List<Path> roots, Set<Path> seen, List<String> warnings) {
        for (String c : csv.split(",")) {
            if (c.isBlank()) {
                continue;
            }
            Path d = Paths.get(c.trim()).toAbsolutePath().normalize();
            if (!Files.isDirectory(d)) {
                warnings.add("pasta de pacotes não encontrada: " + d);
            } else if (seen.add(d)) {
                roots.add(d);
            }
        }
    }

    /** Raiz dos pacotes no repositório Git configurado; {@code null} (com aviso) se não deu para obtê-la. */
    private Path gitRoot(List<String> warnings) {
        try {
            Path checkout = PackSources.sync(gitUrl.get().trim(), gitRef, Paths.get(cacheDir), 180, allowFileUrl);
            Path root = gitPath.filter(p -> !p.isBlank()).map(p -> checkout.resolve(p.trim()).normalize()).orElse(checkout);
            if (!root.startsWith(checkout) || !Files.isDirectory(root)) {
                warnings.add("git-path inválido ou inexistente no repositório de pacotes: " + gitPath.orElse(""));
                return null;
            }
            return root;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // falha de rede não derruba: segue com o que já havia em cache/embutido
            warnings.add("não foi possível atualizar os pacotes do Git: " + e.getMessage());
            LOG.warnf("Pacotes do Git indisponíveis: %s", e.getMessage());
            return null;
        }
    }

    private java.security.PublicKey configuredKey(List<String> warnings) {
        if (publicKey.isEmpty() || publicKey.get().isBlank()) {
            return null;
        }
        try {
            return PackIntegrity.publicKey(publicKey.get());
        } catch (Exception e) {
            warnings.add("sentinel.audit.packs.public-key inválida: assinaturas não serão conferidas");
            return null;
        }
    }

    private Set<String> disabledIds() {
        Set<String> off = new LinkedHashSet<>();
        disabled.ifPresent(v -> {
            for (String id : v.split(",")) {
                if (!id.isBlank()) {
                    off.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
        });
        return off;
    }

    /** Confere integridade e assinatura e fica com a versão mais nova quando o mesmo pacote aparece em mais de uma pasta. */
    private void acceptPack(RulePack p, Path root, java.security.PublicKey key, Map<String, RulePack> byId,
                            Map<String, String> integrity, Map<String, List<String>> rejected, List<String> warnings) {
        PackIntegrity.Result integ = PackIntegrity.verify(p.dir(), key);
        String where = root.getFileName() + "/" + p.dir().getFileName();
        if (integ.state() == PackIntegrity.State.INVALID) {
            rejected.put(where, List.of("integridade: " + integ.detail()));
            return;
        }
        if (requireSignature && integ.state() != PackIntegrity.State.SIGNED) {
            rejected.put(where, List.of("assinatura obrigatória: pacote " + (integ.state() == PackIntegrity.State.UNSIGNED
                    ? "sem pack.lock" : "sem assinatura válida para a chave configurada")));
            return;
        }
        RulePack prev = byId.get(p.id());
        if (prev != null) {
            int cmp = Versions.compare(Versions.parse(p.manifest().version()), Versions.parse(prev.manifest().version()));
            if (cmp < 0) {
                warnings.add("pacote '" + p.id() + "' v" + p.manifest().version() + " ignorado: já há a v"
                        + prev.manifest().version());
                return;
            }
            warnings.add("pacote '" + p.id() + "': v" + p.manifest().version() + " (" + root + ") substitui a v"
                    + prev.manifest().version());
        }
        byId.put(p.id(), p);
        integrity.put(p.id(), integ.state().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    /** Remove perfis com herança quebrada ou circular; pacote desconhecido num perfil vira aviso. */
    private static void validateProfiles(Map<String, Profile> profiles, Map<String, RulePack> packs,
                                         Map<String, List<String>> rejected, List<String> warnings) {
        for (Profile p : new ArrayList<>(profiles.values())) {
            Set<String> chain = new LinkedHashSet<>();
            Profile cur = p;
            while (cur != null && cur.extendsId() != null) {
                if (!chain.add(cur.id())) {
                    break;
                }
                Profile parent = profiles.get(cur.extendsId().toLowerCase(Locale.ROOT));
                if (parent == null) {
                    rejected.put("profiles/" + p.id(), List.of("estende o perfil '" + cur.extendsId() + "', que não existe"));
                    profiles.remove(p.id());
                    cur = null;
                    break;
                }
                if (chain.contains(parent.id())) {
                    rejected.put("profiles/" + p.id(), List.of("herança circular: " + String.join(" -> ", chain) + " -> " + parent.id()));
                    profiles.remove(p.id());
                    cur = null;
                    break;
                }
                cur = parent;
            }
            if (profiles.containsKey(p.id())) {
                List<String> mentioned = new ArrayList<>(p.disablePacks());
                if (p.packs() != null) {
                    mentioned.addAll(p.packs());
                }
                for (String id : mentioned) {
                    if (!packs.containsKey(id)) {
                        warnings.add("perfil '" + p.id() + "' cita o pacote '" + id + "', que não está carregado");
                    }
                }
            }
        }
    }

    public Optional<RulePack> pack(String id) {
        return snapshot().packs().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /**
     * Política efetiva para o perfil ({@code null}/vazio = {@code default}). Perfil inexistente lança
     * {@link IllegalArgumentException}; o perfil {@code default} sempre existe (todos os pacotes habilitados por padrão),
     * a menos que um arquivo de perfil o defina.
     */
    public AuditPolicy policyFor(String profileId) {
        Snapshot s = snapshot();
        return policyFor(profileId, s).withTrustedToolPacks(trustedBuiltinPackIds(s));
    }

    /** Política de um sistema: a do perfil com os ajustes do próprio sistema por cima. */
    public AuditPolicy policyFor(String profileId, SystemTuning tuning) {
        return policyFor(profileId).withTuning(tuning, snapshot().packs());
    }

    /** Confiança só vale para pacote embutido (pasta do produto): um pacote externo com o mesmo id não herda. */
    Set<String> trustedBuiltinPackIds(Snapshot s) {
        Set<String> wanted = new LinkedHashSet<>();
        trustedToolPacks.ifPresent(v -> {
            for (String id : v.split(",")) {
                if (!id.isBlank()) {
                    wanted.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
        });
        Set<Path> builtin = new LinkedHashSet<>();
        for (String c : BUILTIN_DIRS) {
            builtin.add(Paths.get(c).toAbsolutePath().normalize());
        }
        if (fixedRoots != null) {
            fixedRoots.forEach(r -> builtin.add(r.toAbsolutePath().normalize()));
        }
        Set<String> out = new LinkedHashSet<>();
        for (RulePack p : s.packs()) {
            Path parent = p.dir().toAbsolutePath().normalize().getParent();
            if (wanted.contains(p.id()) && parent != null && builtin.contains(parent)) {
                out.add(p.id());
            }
        }
        return out;
    }

    private AuditPolicy policyFor(String profileId, Snapshot s) {
        String id = profileId == null || profileId.isBlank() ? "default" : profileId.trim().toLowerCase(Locale.ROOT);
        Profile profile = s.profiles().get(id);
        if (profile == null) {
            if ("default".equals(id)) {
                return AuditPolicy.defaults(s.packs().stream().filter(p -> p.manifest().enabledByDefault()).toList());
            }
            throw new IllegalArgumentException("Perfil não encontrado: " + id);
        }
        List<Profile> chain = new ArrayList<>();
        for (Profile cur = profile; cur != null; cur = cur.extendsId() == null ? null : s.profiles().get(cur.extendsId().toLowerCase(Locale.ROOT))) {
            chain.add(0, cur);                       // do ancestral para o próprio perfil
        }
        Set<String> selected = null;
        Map<String, RuleOverride> overrides = new LinkedHashMap<>();
        Map<String, Integer> thresholds = new LinkedHashMap<>();
        for (Profile p : chain) {
            if (p.packs() != null) {
                selected = new LinkedHashSet<>(p.packs());
            } else if (selected == null) {
                selected = new LinkedHashSet<>();
                for (RulePack x : s.packs()) {
                    if (x.manifest().enabledByDefault()) {
                        selected.add(x.id());
                    }
                }
            }
            selected.removeAll(p.disablePacks());
            overrides.putAll(p.rules());
            thresholds.putAll(p.thresholds());
        }
        final Set<String> active = selected;
        List<RulePack> packs = s.packs().stream().filter(p -> active.contains(p.id())).toList();
        return new AuditPolicy(profile.id(), packs, overrides, thresholds);
    }

    public boolean profileExists(String id) {
        if (id == null || id.isBlank()) {
            return true;
        }
        String k = id.trim().toLowerCase(Locale.ROOT);
        return "default".equals(k) || snapshot().profiles().containsKey(k);
    }
}
