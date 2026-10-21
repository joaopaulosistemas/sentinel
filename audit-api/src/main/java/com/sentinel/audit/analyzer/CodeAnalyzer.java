package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import java.nio.file.Path;
import java.util.List;

/**
 * Contrato para analisadores de código.
 * Cada implementação é responsável por uma categoria específica de verificações.
 */
public interface CodeAnalyzer {

    /**
     * Executa a análise no projeto localizado em {@code projectPath}.
     *
     * @param projectPath caminho do diretório raiz do projeto clonado
     * @param auditRun    a execução de auditoria em andamento
     * @return resultado com os findings encontrados e metadados da execução
     */
    AnalyzerResult analyze(Path projectPath, AuditRun auditRun);

    /**
     * Executa a análise sob uma política (pacotes ativos, limites do perfil). Analisadores que dependem dela
     * (regras, limites, arquitetura) sobrescrevem este método; os demais seguem no método de dois argumentos.
     */
    default AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        return analyze(projectPath, auditRun);
    }

    /**
     * Retorna o nome descritivo deste analyzer.
     *
     * @return nome do analyzer
     */
    String getName();

    /**
     * Indica se o analyzer suporta a stack tecnológica informada.
     *
     * @param stack stack do sistema sendo auditado
     * @return {@code true} se o analyzer pode analisar esta stack
     */
    boolean supports(Stack stack);

    /**
     * Indica se o analisador pode rodar neste ambiente (ex.: o executável da ferramenta externa existe).
     * Só analisadores disponíveis podem substituir outros ({@link #replaces()}).
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Analisadores internos que este cobre por inteiro: quando este está disponível, eles não rodam,
     * para o mesmo problema não ser contado duas vezes.
     */
    default List<Class<? extends CodeAnalyzer>> replaces() {
        return List.of();
    }
}
