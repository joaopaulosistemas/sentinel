package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import com.sentinel.audit.model.Severity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FingerprintsTest {

    private static Finding dependency(String ids) {
        Finding f = new Finding();
        f.setRuleId("osv-g:lib");
        f.setFilePath("pom.xml");
        f.setCategory(Category.DEPENDENCY);
        f.setSeverity(Severity.HIGH);
        f.setTitle("Dependência vulnerável: g:lib");
        f.setCodeSnippet(ids);
        return f;
    }

    private static Finding code(String snippet) {
        Finding f = new Finding();
        f.setRuleId("r");
        f.setFilePath("A.java");
        f.setCategory(Category.SECURITY);
        f.setSeverity(Severity.HIGH);
        f.setTitle("t");
        f.setCodeSnippet(snippet);
        return f;
    }

    @Test
    void novaVulnerabilidadeNaMesmaDependenciaMudaAIdentidade() {
        assertNotEquals(Fingerprints.compute(dependency("CVE-1")), Fingerprints.compute(dependency("CVE-1, CVE-2")));
        assertEquals(Fingerprints.compute(dependency("CVE-1")), Fingerprints.compute(dependency("CVE-1")));
    }

    @Test
    void identidadeAntigaDeDependenciaIgnoraOsIds() {
        Finding legacy = dependency(null);
        assertEquals(Fingerprints.compute(legacy), Fingerprints.legacyDependency(dependency("CVE-1")));
    }

    @Test
    void ocorrenciasIguaisNoMesmoArquivoGanhamIdentidadesDistintas() {
        List<Finding> two = List.of(code("password = \"x\""), code("password = \"x\""));
        FindingLifecycleService.apply(two, FindingLifecycleService.Baseline.empty());
        assertNotEquals(two.get(0).getFingerprint(), two.get(1).getFingerprint());
        assertEquals(Fingerprints.compute(code("password = \"x\"")), two.get(0).getFingerprint(),
                "a primeira ocorrência mantém a identidade de sempre, preservando supressões existentes");
    }

    @Test
    void suprimirUmaOcorrenciaNaoEscondeAOutra() {
        String base = Fingerprints.compute(code("password = \"x\""));
        var baseline = new FindingLifecycleService.Baseline(null, Set.of(), Set.of(base));
        List<Finding> two = List.of(code("password = \"x\""), code("password = \"x\""));
        FindingLifecycleService.apply(two, baseline);
        assertEquals(FindingStatus.SUPPRESSED, two.get(0).getStatus());
        assertFalse(FindingStatus.SUPPRESSED == two.get(1).getStatus());
    }

    @Test
    void dependenciaDeAuditoriaAnteriorNaoViraNovaSoPorMudarOFormato() {
        String legacy = Fingerprints.legacyDependency(dependency("CVE-1"));
        var baseline = new FindingLifecycleService.Baseline(7L, Set.of(legacy), Set.of());
        List<Finding> list = List.of(dependency("CVE-1"));
        FindingLifecycleService.apply(list, baseline);
        assertEquals(Boolean.FALSE, list.get(0).getIsNew());
    }

    @Test
    void supressaoAntigaDeDependenciaNaoEscondeAsNovasVulnerabilidades() {
        String legacy = Fingerprints.legacyDependency(dependency("CVE-1"));
        var baseline = new FindingLifecycleService.Baseline(7L, Set.of(legacy), Set.of(legacy));
        List<Finding> list = List.of(dependency("CVE-1, CVE-2"));
        FindingLifecycleService.apply(list, baseline);
        assertTrue(list.get(0).getStatus() != FindingStatus.SUPPRESSED);
    }
}
