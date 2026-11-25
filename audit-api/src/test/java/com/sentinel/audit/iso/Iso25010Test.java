package com.sentinel.audit.iso;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Iso25010Test {

    private static Finding f(Category c, String cve) {
        Finding f = new Finding();
        f.setCategory(c);
        f.setCveId(cve);
        return f;
    }

    @Test
    void cadaCategoriaCaiNaCaracteristicaCerta() {
        assertEquals(Iso25010.SEGURANCA, Iso25010.of(f(Category.SECURITY, null)));
        assertEquals(Iso25010.MANUTENIBILIDADE, Iso25010.of(f(Category.QUALITY, null)));
        assertEquals(Iso25010.MANUTENIBILIDADE, Iso25010.of(f(Category.ARCHITECTURE, null)));
        assertEquals(Iso25010.CONFIABILIDADE, Iso25010.of(f(Category.OBSERVABILITY, null)));
        assertEquals(Iso25010.EFICIENCIA_DESEMPENHO, Iso25010.of(f(Category.PERFORMANCE, null)));
    }

    @Test
    void dependenciaComCveEhSegurancaESemCveEhManutenibilidade() {
        assertEquals(Iso25010.SEGURANCA, Iso25010.of(f(Category.DEPENDENCY, "CVE-2026-1")));
        assertEquals(Iso25010.MANUTENIBILIDADE, Iso25010.of(f(Category.DEPENDENCY, null)));
        assertEquals(Iso25010.MANUTENIBILIDADE, Iso25010.of(f(Category.DEPENDENCY, "  ")));
    }

    @Test
    void todaCategoriaTemCaracteristicaAvaliada() {
        for (Category c : Category.values()) {
            assertTrue(Iso25010.of(f(c, null)).avaliada(), c + " cai numa característica não avaliada");
        }
    }

    @Test
    void identificadoresSaoUnicosEBuscaIgnoraMaiusculas() {
        long distintos = java.util.Arrays.stream(Iso25010.values()).map(Iso25010::id).distinct().count();
        assertEquals(Iso25010.values().length, distintos);
        assertEquals(Iso25010.SEGURANCA, Iso25010.byId(" Security "));
        assertNull(Iso25010.byId("nao-existe"));
        assertNull(Iso25010.byId(null));
    }

    @Test
    void quatroAvaliadasECincoNaoAvaliadas() {
        long avaliadas = java.util.Arrays.stream(Iso25010.values()).filter(Iso25010::avaliada).count();
        assertEquals(4, avaliadas);
        assertEquals(5, Iso25010.values().length - avaliadas);
    }

    @Test
    void pesosDeCadaPerfilSomamUm() {
        for (Iso25010Perfil p : Iso25010Perfil.values()) {
            double soma = java.util.Arrays.stream(Iso25010.values()).mapToDouble(p::peso).sum();
            assertEquals(1.0, soma, 1e-9, p.name());
        }
        assertEquals(Iso25010Perfil.PADRAO, Iso25010Perfil.parse(null));
        assertEquals(Iso25010Perfil.FINANCEIRO, Iso25010Perfil.parse(" Financeiro "));
        assertThrows(com.sentinel.audit.service.InvalidInputException.class, () -> Iso25010Perfil.parse("lua"));
    }
}
