package com.sentinel.audit.pack;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Identidade de um pacote de regras ({@code pack.yml}).
 *
 * @param id               identificador estável (minúsculas, números e hífen)
 * @param version          versão semântica (x.y.z)
 * @param languages        linguagens a que o pacote se aplica (informativo, usado na tela de pacotes)
 * @param enabledByDefault se o pacote vale para sistemas sem perfil que o inclua/exclua explicitamente
 */
public record PackManifest(String id, String name, String version, String description, String author,
                           List<String> languages, boolean enabledByDefault) {

    static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}");
    static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+([-+][0-9A-Za-z.-]+)?");
}
