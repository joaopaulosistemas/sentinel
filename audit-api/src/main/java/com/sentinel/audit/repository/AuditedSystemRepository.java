package com.sentinel.audit.repository;

import com.sentinel.audit.model.AuditedSystem;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.Locale;

/**
 * Repositório Panache para a entidade {@link AuditedSystem}.
 */
@ApplicationScoped
public class AuditedSystemRepository implements PanacheRepository<AuditedSystem> {

    /**
     * Pré-filtro no banco para o webhook: só sistemas cujo endereço contém o nome do repositório de algum dos
     * endereços recebidos. A comparação exata (esquema, credencial, ".git") continua em Java; isto só evita
     * carregar todos os sistemas a cada push.
     *
     * @param normalizedRepos endereços já normalizados (minúsculos, sem esquema), p. ex. "github.com/org/repo"
     */
    public List<AuditedSystem> candidatesByRepo(List<String> normalizedRepos) {
        List<String> names = normalizedRepos.stream().map(AuditedSystemRepository::repoName)
                .filter(n -> !n.isEmpty()).distinct().toList();
        if (names.isEmpty()) {
            return List.of();
        }
        StringBuilder where = new StringBuilder();
        Object[] params = new Object[names.size()];
        for (int i = 0; i < names.size(); i++) {
            where.append(i == 0 ? "" : " or ").append("lower(repoUrl) like ?").append(i + 1);
            params[i] = "%" + names.get(i).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        }
        return list(where.toString(), params);
    }

    /**
     * Busca o sistema travando a linha até o fim da transação: duas requisições de auditoria para o mesmo sistema
     * (mesmo em instâncias diferentes da API) passam uma de cada vez pela checagem "já tem auditoria em andamento".
     */
    public Optional<AuditedSystem> lockById(Long id) {
        return Optional.ofNullable(findById(id, LockModeType.PESSIMISTIC_WRITE));
    }

    /** Último trecho do caminho, sem ".git": "github.com/org/repo.git" vira "repo". */
    static String repoName(String normalized) {
        String n = normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.endsWith(".git") ? n.substring(0, n.length() - 4) : n;
    }
}
