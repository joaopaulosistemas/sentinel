package com.sentinel.audit.settings;

import java.util.List;

/**
 * Uma configuração da instalação: o nome da variável de ambiente (a chave), a propriedade que a API lê, onde
 * aparece na tela e como validar. {@code editable = false} marca o que só se muda no ambiente (banco, chaves,
 * caminhos e tudo que dá poder de executar programas); a tela mostra o valor, mas a API não aceita alterá-lo.
 */
public record SettingDef(String key, String property, String section, String group, String label, String help,
                         Kind kind, String def, List<Option> options, String unit, int min, int max,
                         boolean editable, boolean secret) {

    public enum Kind { BOOL, NUMBER, TEXT, ENUM }

    public record Option(String value, String label, String sub) {}
}
