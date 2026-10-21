package com.sentinel.audit.analyzer.deps;

/**
 * Dependência declarada em um manifesto/lockfile.
 *
 * @param ecosystem nome do ecossistema como o OSV.dev espera: Maven, npm, PyPI, NuGet
 * @param name      nome do pacote ("grupo:artefato" no Maven)
 * @param version   versão resolvida/declarada (sem operadores)
 * @param filePath  arquivo de origem, relativo à raiz do projeto
 */
public record Dependency(String ecosystem, String name, String version, String filePath) {

    /** Nome curto (artifactId no Maven) usado pela base interna de fallback. */
    public String shortName() {
        int i = name.lastIndexOf(':');
        return i >= 0 ? name.substring(i + 1) : name;
    }
}
