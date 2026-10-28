package com.sentinel.audit.settings;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    private static ConfigValue valor(String valor, String fonte) {
        ConfigValue cv = mock(ConfigValue.class);
        when(cv.getValue()).thenReturn(valor);
        when(cv.getSourceName()).thenReturn(fonte);
        return cv;
    }

    /** Configuração simulada: cada propriedade devolve o valor e a fonte informados; as outras vêm do padrão. */
    private static Config config(Map<String, ConfigValue> porPropriedade) {
        Config c = mock(Config.class);
        when(c.getConfigValue(anyString())).thenAnswer(inv -> porPropriedade.getOrDefault(inv.getArgument(0), valor(null, null)));
        return c;
    }

    private static SettingsService.SettingView achar(SettingsService.SettingsView v, String key) {
        return v.settings().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void mostraOValorEmUsoComSuaOrigem() {
        Map<String, ConfigValue> cfg = new HashMap<>();
        cfg.put("sentinel.audit.trivy.timeout-seconds", valor("120", "sentinel-settings-db"));
        cfg.put("sentinel.audit.osv.url", valor("https://osv.interno", "EnvConfigSource"));
        cfg.put("sentinel.audit.pmd.max-findings", valor("500", "PropertiesConfigSource[source=application.properties]"));

        SettingsService.SettingsView v = SettingsService.buildView(config(cfg), Map.of("TRIVY_TIMEOUT_SECONDS", "120"));

        assertEquals("tela", achar(v, "TRIVY_TIMEOUT_SECONDS").source());
        assertEquals("ambiente", achar(v, "OSV_URL").source());
        assertEquals("padrão", achar(v, "PMD_MAX_FINDINGS").source());
        assertEquals("padrão", achar(v, "OSV_TIMEOUT").source());
        assertEquals("20", achar(v, "OSV_TIMEOUT").value());
        assertFalse(v.restartRequired());
    }

    @Test
    void segredosSaemMascaradosENuncaComOValor() {
        Map<String, ConfigValue> cfg = new HashMap<>();
        cfg.put("sentinel.audit.api-key", valor("chave-super-secreta", "EnvConfigSource"));
        cfg.put("quarkus.datasource.password", valor("senha-do-banco", "EnvConfigSource"));

        SettingsService.SettingsView v = SettingsService.buildView(config(cfg), Map.of());

        assertEquals("********", achar(v, "API_KEY").value());
        assertEquals("********", achar(v, "DB_PASSWORD").value());
        assertFalse(v.toString().contains("chave-super-secreta"));
        assertFalse(v.toString().contains("senha-do-banco"));
    }

    @Test
    void segredoVazioContinuaVazio() {
        SettingsService.SettingsView v = SettingsService.buildView(config(new HashMap<>()), Map.of());
        assertEquals("", achar(v, "API_KEY").value());
    }

    @Test
    void valorSalvoDiferenteDoEmUsoAguardaReinicio() {
        Map<String, ConfigValue> cfg = new HashMap<>();
        cfg.put("sentinel.audit.trivy.enabled", valor("true", "EnvConfigSource"));

        SettingsService.SettingsView v = SettingsService.buildView(config(cfg), Map.of("TRIVY_ENABLED", "false"));

        SettingsService.SettingView s = achar(v, "TRIVY_ENABLED");
        assertTrue(s.pendingRestart());
        assertEquals("false", s.savedValue());
        assertEquals("true", s.value());
        assertTrue(v.restartRequired());
    }

    @Test
    void voltarParaOAmbienteDepoisDeUsarATelaTambemAguardaReinicio() {
        Map<String, ConfigValue> cfg = new HashMap<>();
        cfg.put("sentinel.audit.trivy.enabled", valor("false", "sentinel-settings-db"));

        SettingsService.SettingsView v = SettingsService.buildView(config(cfg), Map.of());

        assertTrue(achar(v, "TRIVY_ENABLED").pendingRestart());
    }

    @Test
    void somenteLeituraNuncaAguardaReinicioNemMostraValorSalvo() {
        Map<String, ConfigValue> cfg = new HashMap<>();
        cfg.put("sentinel.audit.packs.allow-tools", valor("false", "EnvConfigSource"));

        SettingsService.SettingsView v = SettingsService.buildView(config(cfg), Map.of("PACKS_ALLOW_TOOLS", "true"));

        SettingsService.SettingView s = achar(v, "PACKS_ALLOW_TOOLS");
        assertFalse(s.editable());
        assertFalse(s.pendingRestart());
        assertEquals(null, s.savedValue());
    }

    @Test
    void validaTudoAntesDeGravar() {
        Map<String, String> v = new HashMap<>();
        v.put("TRIVY_ENABLED", "talvez");
        v.put("PACKS_ALLOW_TOOLS", "true");
        v.put("NAO_EXISTE", "1");
        v.put("OSV_TIMEOUT", "30");

        List<String> erros = SettingsService.validateAll(v);

        assertEquals(3, erros.size());
        assertTrue(erros.stream().anyMatch(e -> e.contains("true ou false")));
        assertTrue(erros.stream().anyMatch(e -> e.contains("PACKS_ALLOW_TOOLS")));
        assertTrue(erros.stream().anyMatch(e -> e.contains("NAO_EXISTE")));
    }

    @Test
    void aceitaValoresValidosEValorNuloParaVoltarAoAmbiente() {
        Map<String, String> v = new HashMap<>();
        v.put("OSV_TIMEOUT", "30");
        v.put("TRIVY_ENABLED", null);
        assertTrue(SettingsService.validateAll(v).isEmpty());
    }

    @Test
    void recusaPedidoVazio() {
        assertEquals(1, SettingsService.validateAll(Map.of()).size());
        assertEquals(1, SettingsService.validateAll(null).size());
    }
}
