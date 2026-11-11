package com.sentinel.audit.mcp;

/**
 * Decide o que fazer com uma chamada ao MCP depois de conferir a chave. Quem apresenta a chave certa sempre passa:
 * as chaves são aleatórias e longas, então adivinhar uma não é viável, e travar também o cliente legítimo deixaria
 * qualquer pessoa derrubar o MCP de um sistema só errando a chave. O limite freia somente as tentativas erradas.
 */
final class McpAuthGuard {

    enum Verdict { ALLOWED, UNAUTHORIZED, TOO_MANY }

    private final McpRateLimiter failures;

    McpAuthGuard(McpRateLimiter failures) {
        this.failures = failures;
    }

    /** @param ref nome ou id informado na URL; @param keyValid resultado da conferência da chave */
    Verdict check(String ref, boolean keyValid) {
        if (keyValid) {
            return Verdict.ALLOWED;
        }
        String bucket = "fail:" + ref;
        if (failures.blocked(bucket)) {
            return Verdict.TOO_MANY;
        }
        failures.tryAcquire(bucket);
        return Verdict.UNAUTHORIZED;
    }

    int retryAfterSeconds(String ref) {
        return failures.retryAfterSeconds("fail:" + ref);
    }
}
