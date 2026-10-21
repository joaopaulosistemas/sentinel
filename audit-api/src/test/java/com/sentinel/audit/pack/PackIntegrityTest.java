package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.sentinel.audit.pack.PackFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PackIntegrityTest {

    @Test
    void semLockEhNaoAssinado() throws Exception {
        Path dir = packWithRule(tempRoot(), "meu-pacote", "1.0.0");
        assertEquals(PackIntegrity.State.UNSIGNED, PackIntegrity.verify(dir, null).state());
    }

    @Test
    void lockSemAssinaturaConfereOsHashes() throws Exception {
        Path dir = packWithRule(tempRoot(), "meu-pacote", "1.0.0");
        PackIntegrity.seal(dir, null);

        assertEquals(PackIntegrity.State.CHECKSUM_OK, PackIntegrity.verify(dir, null).state());
        assertTrue(Files.readString(dir.resolve("pack.lock")).contains("  rules/r.yml\n"));
        assertFalse(Files.exists(dir.resolve("pack.sig")));
    }

    @Test
    void assinaturaValidaComAChaveCerta() throws Exception {
        String[] keys = PackIntegrity.generateKeyPair();
        Path dir = packWithRule(tempRoot(), "meu-pacote", "1.0.0");
        PackIntegrity.seal(dir, PackIntegrity.privateKey(keys[0]));

        assertEquals(PackIntegrity.State.SIGNED, PackIntegrity.verify(dir, PackIntegrity.publicKey(keys[1])).state());
        assertEquals(PackIntegrity.State.CHECKSUM_OK, PackIntegrity.verify(dir, null).state(), "sem chave: só os hashes");

        String[] outra = PackIntegrity.generateKeyPair();
        PackIntegrity.Result r = PackIntegrity.verify(dir, PackIntegrity.publicKey(outra[1]));
        assertEquals(PackIntegrity.State.INVALID, r.state());
        assertTrue(r.detail().contains("assinatura"));
    }

    @Test
    void detectaArquivoAlteradoIncluidoERemovido() throws Exception {
        String[] keys = PackIntegrity.generateKeyPair();
        Path dir = packWithRule(tempRoot(), "meu-pacote", "1.0.0");
        PackIntegrity.seal(dir, PackIntegrity.privateKey(keys[0]));
        var pub = PackIntegrity.publicKey(keys[1]);

        Files.writeString(dir.resolve("rules/r.yml"), Files.readString(dir.resolve("rules/r.yml")) + "\n# adulterado\n");
        PackIntegrity.Result alterado = PackIntegrity.verify(dir, pub);
        assertEquals(PackIntegrity.State.INVALID, alterado.state());
        assertEquals("arquivo alterado: rules/r.yml", alterado.detail());

        PackIntegrity.seal(dir, PackIntegrity.privateKey(keys[0]));
        write(dir.resolve("rules/extra.yml"), "rules: []\n");
        assertEquals("arquivo não previsto no pack.lock: rules/extra.yml", PackIntegrity.verify(dir, pub).detail());

        Files.delete(dir.resolve("rules/extra.yml"));
        Files.delete(dir.resolve("tests/r.java"));
        assertEquals("arquivo removido: tests/r.java", PackIntegrity.verify(dir, pub).detail());
    }

    @Test
    void lockReescritoPeloAtacanteNaoPassaSemAssinaturaDele() throws Exception {
        String[] keys = PackIntegrity.generateKeyPair();
        Path dir = packWithRule(tempRoot(), "meu-pacote", "1.0.0");
        PackIntegrity.seal(dir, PackIntegrity.privateKey(keys[0]));
        Files.writeString(dir.resolve("rules/r.yml"), "rules: []\n");
        Files.writeString(dir.resolve("pack.lock"), PackIntegrity.computeLock(dir));   // refaz só o lock

        assertEquals(PackIntegrity.State.INVALID, PackIntegrity.verify(dir, PackIntegrity.publicKey(keys[1])).state());
    }

    @Test
    void sigIlegivelEhInvalido() throws Exception {
        String[] keys = PackIntegrity.generateKeyPair();
        Path dir = pack(tempRoot(), "meu-pacote", "1.0.0", true);
        PackIntegrity.seal(dir, PackIntegrity.privateKey(keys[0]));
        Files.writeString(dir.resolve("pack.sig"), "!!!não é base64!!!");
        assertEquals(PackIntegrity.State.INVALID, PackIntegrity.verify(dir, PackIntegrity.publicKey(keys[1])).state());
    }

    // ------------------------------------------------------------------ registro

    @Test
    void registroRejeitaPacoteAdulteradoMesmoSemExigirAssinatura() throws Exception {
        Path root = tempRoot();
        Path ok = pack(root, "intacto", "1.0.0", true);
        Path ruim = pack(root, "adulterado", "1.0.0", true);
        PackIntegrity.seal(ok, null);
        PackIntegrity.seal(ruim, null);
        Files.writeString(ruim.resolve("pack.yml"), Files.readString(ruim.resolve("pack.yml")) + "description: mexido\n");

        PackRegistry r = PackRegistry.standalone(root);
        r.reload();

        assertEquals(List.of("intacto"), r.snapshot().packs().stream().map(RulePack::id).toList());
        String why = r.snapshot().rejected().values().iterator().next().get(0);
        assertEquals("integridade: arquivo alterado: pack.yml", why);
        assertEquals("checksum-ok", r.snapshot().integrity().get("intacto"));
    }

    @Test
    void exigirAssinaturaSoCarregaPacotesAssinadosComAChaveConfigurada() throws Exception {
        String[] keys = PackIntegrity.generateKeyPair();
        String[] intruso = PackIntegrity.generateKeyPair();
        Path root = tempRoot();
        PackIntegrity.seal(pack(root, "assinado", "1.0.0", true), PackIntegrity.privateKey(keys[0]));
        PackIntegrity.seal(pack(root, "de-outro", "1.0.0", true), PackIntegrity.privateKey(intruso[0]));
        PackIntegrity.seal(pack(root, "so-lock", "1.0.0", true), null);
        pack(root, "sem-nada", "1.0.0", true);

        PackRegistry r = PackRegistry.standalone(root);
        r.requireSignature = true;
        r.publicKey = Optional.of(keys[1]);
        r.reload();

        assertEquals(List.of("assinado"), r.snapshot().packs().stream().map(RulePack::id).toList());
        assertEquals("signed", r.snapshot().integrity().get("assinado"));
        assertEquals(3, r.snapshot().rejected().size(), r.snapshot().rejected().toString());
    }

    @Test
    void exigirAssinaturaSemChaveNaoCarregaNada() throws Exception {
        Path root = tempRoot();
        pack(root, "qualquer", "1.0.0", true);
        PackRegistry r = PackRegistry.standalone(root);
        r.requireSignature = true;
        r.reload();

        assertEquals(0, r.snapshot().packs().size());
        assertTrue(r.snapshot().warnings().stream().anyMatch(w -> w.contains("require-signature")));
    }

    @Test
    void chavePublicaInvalidaGeraAvisoEMantemOComportamentoSemChave() throws Exception {
        Path root = tempRoot();
        pack(root, "qualquer", "1.0.0", true);
        PackRegistry r = PackRegistry.standalone(root);
        r.publicKey = Optional.of("isso-nao-e-chave");
        r.reload();

        assertEquals(1, r.snapshot().packs().size());
        assertTrue(r.snapshot().warnings().stream().anyMatch(w -> w.contains("public-key inválida")));
    }
}
