package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do {@link OWASPDependencyAnalyzer}: comparação de versões (não só do nome) em pom.xml e package.json.
 */
class OWASPDependencyAnalyzerTest {

    @TempDir
    Path tempDir;

    private final OWASPDependencyAnalyzer analyzer = new OWASPDependencyAnalyzer();

    private static String pom(String properties, String dependencies) {
        return "<project><properties>" + properties + "</properties><dependencies>" + dependencies
                + "</dependencies></project>";
    }

    private static String dep(String artifactId, String version) {
        return "<dependency><groupId>g</groupId><artifactId>" + artifactId + "</artifactId>"
                + (version == null ? "" : "<version>" + version + "</version>") + "</dependency>";
    }

    private List<Finding> analyzePom(String content) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), content);
        return analyzer.analyze(tempDir, new AuditRun()).findings();
    }

    @Test
    void deveDetectarLog4jVulneravel() throws IOException {
        List<Finding> findings = analyzePom(pom("", dep("log4j-core", "2.14.1")));
        assertEquals(1, findings.size());
        assertEquals(Severity.CRITICAL, findings.get(0).getSeverity());
        assertEquals("CVE-2021-44228", findings.get(0).getCveId());
        assertEquals("pom.xml", findings.get(0).getFilePath());
    }

    @Test
    void naoDeveAcusarLog4jJaCorrigido() throws IOException {
        assertTrue(analyzePom(pom("", dep("log4j-core", "2.17.1"))).isEmpty());
        assertTrue(analyzePom(pom("", dep("log4j-core", "2.23.1"))).isEmpty());
    }

    @Test
    void naoDeveConfundirArtefatosComNomeParecido() throws IOException {
        // antes: content.contains("log4j") acusava qualquer artefato com esse trecho no nome
        assertTrue(analyzePom(pom("", dep("log4j-api-scala", "1.0.0") + dep("slf4j-log4j-bridge", "1.0"))).isEmpty());
    }

    @Test
    void deveResolverVersaoDefinidaEmPropriedade() throws IOException {
        List<Finding> vulneravel = analyzePom(
                pom("<jackson.version>2.13.0</jackson.version>", dep("jackson-databind", "${jackson.version}")));
        assertEquals(1, vulneravel.size());

        List<Finding> corrigido = analyzePom(
                pom("<jackson.version>2.13.4.2</jackson.version>", dep("jackson-databind", "${jackson.version}")));
        assertTrue(corrigido.isEmpty());
    }

    @Test
    void versaoGerenciadaPorBomNaoEhAvaliada() throws IOException {
        assertTrue(analyzePom(pom("", dep("spring-core", null))).isEmpty());
    }

    @Test
    void deveRespeitarALinhaDeReleaseDoSpring() {
        List<String> fixes = List.of("5.2.20", "5.3.18");
        assertTrue(OWASPDependencyAnalyzer.isVulnerable("5.2.19", fixes));
        assertFalse(OWASPDependencyAnalyzer.isVulnerable("5.2.20", fixes));
        assertTrue(OWASPDependencyAnalyzer.isVulnerable("5.3.17", fixes));
        assertFalse(OWASPDependencyAnalyzer.isVulnerable("5.3.18.RELEASE", fixes));
        assertFalse(OWASPDependencyAnalyzer.isVulnerable("6.1.0", fixes));
        assertTrue(OWASPDependencyAnalyzer.isVulnerable("4.3.30.RELEASE", fixes));
    }

    @Test
    void versaoSemLinhaCorrespondenteUsaAMaiorCorrecao() {
        List<String> fixes = List.of("2.3.2", "2.12.4", "2.17.1");
        assertTrue(OWASPDependencyAnalyzer.isVulnerable("2.14.1", fixes));
        assertTrue(OWASPDependencyAnalyzer.isVulnerable("2.16.0", fixes));
        assertFalse(OWASPDependencyAnalyzer.isVulnerable("2.20.0", fixes));
        assertTrue(OWASPDependencyAnalyzer.isVulnerable("2.12.3", fixes));
        assertFalse(OWASPDependencyAnalyzer.isVulnerable("2.12.4", fixes));
    }

    @Test
    void deveDetectarNpmVulneravelPelaVersaoDeclarada() throws IOException {
        Files.writeString(tempDir.resolve("package.json"),
                "{\"dependencies\":{\"lodash\":\"^4.17.15\",\"moment\":\"2.29.4\",\"react\":\"latest\"},"
                        + "\"devDependencies\":{\"minimist\":\"~1.2.0\"}}");
        List<Finding> findings = analyzer.analyze(tempDir, new AuditRun()).findings();
        assertEquals(2, findings.size());
        assertTrue(findings.stream().anyMatch(f -> "CVE-2021-23337".equals(f.getCveId())));
        assertTrue(findings.stream().anyMatch(f -> "CVE-2021-44906".equals(f.getCveId())));
    }

    @Test
    void deveIgnorarNodeModules() throws IOException {
        Path nm = tempDir.resolve("node_modules/lib");
        Files.createDirectories(nm);
        Files.writeString(nm.resolve("package.json"), "{\"dependencies\":{\"lodash\":\"4.0.0\"}}");
        assertTrue(analyzer.analyze(tempDir, new AuditRun()).findings().isEmpty());
    }

    @Test
    void pomMalFormadoNaoQuebraAAnalise() throws IOException {
        assertTrue(analyzePom("<project><dependencies><dependency>").isEmpty());
    }

    @Test
    void pomComDoctypeEhRejeitadoSemExpandirEntidades() throws IOException {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE p [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<project><dependencies>" + dep("log4j-core", "&x;") + "</dependencies></project>";
        assertTrue(analyzePom(xxe).isEmpty());
    }

    @Test
    void cleanNpmRange() {
        assertEquals("4.17.15", OWASPDependencyAnalyzer.cleanNpmRange("^4.17.15"));
        assertEquals("1.2.0", OWASPDependencyAnalyzer.cleanNpmRange(">=1.2.0 <2"));
        assertNull(OWASPDependencyAnalyzer.cleanNpmRange("latest"));
        assertNull(OWASPDependencyAnalyzer.cleanNpmRange("github:user/repo"));
    }
}
