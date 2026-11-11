package com.sentinel.audit.pack;

import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileLoaderTest {

    private static Path raizCom(String... nomeEConteudo) throws IOException {
        Path root = Files.createTempDirectory("perfis");
        Path dir = Files.createDirectories(root.resolve("profiles"));
        for (int i = 0; i < nomeEConteudo.length; i += 2) {
            Files.writeString(dir.resolve(nomeEConteudo[i]), nomeEConteudo[i + 1]);
        }
        return root;
    }

    @Test
    void carregaPerfilCompletoComRegrasELimites() throws Exception {
        Path root = raizCom("legado.yml", """
                id: legado
                name: Sistema legado
                description: tolera mais
                extends: base
                packs: [core-security]
                disable-packs: [core-vue]
                rules:
                  Arch-God-Object: {severity: low}
                  sentinel-x: {disabled: true, shadow: true}
                thresholds:
                  max-function-lines: 80
                """);
        Map<String, List<String>> rejected = new LinkedHashMap<>();

        Map<String, Profile> out = ProfileLoader.loadAll(root, rejected);

        assertTrue(rejected.isEmpty(), rejected.toString());
        Profile p = out.get("legado");
        assertEquals("Sistema legado", p.name());
        assertEquals("base", p.extendsId());
        assertEquals(List.of("core-security"), p.packs());
        assertEquals(List.of("core-vue"), p.disablePacks());
        assertEquals(Severity.LOW, p.rules().get("arch-god-object").severity(), "o id da regra é normalizado para minúsculas");
        assertTrue(p.rules().get("sentinel-x").disabled());
        assertEquals(Boolean.TRUE, p.rules().get("sentinel-x").shadow());
        assertEquals(80, p.thresholds().get("max-function-lines").intValue());
    }

    @Test
    void semPastaProfilesOuSemRaizNaoHaPerfisENemErro() throws Exception {
        Map<String, List<String>> rejected = new LinkedHashMap<>();
        assertTrue(ProfileLoader.loadAll(null, rejected).isEmpty());
        assertTrue(ProfileLoader.loadAll(Files.createTempDirectory("vazio"), rejected).isEmpty());
        assertTrue(rejected.isEmpty());
    }

    @Test
    void chaveDesconhecidaSeveridadeInvalidaELimiteErradoRejeitamOPerfil() throws Exception {
        Path root = raizCom(
                "chave.yml", "id: perfil-a\nnome: errado\n",
                "sev.yml", "id: perfil-b\nrules:\n  r1: {severity: gravissima}\n",
                "lim.yml", "id: perfil-c\nthresholds:\n  max-linhas: 5\n  max-nesting: 0\n",
                "shadow.yml", "id: perfil-d\nrules:\n  r1: {shadow: talvez}\n");
        Map<String, List<String>> rejected = new LinkedHashMap<>();

        Map<String, Profile> out = ProfileLoader.loadAll(root, rejected);

        assertTrue(out.isEmpty(), "nenhum perfil com erro é aceito pela metade");
        assertTrue(rejected.get("profiles/chave.yml").get(0).contains("chave desconhecida 'nome'"));
        assertTrue(rejected.get("profiles/sev.yml").get(0).contains("severity: inválida 'gravissima'"));
        List<String> lim = rejected.get("profiles/lim.yml");
        assertTrue(lim.stream().anyMatch(e -> e.contains("limite desconhecido 'max-linhas'")));
        assertTrue(lim.stream().anyMatch(e -> e.contains("max-nesting: use um inteiro maior que zero")));
        assertTrue(rejected.get("profiles/shadow.yml").get(0).contains("shadow: use true ou false"));
    }

    @Test
    void idAusenteOuInvalidoEPerfilRepetidoSaoRejeitados() throws Exception {
        Path root = raizCom(
                "a.yml", "name: sem id\n",
                "b.yml", "id: Maiuscula_Errada\n",
                "c.yml", "id: igual\n",
                "d.yml", "id: igual\n");
        Map<String, List<String>> rejected = new LinkedHashMap<>();

        Map<String, Profile> out = ProfileLoader.loadAll(root, rejected);

        assertEquals(List.of("igual"), List.copyOf(out.keySet()), "o primeiro perfil com o id vale, em ordem de arquivo");
        assertTrue(rejected.get("profiles/a.yml").get(0).contains("'id' ausente ou inválido"));
        assertTrue(rejected.get("profiles/b.yml").get(0).contains("'id' ausente ou inválido"));
        assertTrue(rejected.get("profiles/d.yml").get(0).contains("perfil 'igual' repetido"));
        assertFalse(rejected.containsKey("profiles/c.yml"));
    }

    @Test
    void yamlQuebradoOuQueNaoEMapaEArquivoGrandeNaoDerrubamOCarregamento() throws Exception {
        Path root = raizCom(
                "quebrado.yml", "id: [abc\n",
                "lista.yml", "- um\n- dois\n",
                "ok.yml", "id: bom\n",
                "leia-me.txt", "não é perfil\n");
        Files.writeString(root.resolve("profiles/grande.yml"), "id: perfil-g\n# " + "x".repeat((int) PackLoader.MAX_FILE_BYTES + 10));
        Map<String, List<String>> rejected = new LinkedHashMap<>();

        Map<String, Profile> out = ProfileLoader.loadAll(root, rejected);

        assertEquals(List.of("bom"), List.copyOf(out.keySet()));
        assertTrue(rejected.get("profiles/quebrado.yml").get(0).contains("YAML inválido"));
        assertTrue(rejected.get("profiles/lista.yml").get(0).contains("esperava um mapa"));
        assertTrue(rejected.get("profiles/grande.yml").get(0).contains("maior que 1 MB"));
        assertNull(rejected.get("profiles/leia-me.txt"), "só .yml e .yaml são perfis");
    }
}
