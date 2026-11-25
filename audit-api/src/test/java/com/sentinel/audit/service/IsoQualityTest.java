package com.sentinel.audit.service;

import com.sentinel.audit.dto.IsoQualityDto;
import com.sentinel.audit.iso.Iso25010;
import com.sentinel.audit.iso.Iso25010Perfil;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IsoQualityTest {

    private final ScoringService scoring = new ScoringService();

    private static Finding f(Category c, Severity s, String rule, String cve) {
        Finding f = new Finding();
        f.setCategory(c);
        f.setSeverity(s);
        f.setRuleId(rule);
        f.setCveId(cve);
        return f;
    }

    private static IsoQualityDto.Caracteristica get(IsoQualityDto d, Iso25010 c) {
        return d.caracteristicas().stream().filter(x -> x.id().equals(c.id())).findFirst().orElseThrow();
    }

    @Test
    void semAchadosAsAvaliadasTiramCemEAsDemaisFicamNaoAvaliadas() {
        IsoQualityDto d = IsoQuality.calculate(scoring, List.of(), null, Iso25010Perfil.PADRAO);

        assertEquals(100.0, d.notaPonderada());
        assertEquals(Iso25010.values().length, d.caracteristicas().size());
        assertEquals(100.0, get(d, Iso25010.SEGURANCA).nota());
        IsoQualityDto.Caracteristica safety = get(d, Iso25010.SEGURANCA_OPERACIONAL);
        assertFalse(safety.avaliada());
        assertNull(safety.nota());
        assertNull(d.metasAtingidas());
    }

    @Test
    void notaDeSegurancaComSoAchadosDeSegurancaBateComANotaDaDimensao() {
        List<Finding> list = List.of(f(Category.SECURITY, Severity.HIGH, "r1", null), f(Category.SECURITY, Severity.CRITICAL, "r2", null));
        IsoQualityDto d = IsoQuality.calculate(scoring, list, null, Iso25010Perfil.PADRAO);

        double dimensao = scoring.calculateDimensionScore(list, Category.SECURITY);
        assertEquals(Math.round(dimensao * 10) / 10.0, get(d, Iso25010.SEGURANCA).nota());
        assertEquals(2, get(d, Iso25010.SEGURANCA).achados());
        assertEquals(1L, get(d, Iso25010.SEGURANCA).porSeveridade().get(Severity.CRITICAL));
    }

    @Test
    void dependenciaComCveReduzSegurancaESemCveReduzManutenibilidade() {
        List<Finding> list = List.of(f(Category.DEPENDENCY, Severity.HIGH, "dep", "CVE-2026-9"),
                f(Category.DEPENDENCY, Severity.HIGH, "velha", null));
        IsoQualityDto d = IsoQuality.calculate(scoring, list, null, Iso25010Perfil.PADRAO);

        assertTrue(get(d, Iso25010.SEGURANCA).nota() < 100);
        assertTrue(get(d, Iso25010.MANUTENIBILIDADE).nota() < 100);
        assertEquals(100.0, get(d, Iso25010.CONFIABILIDADE).nota());
    }

    @Test
    void perfilMudaANotaPonderadaEAPrioridadeMasNaoANotaDeCadaCaracteristica() {
        List<Finding> list = List.of(f(Category.SECURITY, Severity.CRITICAL, "r", null),
                f(Category.QUALITY, Severity.CRITICAL, "q", null));
        IsoQualityDto padrao = IsoQuality.calculate(scoring, list, null, Iso25010Perfil.PADRAO);
        IsoQualityDto interno = IsoQuality.calculate(scoring, list, null, Iso25010Perfil.INTERNO);

        assertEquals(get(padrao, Iso25010.SEGURANCA).nota(), get(interno, Iso25010.SEGURANCA).nota());
        assertNotEquals(padrao.notaPonderada(), interno.notaPonderada());
        assertEquals("interno", interno.perfil());
        // no perfil interno a manutenibilidade pesa mais, então vem primeiro; no padrão os pesos são iguais e o desempate é estável
        assertEquals("maintainability", interno.prioridade().get(0));
        assertEquals(4, interno.prioridade().size());
    }

    @Test
    void metasMarcamAtingiuEResumemEmMetasAtingidas() {
        List<Finding> list = List.of(f(Category.SECURITY, Severity.CRITICAL, "r", null));
        Map<Iso25010, Double> metas = IsoQuality.parseMetas("security:90, maintainability=75");

        IsoQualityDto d = IsoQuality.calculate(scoring, list, null, Iso25010Perfil.PADRAO, metas);

        assertEquals(Boolean.FALSE, get(d, Iso25010.SEGURANCA).atingiu());
        assertEquals(Boolean.TRUE, get(d, Iso25010.MANUTENIBILIDADE).atingiu());
        assertNull(get(d, Iso25010.CONFIABILIDADE).atingiu());
        assertEquals(Boolean.FALSE, d.metasAtingidas());

        IsoQualityDto ok = IsoQuality.calculate(scoring, List.of(), null, Iso25010Perfil.PADRAO, IsoQuality.parseMetas("security:90"));
        assertEquals(Boolean.TRUE, ok.metasAtingidas());
    }

    @Test
    void metasInvalidasViram400() {
        assertTrue(IsoQuality.parseMetas(null).isEmpty());
        assertTrue(IsoQuality.parseMetas("  ").isEmpty());
        for (String ruim : List.of("security", "lua:90", "security:abc", "security:101", "security:-1", "safety:50")) {
            assertThrows(InvalidInputException.class, () -> IsoQuality.parseMetas(ruim), ruim);
        }
    }

    @Test
    void scoreOfEquivaleAoCalculoPorDimensao() {
        List<Finding> list = List.of(f(Category.QUALITY, Severity.HIGH, "a", null), f(Category.QUALITY, Severity.LOW, "b", null),
                f(Category.SECURITY, Severity.HIGH, "c", null));
        List<Finding> soQualidade = list.stream().filter(x -> x.getCategory() == Category.QUALITY).toList();

        assertEquals(scoring.calculateDimensionScore(list, Category.QUALITY), scoring.scoreOf(soQualidade, 1.0), 1e-9);
    }
}
