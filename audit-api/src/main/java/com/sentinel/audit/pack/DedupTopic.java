package com.sentinel.audit.pack;

import java.util.Set;

/**
 * Grupo de regras de ferramentas diferentes que apontam o mesmo problema.
 *
 * @param rules    trechos (minúsculos) que, contidos no ruleId, indicam este tópico
 * @param maxLines distância máxima entre as linhas para serem o mesmo ponto (-1 = problema do arquivo todo)
 * @param prefer   ferramenta cujo achado fica quando há duplicata; nulo = Semgrep (o padrão do Sentinel)
 */
public record DedupTopic(String name, Set<String> rules, int maxLines, String prefer) {

    public DedupTopic(String name, Set<String> rules, int maxLines) {
        this(name, rules, maxLines, null);
    }
}
