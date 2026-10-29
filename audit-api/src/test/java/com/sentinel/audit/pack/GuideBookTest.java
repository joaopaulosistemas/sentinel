package com.sentinel.audit.pack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideBookTest {

    private static List<GuideCatalog.GuideRule> rules() {
        return GuideCatalog.of(PackLoader.discover(TestPacks.builtinRoot()).packs());
    }

    private static GuideBook.Stack stack(String id) {
        return GuideBook.stacks().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void asSeteTecnologiasTemBoasPraticasEComandos() {
        List<GuideBook.Stack> stacks = GuideBook.stacks();

        assertEquals(List.of("java", "spring", "quarkus", "ts", "angular", "react", "vue"), stacks.stream().map(GuideBook.Stack::id).toList());
        for (GuideBook.Stack s : stacks) {
            assertTrue(s.practices().size() >= 5, s.id() + " tem boas práticas");
            assertFalse(s.commands().isEmpty(), s.id() + " tem comandos");
            for (GuideBook.Practice p : s.practices()) {
                assertFalse(p.doIt().isEmpty(), s.id() + ": '" + p.title() + "' diz o que fazer");
                assertFalse(p.avoid().isEmpty(), s.id() + ": '" + p.title() + "' diz o que evitar");
            }
        }
    }

    @Test
    void asRegrasNovasDeQuarkusEVueSaoEspecificasDaTecnologia() {
        List<GuideBook.Stack> stacks = GuideBook.stacks();
        List<GuideCatalog.GuideRule> rules = rules();

        long quarkus = GuideBook.rulesOf(stack("quarkus"), stacks, rules).stream().filter(GuideBook.StackRule::own).count();
        long vue = GuideBook.rulesOf(stack("vue"), stacks, rules).stream().filter(GuideBook.StackRule::own).count();

        assertEquals(7, quarkus);
        assertEquals(8, vue);
    }

    @Test
    void regraDeOutraTecnologiaNaoEntraNaMesmaLinguagem() {
        List<GuideBook.Stack> stacks = GuideBook.stacks();
        List<GuideCatalog.GuideRule> rules = rules();

        List<String> vue = GuideBook.rulesOf(stack("vue"), stacks, rules).stream().map(s -> s.rule().id()).toList();
        List<String> java = GuideBook.rulesOf(stack("java"), stacks, rules).stream().map(s -> s.rule().id()).toList();

        assertFalse(vue.contains("sentinel-ng-componente-sem-onpush"), "regra de Angular fora do Vue");
        assertFalse(vue.contains("sentinel-react-dangerously"), "regra de React fora do Vue");
        assertFalse(java.contains("sentinel-spring-fetch-eager"), "regra de Spring fora do Java puro");
        assertTrue(vue.contains("sentinel-ts-any-explicito"), "regra de TypeScript vale no Vue");
    }

    @Test
    void todaRegraNovaTemOAntesEDepois() {
        for (GuideCatalog.GuideRule r : rules()) {
            if (r.pack().equals("core-quarkus") || r.pack().equals("core-vue") || r.pack().equals("core-boas-praticas")) {
                assertFalse(r.before().isEmpty(), r.id() + " tem 'antes'");
                assertFalse(r.after().isEmpty(), r.id() + " tem 'depois'");
                assertFalse(r.action().isEmpty(), r.id() + " tem ação");
            }
        }
    }

    @Test
    void claudeMdFicaCurtoComComandosEARegrasSemExcecao() {
        List<GuideBook.Stack> stacks = GuideBook.stacks();
        List<GuideCatalog.GuideRule> rules = rules();

        for (GuideBook.Stack s : stacks) {
            String md = GuideBook.claudeMd(s, GuideBook.rulesOf(s, stacks, rules));
            assertTrue(md.lines().count() < 200, s.id() + ": CLAUDE.md abaixo de 200 linhas");
            assertTrue(md.contains("## Verifique antes de dizer que terminou"), s.id());
            assertTrue(md.contains(s.commands().get(0)), s.id() + " traz o primeiro comando");
        }
    }

    @Test
    void curingaCasaEmQualquerPosicaoELiteralNoResto() {
        assertTrue(GuideBook.matches("sentinel-vue-v-html", "sentinel-vue-*"));
        assertTrue(GuideBook.matches("sentinel-ts-useeffect-async", "sentinel-ts-useeffect-async"));
        assertFalse(GuideBook.matches("sentinel-ts-useeffect-async2", "sentinel-ts-useeffect-async"));
        assertTrue(GuideBook.matches("sentinel-a-b-c", "sentinel-*-c"));
        assertFalse(GuideBook.matches("sentinel-axb", "sentinel-a.b"));
    }

    @Test
    void doisPontosDentroDeUmaPalavraNaoCortamOTitulo() {
        assertEquals("v-for sem :key", GuideCatalog.title("v-for sem :key. Sem chave estável o Vue mistura estado."));
    }

    @Test
    void tituloVemAntesDosDoisPontosOuDaPrimeiraFrase() {
        assertEquals("Hash fraco", GuideCatalog.title("Hash fraco: use SHA-256. Detalhe."));
        assertEquals("Algoritmo de hash fraco (MD5/SHA-1)", GuideCatalog.title("Algoritmo de hash fraco (MD5/SHA-1). Use SHA-256."));
    }
}
