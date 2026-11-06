package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.Finding;

import java.util.List;

/** Como uma ferramenta SARIF de comando terminou: o que achou e o que aconteceu com ela. */
record SarifExecucao(List<Finding> findings, Situacao situacao, long segundos) {

    enum Situacao { RODOU, SEM_PERMISSAO, NAO_INSTALADA, SEM_ARQUIVOS, SEM_RESULTADO }

    static SarifExecucao semAchados(Situacao situacao) {
        return new SarifExecucao(List.of(), situacao, 0);
    }
}
