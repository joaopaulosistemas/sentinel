package com.sentinel.audit.analyzer.deps;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyExtractorTest {

    @TempDir
    Path dir;

    private void write(String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private static boolean has(List<Dependency> deps, String eco, String name, String version) {
        return deps.stream().anyMatch(d -> d.ecosystem().equals(eco) && d.name().equals(name) && d.version().equals(version));
    }

    @Test
    void lePomComPropriedadesEIgnoraVersaoDeBom() throws IOException {
        write("pom.xml", "<project><properties><log4j.v>2.14.1</log4j.v></properties><dependencies>"
                + "<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><version>${log4j.v}</version></dependency>"
                + "<dependency><groupId>g</groupId><artifactId>semversao</artifactId></dependency>"
                + "</dependencies></project>");
        List<Dependency> deps = DependencyExtractor.extract(dir);
        assertEquals(1, deps.size());
        assertTrue(has(deps, "Maven", "org.apache.logging.log4j:log4j-core", "2.14.1"));
        assertEquals("log4j-core", deps.get(0).shortName());
    }

    @Test
    void lePackageLockV3ComDependenciasTransitivas() throws IOException {
        write("package-lock.json", "{\"lockfileVersion\":3,\"packages\":{\"\":{\"name\":\"app\"},"
                + "\"node_modules/lodash\":{\"version\":\"4.17.15\"},"
                + "\"node_modules/a/node_modules/minimist\":{\"version\":\"1.2.0\"},"
                + "\"node_modules/local\":{\"link\":true,\"resolved\":\"../local\"}}}");
        List<Dependency> deps = DependencyExtractor.extract(dir);
        assertEquals(2, deps.size());
        assertTrue(has(deps, "npm", "lodash", "4.17.15"));
        assertTrue(has(deps, "npm", "minimist", "1.2.0"));
    }

    @Test
    void lePackageLockV1() throws IOException {
        write("package-lock.json", "{\"lockfileVersion\":1,\"dependencies\":{\"axios\":{\"version\":\"0.21.0\","
                + "\"dependencies\":{\"follow-redirects\":{\"version\":\"1.5.0\"}}}}}");
        List<Dependency> deps = DependencyExtractor.extract(dir);
        assertTrue(has(deps, "npm", "axios", "0.21.0"));
        assertTrue(has(deps, "npm", "follow-redirects", "1.5.0"));
    }

    @Test
    void packageJsonSoEUsadoSemLockfile() throws IOException {
        write("a/package.json", "{\"dependencies\":{\"lodash\":\"^4.17.15\",\"x\":\"latest\"}}");
        write("b/package.json", "{\"dependencies\":{\"lodash\":\"^1.0.0\"}}");
        write("b/package-lock.json", "{\"lockfileVersion\":3,\"packages\":{\"node_modules/lodash\":{\"version\":\"4.17.21\"}}}");
        List<Dependency> deps = DependencyExtractor.extract(dir);
        assertTrue(has(deps, "npm", "lodash", "4.17.15"));   // a/: declarado
        assertTrue(has(deps, "npm", "lodash", "4.17.21"));   // b/: lockfile
        assertFalse(has(deps, "npm", "lodash", "1.0.0"));
        assertEquals(2, deps.size());
    }

    @Test
    void leGradleRequirementsECsproj() throws IOException {
        write("build.gradle", "dependencies {\n implementation 'org.yaml:snakeyaml:1.30'\n api(\"com.google.guava:guava:31.0\")\n"
                + " implementation \"x:y:$ver\"\n}");
        write("requirements.txt", "Django==3.0.1\nrequests>=2\n# comentario\nPyYAML[extra]==5.3\n");
        write("App/App.csproj", "<Project><ItemGroup><PackageReference Include=\"Newtonsoft.Json\" Version=\"12.0.1\" />"
                + "</ItemGroup></Project>");
        List<Dependency> deps = DependencyExtractor.extract(dir);
        assertTrue(has(deps, "Maven", "org.yaml:snakeyaml", "1.30"));
        assertTrue(has(deps, "Maven", "com.google.guava:guava", "31.0"));
        assertFalse(deps.stream().anyMatch(d -> d.name().equals("x:y")));   // versão é variável
        assertTrue(has(deps, "PyPI", "django", "3.0.1"));
        assertTrue(has(deps, "PyPI", "pyyaml", "5.3"));
        assertFalse(deps.stream().anyMatch(d -> d.name().equals("requests")));
        assertTrue(has(deps, "NuGet", "Newtonsoft.Json", "12.0.1"));
    }

    @Test
    void ignoraNodeModulesEPomMalFormado() throws IOException {
        write("node_modules/x/package.json", "{\"dependencies\":{\"lodash\":\"4.0.0\"}}");
        write("pom.xml", "<project><dependencies>");   // XML quebrado
        write("evil.xml", "x");
        write("sub/pom.xml", "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><project>&xxe;</project>");
        assertTrue(DependencyExtractor.extract(dir).isEmpty());
    }
}
