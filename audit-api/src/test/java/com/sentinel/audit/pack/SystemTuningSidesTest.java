package com.sentinel.audit.pack;

import com.sentinel.audit.model.Finding;
import com.sentinel.audit.tuning.TuningJson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Desligar o back-end ou o front-end de um sistema: regras, analisadores e achados do lado saem; o comum fica. */
class SystemTuningSidesTest {

    private static SystemTuning sides(String... off) {
        return new SystemTuning(Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(off));
    }

    private static Finding finding(String rule, String file, String tool) {
        Finding f = new Finding();
        f.setRuleId(rule);
        f.setFilePath(file);
        f.setToolSource(tool);
        return f;
    }

    private static AuditPolicy policy(SystemTuning t) {
        PackRegistry reg = TestPacks.registry();
        return reg.policyFor("default").withTuning(t, reg.snapshot().packs());
    }

    @Test
    void ladoDesligadoContaComoAjusteEFicaEmMinusculas() {
        SystemTuning t = sides(" Front ");
        assertEquals(Set.of("front"), t.disableSides());
        assertEquals(1, t.adjustments());
        assertFalse(t.isEmpty());
        assertTrue(t.sideDisabled("front"));
        assertFalse(t.sideDisabled("back"));
    }

    @Test
    void ajusteSemLadoContinuaIgualAoDeAntes() {
        assertTrue(new SystemTuning(Map.of(), Map.of(), Set.of(), Set.of(), Set.of()).disableSides().isEmpty());
        assertTrue(SystemTuning.EMPTY.disableSides().isEmpty());
    }

    @Test
    void jsonGravaELeOLado() {
        SystemTuning back = TuningJson.read(TuningJson.write(sides("back")));
        assertEquals(Set.of("back"), back.disableSides());
        assertEquals(Set.of(), TuningJson.read("{\"rules\":[]}").disableSides());
    }

    @Test
    void jsonRecusaLadoDesconhecidoEOsDoisLadosJuntos() {
        assertThrows(IllegalArgumentException.class, () -> TuningJson.read("{\"disableSides\":[\"meio\"]}"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TuningJson.read("{\"disableSides\":[\"back\",\"front\"]}"));
        assertTrue(e.getMessage().contains("os dois lados"), e.getMessage());
    }

    @Test
    void regrasDoLadoDesligadoSaemEAsDoOutroLadoFicam() {
        AuditPolicy semFront = policy(sides("front"));
        List<Finding> out = semFront.apply(List.of(
                finding("sentinel-ng-bypass-security", "web/app.ts", "Opengrep"),
                finding("sentinel-jpa-n-mais-um", "src/Repo.java", "Opengrep"),
                finding("sentinel-docker-sem-user", "Dockerfile", "Opengrep")));
        assertEquals(List.of("sentinel-jpa-n-mais-um", "sentinel-docker-sem-user"), out.stream().map(Finding::getRuleId).toList());

        AuditPolicy semBack = policy(sides("back"));
        out = semBack.apply(List.of(
                finding("sentinel-ng-bypass-security", "web/app.ts", "Opengrep"),
                finding("sentinel-jpa-n-mais-um", "src/Repo.java", "Opengrep"),
                finding("sentinel-docker-sem-user", "Dockerfile", "Opengrep")));
        assertEquals(List.of("sentinel-ng-bypass-security", "sentinel-docker-sem-user"), out.stream().map(Finding::getRuleId).toList());
    }

    @Test
    void semLadoDesligadoNadaMudaNaPolitica() {
        PackRegistry reg = TestPacks.registry();
        AuditPolicy base = reg.policyFor("default");
        List<Finding> in = List.of(finding("sentinel-ng-bypass-security", "web/app.ts", "Opengrep"));
        assertEquals(1, base.apply(in).size());
        assertEquals(1, policy(sides()).apply(in).size());
    }

    @Test
    void analisadoresDoLadoDesligadoNaoRodamEOsComunsRodam() {
        AuditPolicy semFront = policy(sides("front"));
        assertTrue(semFront.analyzerDisabled("ESLint"));
        assertTrue(semFront.analyzerDisabled("Código morto"));
        assertFalse(semFront.analyzerDisabled("PMD"));
        assertFalse(semFront.analyzerDisabled("Gitleaks"));
        assertTrue(semFront.analyzerOffNote("ESLint").contains("front-end"));

        AuditPolicy semBack = policy(sides("back"));
        assertTrue(semBack.analyzerDisabled("PMD"));
        assertFalse(semBack.analyzerDisabled("ESLint"));
        assertNull(semBack.analyzerOffNote("Gitleaks"));
    }

    @Test
    void analisadorDesligadoNaMaoContinuaComONotaAntiga() {
        SystemTuning t = new SystemTuning(Map.of(), Map.of(), Set.of(), Set.of(), Set.of("Gitleaks"), Set.of(), Set.of());
        assertEquals("desligado nos ajustes deste sistema", policy(t).analyzerOffNote("Gitleaks"));
    }

    @Test
    void analisadorComumSoPerdeOsAchadosDosArquivosDoLadoDesligado() {
        AuditPolicy semFront = policy(sides("front"));
        List<Finding> out = semFront.apply(List.of(
                finding("clean-funcao-longa", "web/src/app.component.ts", "CleanCode"),
                finding("clean-funcao-longa", "web/src/Tela.tsx", "CleanCode"),
                finding("clean-funcao-longa", "src/Servico.java", "CleanCode"),
                finding("gitleaks-token", "web/src/config.ts", "Gitleaks")));
        assertEquals(List.of("src/Servico.java", "web/src/config.ts"), out.stream().map(Finding::getFilePath).toList());

        AuditPolicy semBack = policy(sides("back"));
        out = semBack.apply(List.of(
                finding("clean-funcao-longa", "src/Servico.java", "CleanCode"),
                finding("clean-funcao-longa", "web/src/app.component.ts", "CleanCode"),
                finding("dup", "src/Outro.java", "Duplicação")));
        assertEquals(List.of("web/src/app.component.ts"), out.stream().map(Finding::getFilePath).toList());
    }

    @Test
    void jscpdComoDuplicacaoSoPerdeOsAchadosDosArquivosDoLadoDesligado() {
        List<Finding> todos = List.of(
                finding("jscpd-duplicado", "web/src/estilo.scss", "jscpd"),
                finding("jscpd-duplicado", "src/Servico.java", "jscpd"));

        assertEquals(List.of("src/Servico.java"), policy(sides("front")).apply(todos).stream().map(Finding::getFilePath).toList());
        assertEquals(List.of("web/src/estilo.scss"), policy(sides("back")).apply(todos).stream().map(Finding::getFilePath).toList());
        assertEquals(2, policy(SystemTuning.EMPTY).apply(todos).size(), "sem lado desligado nada muda");
    }

    @Test
    void ferramentaExternaDeLadoDesligadoTemMotivoEAComumNao() {
        ToolSpec front = new ToolSpec("stylelint", "Stylelint", List.of(), "x/*.sarif", com.sentinel.audit.model.Category.QUALITY,
                Map.of(), 30, false, List.of(), RuleSide.FRONT);
        ToolSpec comum = new ToolSpec("sqlfluff", "SQLFluff", List.of(), "x/*.sarif", com.sentinel.audit.model.Category.QUALITY,
                Map.of(), 30, false, List.of(), RuleSide.COMMON);

        assertEquals("lado front-end desligado no escopo deste sistema", policy(sides("front")).toolOffNote(front));
        assertNull(policy(sides("front")).toolOffNote(comum));
        assertNull(policy(sides("back")).toolOffNote(front));
        assertNull(policy(SystemTuning.EMPTY).toolOffNote(front));
    }
}
