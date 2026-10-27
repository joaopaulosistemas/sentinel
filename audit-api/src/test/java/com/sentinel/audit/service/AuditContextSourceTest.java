package com.sentinel.audit.service;

import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.model.SourceType;
import com.sentinel.audit.model.Stack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Como a origem aparece nas mensagens de andamento e qual caminho a auditoria segue. */
class AuditContextSourceTest {

    private static AuditOrchestrationService.AuditContext context(SourceType type, String url, String path) {
        return new AuditOrchestrationService.AuditContext(url, "main", Stack.JAVA, "api", "default", null, null, null, null, 1L,
                type, path, LocalSnapshot.COMMIT);
    }

    @Test
    void gitMostraAUrl() {
        AuditOrchestrationService.AuditContext c = context(SourceType.GIT_URL, "https://github.com/org/api", null);
        assertFalse(c.local());
        assertEquals("https://github.com/org/api", c.sourceLabel());
    }

    @Test
    void pastaLocalMostraAPasta() {
        AuditOrchestrationService.AuditContext c = context(SourceType.LOCAL_PATH, null, "projetos/api");
        assertTrue(c.local());
        assertEquals("pasta local projetos/api", c.sourceLabel());
    }
}
