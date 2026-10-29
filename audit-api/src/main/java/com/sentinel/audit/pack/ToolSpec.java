package com.sentinel.audit.pack;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Severity;

import java.util.List;
import java.util.Map;

/**
 * Fonte de findings no formato SARIF 2.1.0 declarada por um pacote, sem precisar de código Java.
 * <p>
 * Dois modos: {@code input} (lê arquivos SARIF que já estão no projeto auditado, ex.: gerados pelo CI do time;
 * só leitura, sempre permitido) e {@code command} (executa uma ferramenta que escreve o SARIF em {@code {output}}
 * ou, com {@code stdout}, na saída padrão; só roda se a instalação liberou, ver {@code sentinel.audit.packs.allow-tools}
 * e {@code sentinel.audit.packs.trusted-tool-packs}).
 *
 * @param command       comando e argumentos (sem shell); placeholders {@code {project}}, {@code {output}} e {@code {files}}
 * @param input         glob, relativo à raiz do projeto, dos arquivos SARIF a ler
 * @param levelSeverity severidade por nível SARIF (error/warning/note/none)
 * @param stdout        {@code true}: o SARIF é a saída padrão da ferramenta (ela não precisa de {@code {output}})
 * @param appliesTo     globs: a ferramenta só roda se o projeto tiver ao menos um arquivo que case (vazio = sempre);
 *                      {@code {files}} vira a lista desses arquivos
 */
public record ToolSpec(String id, String name, List<String> command, String input, Category category,
                       Map<String, Severity> levelSeverity, int timeoutSeconds, boolean stdout, List<String> appliesTo) {

    public boolean executes() {
        return command != null && !command.isEmpty();
    }
}
