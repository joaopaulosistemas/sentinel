package com.sentinel.audit.analyzer;

/**
 * Encerra um processo externo junto com tudo o que ele iniciou. {@code Process.destroyForcibly()} mata só o
 * processo direto: um wrapper (npx, sh, mvn) morre e deixa a ferramenta de verdade rodando, consumindo CPU e memória
 * depois de a auditoria ter sido cancelada ou ter estourado o tempo.
 */
public final class ProcessTree {

    private ProcessTree() {
    }

    public static void destroy(Process process) {
        if (process == null) {
            return;
        }
        // descendentes primeiro: depois que o pai morre, os filhos passam a ser filhos do init e somem da árvore
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
