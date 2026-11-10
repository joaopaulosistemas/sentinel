package com.sentinel.audit.analyzer;

import com.sentinel.audit.pack.ToolSpec;

import java.util.ArrayList;
import java.util.List;

/** Resumo, para a tela de acompanhamento, de quais ferramentas de comando rodaram e quais ficaram de fora. */
final class SarifResumo {

    /** Identificador da fonte do Joern em {@code market-tools/tools/taint.yml}. */
    static final String JOERN_ID = "joern-taint";

    /** Pacote que traz as ferramentas externas, entre elas o Joern. */
    static final String TOOLS_PACK_ID = "market-tools";

    private final List<String> rodaram = new ArrayList<>();
    private final List<String> semPermissao = new ArrayList<>();
    private final List<String> naoInstaladas = new ArrayList<>();
    private final List<String> semResultado = new ArrayList<>();
    private final List<String> falhou = new ArrayList<>();
    /** Situação do Joern em frase própria: é a ferramenta opcional, e a pessoa precisa ver se está ativa. */
    private String joern;

    /** O pacote das ferramentas externas não está ativo neste sistema (fora do perfil ou desligado nos ajustes). */
    void joernDesligado() {
        if (joern == null) {
            joern = "desligado neste sistema (o pacote " + TOOLS_PACK_ID + " está fora do perfil ou desligado nos ajustes).";
        }
    }

    void falhou(ToolSpec spec) {
        if (JOERN_ID.equals(spec.id())) {
            joern = "falhou. Veja o log da API.";
        } else {
            falhou.add(spec.name());
        }
    }

    void registrar(ToolSpec spec, SarifExecucao e) {
        if (JOERN_ID.equals(spec.id())) {
            joern = fraseDoJoern(e);
        } else {
            registrarFerramenta(spec.name(), e);
        }
    }

    private static String fraseDoJoern(SarifExecucao e) {
        return switch (e.situacao()) {
            case RODOU -> "ativo, rodou em " + e.segundos() + " s com " + achados(e) + ".";
            case SEM_RESULTADO -> "rodou sem gerar resultado.";
            case NAO_INSTALADA -> "não instalado nesta imagem. Construa com WITH_JOERN=true para ligar.";
            case SEM_PERMISSAO -> "sem permissão para executar programas neste pacote.";
            case SEM_ARQUIVOS -> "não se aplica: o projeto não tem arquivos Java.";
        };
    }

    private void registrarFerramenta(String nome, SarifExecucao e) {
        switch (e.situacao()) {
            case RODOU -> rodaram.add(nome + " (" + e.segundos() + " s, " + achados(e) + ")");
            case SEM_PERMISSAO -> semPermissao.add(nome);
            case NAO_INSTALADA -> naoInstaladas.add(nome);
            case SEM_RESULTADO -> semResultado.add(nome);
            case SEM_ARQUIVOS -> { /* não se aplica a este projeto: não entra no resumo */ }
            default -> { /* situação nova: ainda não entra no resumo */ }
        }
    }

    private static String achados(SarifExecucao e) {
        int n = e.findings().size();
        return n + (n == 1 ? " achado" : " achados");
    }

    /** {@code null} quando nenhuma ferramenta de comando entrou em jogo. */
    String texto() {
        List<String> partes = new ArrayList<>();
        adicionar(partes, "Rodaram: ", rodaram);
        adicionar(partes, "Rodaram sem gerar resultado: ", semResultado);
        adicionar(partes, "Falharam: ", falhou);
        adicionar(partes, "Sem permissão para executar programas: ", semPermissao);
        adicionar(partes, "Não instaladas: ", naoInstaladas);
        String geral = partes.isEmpty() ? null : String.join(". ", partes) + ".";
        if (joern == null) {
            return geral;
        }
        String linhaJoern = "Joern (taint entre arquivos): " + joern;
        // A frase do Joern vai primeiro: a tela corta a nota em 300 caracteres e a lista geral pode ser longa.
        return geral == null ? linhaJoern : linhaJoern + "\n" + geral;
    }

    private static void adicionar(List<String> partes, String rotulo, List<String> nomes) {
        if (!nomes.isEmpty()) {
            partes.add(rotulo + String.join(", ", nomes));
        }
    }
}
