package com.sentinel.audit.analyzer;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Caminhos que o projeto auditado declara fora da auditoria ({@code exclude} do {@code .sentinel.yml}).
 * O analisador declara a interface e o serviço a implementa, para o pacote {@code analyzer} não depender de
 * {@code service}.
 */
public interface ProjectExclusions {

    /** Nenhuma exclusão: tudo entra na auditoria. */
    ProjectExclusions NONE = root -> path -> false;

    /** Devolve o teste "este caminho (relativo à raiz do projeto) está excluído?" para o projeto em {@code root}. */
    Predicate<String> forProject(Path root);
}
