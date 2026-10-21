package com.sentinel.audit.pack;

import java.nio.file.Path;

/** Carrega os pacotes embutidos (audit-rules/packs) para os testes que dependem do catálogo e das equivalências. */
public final class TestPacks {

    private static PackRegistry cached;

    private TestPacks() {}

    /** Raiz de pacotes do repositório, vista do módulo audit-api (onde o Maven roda os testes). */
    public static Path builtinRoot() {
        return Path.of("..", "audit-rules", "packs");
    }

    /** Registro com os pacotes embutidos; também instala o catálogo de correções e as equivalências globais. */
    public static synchronized PackRegistry registry() {
        if (cached == null) {
            cached = PackRegistry.standalone(builtinRoot());
        }
        cached.reload();                     // reinstala os estáticos globais caso outro teste os tenha trocado
        return cached;
    }
}
