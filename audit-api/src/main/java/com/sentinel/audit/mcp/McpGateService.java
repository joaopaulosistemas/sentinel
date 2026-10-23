package com.sentinel.audit.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Portão do MCP: guarda a chave de cada sistema (só o hash), diz quais ferramentas estão liberadas e registra
 * cada chamada. Quem passa por aqui já é um cliente com a chave certa do sistema certo.
 */
@ApplicationScoped
public class McpGateService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "snt_";

    @Inject
    McpAccessRepository accessRepository;

    @Inject
    McpCallLogRepository logRepository;

    /** Chave recém-criada: o texto completo só existe neste retorno. */
    public record IssuedKey(String key, String prefix) {}

    /** Chave nova, longa e aleatória. */
    static String newKey() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    static String hash(String key) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public McpAccess find(Long systemId) {
        return accessRepository.findBySystemId(systemId);
    }

    /** A chave apresentada vale para este acesso? Só se estiver ligado e tiver chave. */
    public boolean accepts(McpAccess access, String presentedKey) {
        if (access == null || !access.isEnabled() || access.getKeyHash() == null || presentedKey == null || presentedKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(presentedKey.trim()).getBytes(StandardCharsets.UTF_8),
                access.getKeyHash().getBytes(StandardCharsets.UTF_8));
    }

    /** Gera uma chave nova (a anterior deixa de valer). O acesso continua como estava: ligar é outro passo. */
    @Transactional
    public IssuedKey issueKey(Long systemId) {
        McpAccess access = accessFor(systemId);
        String key = newKey();
        access.setKeyHash(hash(key));
        access.setKeyPrefix(key.substring(0, KEY_PREFIX.length() + 6));
        access.setKeyCreatedAt(LocalDateTime.now());
        return new IssuedKey(key, access.getKeyPrefix());
    }

    /** Apaga a chave e desliga o acesso. */
    @Transactional
    public void revokeKey(Long systemId) {
        McpAccess access = accessRepository.findBySystemId(systemId);
        if (access != null) {
            access.setKeyHash(null);
            access.setKeyPrefix(null);
            access.setKeyCreatedAt(null);
            access.setEnabled(false);
        }
    }

    /** Ferramentas liberadas: as escolhidas, ou todas as de leitura se nada foi escolhido. */
    public List<McpTool> enabledTools(McpAccess access) {
        if (access == null || access.getTools() == null || access.getTools().isBlank()) {
            return Arrays.stream(McpTool.values()).filter(t -> !t.writes()).toList();
        }
        Set<String> chosen = Arrays.stream(access.getTools().split(",")).map(String::trim).collect(Collectors.toSet());
        // As que gravam só entram quando a pessoa as escolheu de propósito.
        return Arrays.stream(McpTool.values()).filter(t -> chosen.contains(t.wire())).toList();
    }

    /**
     * Guarda o que a pessoa escolheu. Campos nulos não mudam.
     *
     * @throws IllegalArgumentException nome de ferramenta que não existe, ou ligar sem ter gerado a chave
     */
    @Transactional
    public McpAccess update(Long systemId, Boolean enabled, DataLevel level, List<String> tools) {
        McpAccess access = accessFor(systemId);
        if (tools != null) {
            Set<String> names = new LinkedHashSet<>();
            for (String name : tools) {
                McpTool tool = McpTool.byWire(name).orElseThrow(() -> new IllegalArgumentException("Ferramenta desconhecida: " + name));
                names.add(tool.wire());
            }
            access.setTools(String.join(",", names));
        }
        if (level != null) {
            access.setDataLevel(level);
        }
        if (enabled != null) {
            if (enabled && access.getKeyHash() == null) {
                throw new IllegalArgumentException("Gere uma chave antes de ligar o acesso.");
            }
            access.setEnabled(enabled);
        }
        return access;
    }

    @Transactional
    public void log(Long systemId, String tool, boolean ok, long durationMs, String detail) {
        McpCallLog entry = new McpCallLog();
        entry.setSystemId(systemId);
        entry.setTool(tool);
        entry.setCalledAt(LocalDateTime.now());
        entry.setOk(ok);
        entry.setDurationMs(durationMs);
        entry.setDetail(detail == null ? null : detail.substring(0, Math.min(detail.length(), 300)));
        logRepository.persist(entry);
    }

    public List<McpCallLog> recentCalls(Long systemId, int limit) {
        return logRepository.recent(systemId, Math.max(1, Math.min(limit, 200)));
    }

    /** Remove acesso e registro de um sistema que está sendo apagado. */
    @Transactional
    public void purge(Long systemId) {
        logRepository.deleteBySystemId(systemId);
        McpAccess access = accessRepository.findBySystemId(systemId);
        if (access != null) {
            accessRepository.delete(access);
        }
    }

    private McpAccess accessFor(Long systemId) {
        McpAccess access = accessRepository.findBySystemId(systemId);
        if (access == null) {
            access = new McpAccess();
            access.setSystemId(systemId);
            accessRepository.persist(access);
        }
        return access;
    }
}
