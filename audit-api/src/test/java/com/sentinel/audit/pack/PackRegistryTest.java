package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static com.sentinel.audit.pack.PackFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PackRegistryTest {

    private static List<String> ids(AuditPolicy p) {
        return p.packs().stream().map(RulePack::id).sorted().collect(Collectors.toList());
    }

    @Test
    void mesmoIdFicaComAMaiorVersao() throws Exception {
        Path a = tempRoot();
        Path b = tempRoot();
        pack(a, "meu", "1.0.0", true);
        pack(b, "meu", "1.10.0", true);

        PackRegistry r = PackRegistry.standalone(a, b);
        r.reload();
        assertEquals("1.10.0", r.pack("meu").get().manifest().version(), "1.10 > 1.9: comparação numérica");

        PackRegistry inv = PackRegistry.standalone(b, a);
        inv.reload();
        assertEquals("1.10.0", inv.pack("meu").get().manifest().version(), "ordem das raízes não importa");
        assertTrue(inv.snapshot().warnings().stream().anyMatch(w -> w.contains("ignorado")));
    }

    @Test
    void pacotesPadraoEDesabilitados() throws Exception {
        Path root = tempRoot();
        pack(root, "ligado", "1.0.0", true);
        pack(root, "opcional", "1.0.0", false);
        PackRegistry r = PackRegistry.standalone(root);
        r.reload();

        assertEquals(List.of("ligado"), ids(r.policyFor(null)), "só enabled-by-default no perfil default");
        assertEquals(2, r.snapshot().packs().size());

        r.disabled = java.util.Optional.of("ligado");
        r.reload();
        assertTrue(r.pack("ligado").isEmpty());
        assertEquals(List.of(), ids(r.policyFor("default")));
    }

    @Test
    void perfilEscolhePacotesHerdaESobrescreve() throws Exception {
        Path root = tempRoot();
        pack(root, "ligado", "1.0.0", true);
        pack(root, "opcional", "1.0.0", false);
        profile(root, "base", """
                id: base
                packs: [ligado, opcional]
                rules:
                  regra-a: {severity: low}
                  regra-b: {disabled: true}
                thresholds: {max-function-lines: 40, max-parameters: 4}
                """);
        profile(root, "filho", """
                id: filho
                extends: base
                disable-packs: [ligado]
                rules:
                  regra-a: {severity: critical}
                thresholds: {max-parameters: 6}
                """);
        PackRegistry r = PackRegistry.standalone(root);
        r.reload();

        AuditPolicy p = r.policyFor("FILHO");
        assertEquals("filho", p.profileId());
        assertEquals(List.of("opcional"), ids(p));
        assertEquals(com.sentinel.audit.model.Severity.CRITICAL, p.overrideFor("regra-a").severity(), "filho vence o pai");
        assertTrue(p.overrideFor("regra-b").disabled(), "herdado do pai");
        assertEquals(40, p.threshold("max-function-lines", 80));
        assertEquals(6, p.threshold("max-parameters", 5));
        assertEquals(7, p.threshold("max-nesting", 7), "sem valor: usa o padrão");
    }

    @Test
    void perfilInexistenteELancadoMasDefaultSempreExiste() throws Exception {
        PackRegistry r = PackRegistry.standalone(tempRoot());
        r.reload();

        assertThrows(IllegalArgumentException.class, () -> r.policyFor("nao-existe"));
        assertNotNull(r.policyFor("default"));
        assertTrue(r.profileExists(null));
        assertTrue(r.profileExists("default"));
        assertFalse(r.profileExists("nao-existe"));
    }

    @Test
    void herancaCircularEDesconhecidaRejeitaOPerfil() throws Exception {
        Path root = tempRoot();
        pack(root, "pk", "1.0.0", true);
        profile(root, "aa", "id: aa\nextends: bb\n");
        profile(root, "bb", "id: bb\nextends: aa\n");
        profile(root, "orfao", "id: orfao\nextends: fantasma\n");
        profile(root, "ok", "id: ok\npacotes-typo: [x]\n");
        profile(root, "bom", "id: bom\npacks: [pk, nao-instalado]\n");
        PackRegistry r = PackRegistry.standalone(root);
        Object snap = r.reload();

        PackRegistry.Snapshot s = r.snapshot();
        assertFalse(s.profiles().containsKey("aa"));
        assertFalse(s.profiles().containsKey("bb"));
        assertFalse(s.profiles().containsKey("orfao"));
        assertFalse(s.profiles().containsKey("ok"), "chave desconhecida rejeita");
        assertTrue(s.profiles().containsKey("bom"));
        assertEquals(4, s.rejected().size(), s.rejected().toString());
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("nao-instalado")), "pacote inexistente vira aviso");
        assertEquals(List.of("pk"), ids(r.policyFor("bom")));
    }

    @Test
    void perfilRejeitaLimiteEChavesInvalidas() throws Exception {
        Path root = tempRoot();
        profile(root, "xx", """
                id: xx
                thresholds: {max-linhas: 10, max-parameters: 0}
                rules:
                  r1: {severity: gravissima}
                  r2: {shadow: talvez}
                  r3: {foo: 1}
                """);
        PackRegistry r = PackRegistry.standalone(root);
        r.reload();

        String msg = String.join(" | ", r.snapshot().rejected().get("profiles/xx.yml"));
        assertTrue(msg.contains("limite desconhecido 'max-linhas'"), msg);
        assertTrue(msg.contains("max-parameters: use um inteiro maior que zero"), msg);
        assertTrue(msg.contains("severity: inválida 'gravissima'"), msg);
        assertTrue(msg.contains("shadow: use true ou false"), msg);
        assertTrue(msg.contains("chave desconhecida 'foo'"), msg);
    }

    @Test
    void recarregarInstalaRemediacaoEDedupDosPacotes() throws Exception {
        Path root = tempRoot();
        Path d = pack(root, "dados", "1.0.0", true);
        write(d.resolve("remediation/r.yml"), "- key: regra-do-pacote\n  summary: Faça assim\n  refs: [https://exemplo.org]\n");
        PackRegistry r = PackRegistry.standalone(root);
        r.reload();

        com.sentinel.audit.model.Finding f = new com.sentinel.audit.model.Finding();
        f.setRuleId("x.y.regra-do-pacote-123");
        assertNotNull(com.sentinel.audit.service.RemediationCatalog.forFinding(f), "chave casa por trecho do id");
        TestPacks.registry();      // restaura o catálogo embutido para os demais testes
    }

    @Test
    void raizDoGitComCheckoutRealELeituraDePacotes() throws Exception {
        Path repo = tempRoot();
        pack(repo, "remoto", "2.0.0", true);
        String[][] cmds = {{"git", "init", "-q", "-b", "main"}, {"git", "add", "-A"},
                {"git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "v1"}};
        for (String[] c : cmds) {
            assertEquals(0, new ProcessBuilder(c).directory(repo.toFile()).inheritIO().start().waitFor(), String.join(" ", c));
        }
        Path cache = tempRoot();

        Path checkout = PackSources.sync("file://" + repo, "main", cache, 60, true);
        assertEquals("remoto", PackLoader.discover(checkout).packs().get(0).id());

        pack(repo, "novo", "1.0.0", true);
        for (String[] c : new String[][]{{"git", "add", "-A"}, {"git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "v2"}}) {
            assertEquals(0, new ProcessBuilder(c).directory(repo.toFile()).inheritIO().start().waitFor());
        }
        Path again = PackSources.sync("file://" + repo, "main", cache, 60, true);
        assertEquals(checkout, again, "reutiliza o cache");
        assertEquals(2, PackLoader.discover(again).packs().size(), "atualiza por fetch");
    }

    @Test
    void urlsEReferenciasPerigosasSaoRecusadas() throws Exception {
        Path cache = tempRoot();
        assertThrows(Exception.class, () -> PackSources.sync("--upload-pack=touch /tmp/x", "main", cache, 5, false));
        assertThrows(Exception.class, () -> PackSources.sync("file:///etc", "main", cache, 5, false), "file:// só em teste");
        assertThrows(Exception.class, () -> PackSources.sync("http://exemplo.org/r.git", "main", cache, 5, false));
        assertThrows(Exception.class, () -> PackSources.sync("https://exemplo.org/r.git", "--evil", cache, 5, false));
        assertFalse(Files.exists(Path.of("/tmp/x")) && Files.getLastModifiedTime(Path.of("/tmp/x")).toMillis() > System.currentTimeMillis() - 60000);
    }

    @Test
    void perfisEmbutidosCarregamEResolvem() {
        PackRegistry r = TestPacks.registry();

        assertTrue(r.snapshot().rejected().isEmpty(), r.snapshot().rejected().toString());
        AuditPolicy legado = r.policyFor("legado");
        assertEquals(120, legado.threshold("max-function-lines", 50));
        assertEquals(Boolean.TRUE, legado.overrideFor("sentinel-java-clean-code.alguma-regra").shadow());
        assertEquals(List.of("core-frontend", "core-iac", "core-persistencia", "core-quality", "core-security", "core-spring", "market-tools"), ids(legado), "sem 'packs': só os habilitados por padrão");

        AuditPolicy camadas = r.policyFor("arquitetura-em-camadas");
        assertTrue(ids(camadas).contains("core-architecture"));
        assertFalse(camadas.architecture().isEmpty());
        assertFalse(ids(r.policyFor("default")).contains("core-architecture"), "exemplo vem desligado por padrão");

        assertFalse(ids(r.policyFor("default")).contains("ts-clean-code"), "padrão de código TypeScript é opt-in");
        AuditPolicy padraoTs = r.policyFor("padrao-typescript-angular");
        assertTrue(ids(padraoTs).contains("ts-clean-code"));
        assertEquals(50, padraoTs.threshold("max-function-lines", 0));
        assertEquals(10, padraoTs.threshold("max-complexity", 0));
        assertEquals(3, padraoTs.threshold("max-nesting", 0));
        assertEquals(500, padraoTs.threshold("max-component-lines", 0));
        assertEquals(com.sentinel.audit.model.Severity.LOW, padraoTs.overrideFor("sentinel-tsp-abreviacao").severity());
        // regras com precisão medida baixa (docs/evolucao/precisao.md) pesam no score só depois de melhorarem
        assertEquals(Boolean.TRUE, padraoTs.overrideFor("eslint-regexp/no-super-linear-move").shadow());
        assertEquals(Boolean.TRUE, padraoTs.overrideFor("jscpd-codigo-duplicado").shadow());
        assertEquals(Boolean.TRUE, legado.overrideFor("jscpd-codigo-duplicado").shadow());
    }
}
