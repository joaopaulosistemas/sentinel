package com.sentinel.audit.review;

import com.sentinel.audit.pack.AuditPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Leva as regras locais do sistema (em observação ou promovidas) para uma pasta temporária que o Semgrep lê.
 * Regra em observação entra em modo sombra: aparece no resultado e não pesa na nota. Qualquer falha aqui
 * deixa a auditoria seguir sem as regras locais.
 */
@ApplicationScoped
public class LocalRuleMaterializer {

    private static final Logger LOG = Logger.getLogger(LocalRuleMaterializer.class);

    @Inject
    ReviewService review;

    /** Política com as regras locais; a pasta criada deve ser apagada com {@link #cleanup(AuditPolicy)}. */
    public AuditPolicy apply(AuditPolicy policy, Long systemId) {
        if (policy == null || systemId == null || review == null) {
            return policy;
        }
        try {
            List<RuleProposal> rules = review.runningRules(systemId).stream()
                    .filter(r -> RuleDraft.validate(r.getSlug(), r.getSemgrepYaml()).isEmpty())
                    .toList();
            if (rules.isEmpty()) {
                return policy;
            }
            Path dir = Files.createTempDirectory("sentinel-local-rules-");
            for (RuleProposal r : rules) {
                Files.writeString(dir.resolve(r.getSlug() + ".yml"), r.getSemgrepYaml(), StandardCharsets.UTF_8);
            }
            Set<String> shadow = rules.stream().filter(r -> r.getStage() == RuleStage.OBSERVING)
                    .map(r -> RuleDraft.semgrepId(r.getSlug())).collect(Collectors.toSet());
            LOG.infof("Sistema %d: %d regra(s) local(is), %d em observação", systemId, rules.size(), shadow.size());
            return policy.withLocalRules(dir, shadow);
        } catch (IOException | RuntimeException e) {
            LOG.warnf("Regras locais do sistema %d ignoradas: %s", systemId, e.getMessage());
            return policy;
        }
    }

    public void cleanup(AuditPolicy policy) {
        Path dir = policy == null ? null : policy.localRulesDir();
        if (dir == null) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException | RuntimeException e) {
            LOG.debugf("Não foi possível apagar %s", dir);
        }
    }
}
