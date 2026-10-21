package com.sentinel.audit.service;

import com.sentinel.audit.dto.RemediationDto;
import com.sentinel.audit.model.Finding;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RemediationCatalogTest {

    @org.junit.jupiter.api.BeforeEach
    void carregaPacotes() {
        com.sentinel.audit.pack.TestPacks.registry();
    }

    private static Finding rule(String id) {
        Finding f = new Finding();
        f.setRuleId(id);
        return f;
    }

    /** Raiz do repositório vista a partir do módulo audit-api (onde o Maven roda os testes). */
    private static Path rulesDir() {
        return Path.of("..", "audit-rules", "packs", "core-quality", "rules");
    }

    @Test
    void todaRegraDeQualidadeDoSemgrepTemOrientacaoDeCorrecao() throws IOException {
        Path dir = rulesDir();
        assertTrue(Files.isDirectory(dir), "diretório de regras não encontrado: " + dir.toAbsolutePath());
        Pattern id = Pattern.compile("^\\s*-\\s*id:\\s*(\\S+)", Pattern.MULTILINE);
        List<String> semOrientacao = new ArrayList<>();
        int total = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path y : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                Matcher m = id.matcher(Files.readString(y));
                while (m.find()) {
                    total++;
                    RemediationDto r = RemediationCatalog.forFinding(rule("audit-rules.packs.core-quality.rules." + m.group(1)));
                    if (r == null || r.summary() == null || r.summary().isBlank() || r.after() == null) {
                        semOrientacao.add(m.group(1));
                    }
                }
            }
        }
        assertTrue(total >= 30, "esperava ler as regras dos YAMLs, leu " + total);
        assertEquals(List.of(), semOrientacao, "regras sem orientação no RemediationCatalog");
    }

    @Test
    void regrasDeCleanCodeTemOrientacao() {
        for (String r : List.of("sentinel-cc-funcao-longa", "sentinel-cc-muitos-parametros",
                "sentinel-cc-complexidade", "sentinel-cc-aninhamento")) {
            RemediationDto d = RemediationCatalog.forFinding(rule(r));
            assertNotNull(d, r);
            assertFalse(d.summary().isBlank(), r);
        }
    }

    @Test
    void regraDeLintDeFerramentaExternaNaoGanhaOrientacaoInventada() {
        assertNull(RemediationCatalog.forFinding(rule("pmd-EmptyCatchBlock")));
        assertNull(RemediationCatalog.forFinding(rule("eslint-no-var")));
    }
}
