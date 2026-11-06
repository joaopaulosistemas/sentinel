package com.sentinel.audit.pack;

import java.util.List;
import java.util.Set;

/**
 * Grupo de regras de ferramentas diferentes que apontam o mesmo problema.
 *
 * @param rules    trechos (minúsculos) que, contidos no ruleId, indicam este tópico
 * @param maxLines distância máxima entre as linhas para serem o mesmo ponto (-1 = problema do arquivo todo)
 * @param prefer   ferramenta cujo achado fica quando há duplicata; nulo = Semgrep (o padrão do Sentinel)
 * @param order    só para regras da MESMA ferramenta (duas regras Sentinel na mesma linha): ids em ordem de
 *                 prioridade, da mais específica para a mais genérica; o achado da regra de menor índice fica e
 *                 absorve os demais. Vazio = tópico entre ferramentas diferentes (o caso comum)
 */
public record DedupTopic(String name, Set<String> rules, int maxLines, String prefer, List<String> order) {

    public DedupTopic {
        order = order == null ? List.of() : List.copyOf(order);
    }

    public DedupTopic(String name, Set<String> rules, int maxLines, String prefer) {
        this(name, rules, maxLines, prefer, List.of());
    }

    public DedupTopic(String name, Set<String> rules, int maxLines) {
        this(name, rules, maxLines, null, List.of());
    }
}
