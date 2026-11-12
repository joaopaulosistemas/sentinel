package com.sentinel.audit.settings;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lê o valor em uso de cada configuração e grava o que foi definido na tela. O que vale agora é o que a API leu
 * ao subir; o que foi salvo depois só entra em vigor no próximo reinício ({@code pendingRestart}).
 */
@ApplicationScoped
public class SettingsService {

    static final String MASK = "********";
    static final String SOURCE_DB = "tela";

    public record SettingView(@Schema(description = "Chave da configuração.") String key, @Schema(description = "Seção.") String section, @Schema(description = "Grupo.") String group, @Schema(description = "Nome exibido.") String label, @Schema(description = "Ajuda.") String help, @Schema(description = "Tipo do valor.") String kind,
                              @Schema(description = "Valor padrão.") String def, @Schema(description = "Opções, quando o valor é uma escolha.") List<SettingDef.Option> options, @Schema(description = "Unidade.") String unit, @Schema(description = "Mínimo.") int min, @Schema(description = "Máximo.") int max,
                              @Schema(description = "Se pode ser editada na tela.") boolean editable, @Schema(description = "Se o valor é secreto e não é mostrado.") boolean secret, @Schema(description = "Valor em uso.") String value, @Schema(description = "Valor salvo.") String savedValue, @Schema(description = "De onde vem o valor em uso (padrão, ambiente ou o que foi salvo na tela).") String source,
                              @Schema(description = "Salvo, mas só vale após reiniciar.") boolean pendingRestart) {}

    public record SettingsView(@Schema(description = "Configurações.") List<SettingView> settings, @Schema(description = "Há alteração que só vale após reiniciar.") boolean restartRequired) {}

    @Inject
    EntityManager em;

    public SettingsView view() {
        Map<String, String> saved = em.createQuery("select s from AppSetting s", AppSetting.class).getResultList().stream()
                .collect(Collectors.toMap(AppSetting::getKey, AppSetting::getValue));
        return buildView(ConfigProvider.getConfig(), saved);
    }

    /** Monta a visão a partir da configuração em uso e dos valores salvos (separado para testar sem a API no ar). */
    static SettingsView buildView(Config config, Map<String, String> saved) {
        List<SettingView> out = new ArrayList<>();
        boolean restart = false;
        for (SettingDef d : SettingsCatalog.all()) {
            ConfigValue cv = config.getConfigValue(d.property());
            String effective = cv == null || cv.getValue() == null ? d.def() : cv.getValue();
            String source = sourceOf(cv);
            String savedValue = d.editable() ? saved.get(d.key()) : null;
            boolean pending = d.editable() && (savedValue != null ? !savedValue.equals(effective) : SOURCE_DB.equals(source));
            restart |= pending;
            out.add(new SettingView(d.key(), d.section(), d.group(), d.label(), d.help(), d.kind().name().toLowerCase(java.util.Locale.ROOT),
                    d.def(), d.options(), d.unit(), d.min(), d.max(), d.editable(), d.secret(),
                    mask(d, effective), savedValue, source, pending));
        }
        return new SettingsView(List.copyOf(out), restart);
    }

    static String mask(SettingDef d, String value) {
        return d.secret() && value != null && !value.isEmpty() ? MASK : value;
    }

    static String sourceOf(ConfigValue cv) {
        if (cv == null || cv.getValue() == null || cv.getSourceName() == null) {
            return "padrão";
        }
        String name = cv.getSourceName();
        if (name.contains("sentinel-settings-db")) {
            return SOURCE_DB;
        }
        return name.toLowerCase(java.util.Locale.ROOT).contains("env") ? "ambiente" : "padrão";
    }

    /** Valida tudo antes de gravar qualquer coisa. Valor nulo volta a configuração para o que vem do ambiente. */
    @Transactional
    public SettingsView save(Map<String, String> values) {
        List<String> errors = validateAll(values);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            reset(e.getKey());
            if (e.getValue() != null) {
                AppSetting row = new AppSetting();
                row.setKey(e.getKey());
                row.setValue(e.getValue().trim());
                em.persist(row);
            }
        }
        em.flush();
        return view();
    }

    static List<String> validateAll(Map<String, String> values) {
        List<String> errors = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            errors.add("Nenhuma configuração informada");
            return errors;
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            SettingDef d = SettingsCatalog.find(e.getKey()).orElse(null);
            if (d == null) {
                errors.add("Configuração desconhecida: " + e.getKey());
            } else if (e.getValue() != null) {
                String err = SettingsCatalog.validate(d, e.getValue());
                if (err != null) {
                    errors.add(err);
                }
            } else if (!d.editable()) {
                errors.add(SettingsCatalog.validate(d, ""));
            }
        }
        return errors;
    }

    @Transactional
    public SettingsView resetOne(String key) {
        SettingDef d = SettingsCatalog.find(key).orElseThrow(() -> new IllegalArgumentException("Configuração desconhecida: " + key));
        if (!d.editable()) {
            throw new IllegalArgumentException(SettingsCatalog.validate(d, ""));
        }
        reset(key);
        em.flush();
        return view();
    }

    private void reset(String key) {
        AppSetting existing = em.find(AppSetting.class, key);
        if (existing != null) {
            em.remove(existing);
            em.flush();
        }
    }
}
