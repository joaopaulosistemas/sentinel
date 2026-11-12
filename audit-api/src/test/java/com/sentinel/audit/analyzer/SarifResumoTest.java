package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.pack.RuleSide;
import com.sentinel.audit.pack.ToolSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.sentinel.audit.analyzer.SarifExecucao.Situacao.NAO_INSTALADA;
import static com.sentinel.audit.analyzer.SarifExecucao.Situacao.RODOU;
import static com.sentinel.audit.analyzer.SarifExecucao.Situacao.SEM_ARQUIVOS;
import static com.sentinel.audit.analyzer.SarifExecucao.Situacao.SEM_PERMISSAO;
import static com.sentinel.audit.analyzer.SarifExecucao.Situacao.SEM_RESULTADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** O texto do resumo das ferramentas de comando, sem rodar nenhuma. */
class SarifResumoTest {

    private static ToolSpec spec(String id, String name) {
        return new ToolSpec(id, name, List.of("x"), null, Category.QUALITY, Map.of(), 60, false, List.of(), RuleSide.COMMON);
    }

    private static SarifExecucao rodou(int achados, long segundos) {
        return new SarifExecucao(java.util.Collections.nCopies(achados, new Finding()), RODOU, segundos);
    }

    @Test
    void semNadaRegistradoNaoHaTexto() {
        assertNull(new SarifResumo().texto());
    }

    @Test
    void agrupaPorSituacaoNaOrdemDaTela() {
        SarifResumo r = new SarifResumo();
        r.registrar(spec("a", "A"), rodou(1, 3));
        r.registrar(spec("b", "B"), rodou(2, 4));
        r.registrar(spec("c", "C"), SarifExecucao.semAchados(SEM_RESULTADO));
        r.falhou(spec("d", "D"));
        r.registrar(spec("e", "E"), SarifExecucao.semAchados(SEM_PERMISSAO));
        r.registrar(spec("f", "F"), SarifExecucao.semAchados(NAO_INSTALADA));
        r.registrar(spec("g", "G"), SarifExecucao.semAchados(SEM_ARQUIVOS));

        assertEquals("Rodaram: A (3 s, 1 achado), B (4 s, 2 achados). Rodaram sem gerar resultado: C. Falharam: D. "
                + "Sem permissão para executar programas: E. Não instaladas: F.", r.texto());
    }

    @Test
    void joernTemFrasePropriaEVemPrimeiro() {
        SarifResumo r = new SarifResumo();
        r.registrar(spec(SarifResumo.JOERN_ID, "Joern"), rodou(1, 7));
        r.registrar(spec("b", "B"), SarifExecucao.semAchados(NAO_INSTALADA));

        assertEquals("Joern (taint entre arquivos): ativo, rodou em 7 s com 1 achado.\nNão instaladas: B.", r.texto());
    }

    @Test
    void joernSemArquivosJavaNaoSeAplica() {
        SarifResumo r = new SarifResumo();
        r.registrar(spec(SarifResumo.JOERN_ID, "Joern"), SarifExecucao.semAchados(SEM_ARQUIVOS));

        assertEquals("Joern (taint entre arquivos): não se aplica: o projeto não tem arquivos Java.", r.texto());
    }

    @Test
    void falhaDoJoernNaoEntraNaListaGeralEDesligadoNaoSobrescreve() {
        SarifResumo r = new SarifResumo();
        r.falhou(spec(SarifResumo.JOERN_ID, "Joern"));
        r.joernDesligado();

        assertEquals("Joern (taint entre arquivos): falhou. Veja o log da API.", r.texto());
    }
}
