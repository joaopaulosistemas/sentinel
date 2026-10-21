package com.sentinel.audit.pack;

import java.util.Set;

/**
 * Grupo de regras de ferramentas diferentes que apontam o mesmo problema.
 *
 * @param rules    trechos (minúsculos) que, contidos no ruleId, indicam este tópico
 * @param maxLines distância máxima entre as linhas para serem o mesmo ponto (-1 = problema do arquivo todo)
 */
public record DedupTopic(String name, Set<String> rules, int maxLines) {}
