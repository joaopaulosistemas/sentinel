package com.sentinel.audit.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiKeyFilterPathTest {

    @Test
    void saudeWebhookEMcpSaoAbertos() {
        assertTrue(ApiKeyFilter.isOpenPath("api/v1/health"));
        assertTrue(ApiKeyFilter.isOpenPath("/api/v1/health/ready"));
        assertTrue(ApiKeyFilter.isOpenPath("api/v1/webhooks/github"));
        assertTrue(ApiKeyFilter.isOpenPath("mcp/meu-sistema"));
    }

    @Test
    void prefixoSemelhanteNaoAbreAcesso() {
        assertFalse(ApiKeyFilter.isOpenPath("api/v1/healthcheck-interno"));
        assertFalse(ApiKeyFilter.isOpenPath("api/v1/health-admin"));
        assertFalse(ApiKeyFilter.isOpenPath("api/v1/audits"));
        assertFalse(ApiKeyFilter.isOpenPath(null));
    }
}
