package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.service.Fingerprints;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Os quatro analisadores estruturais de ponta a ponta, sobre um projeto de verdade em disco. */
class StructuralAnalyzersTest {

    private static void write(Path root, String rel, String text) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, text);
    }

    @Test
    void ciclosEmJavaETypeScriptViramAchadosComIdentidadeEstavel(@TempDir Path root) throws Exception {
        write(root, "api/src/main/java/com/x/model/A.java", "package com.x.model;\nimport com.x.service.S;\npublic class A {}");
        write(root, "api/src/main/java/com/x/service/S.java", "package com.x.service;\nimport com.x.model.A;\npublic class S {}");
        write(root, "web/src/a.ts", "import { b } from './b';\nexport const a = 1;");
        write(root, "web/src/b.ts", "import { a } from './a';\nexport const b = 1;");
        write(root, "api/src/test/java/com/x/other/T.java", "package com.x.other;\nimport com.x.model.A;\nclass T {}");

        List<Finding> fs = new DependencyCyclesAnalyzer().analyze(root, new AuditRun()).findings();

        assertEquals(2, fs.size());
        Finding pkg = fs.stream().filter(f -> f.getRuleId().equals("sentinel-arq-ciclo-pacotes")).findFirst().orElseThrow();
        assertEquals("api/src/main/java/com/x/model/A.java", pkg.getFilePath());
        assertEquals(Severity.MEDIUM, pkg.getSeverity());
        assertTrue(pkg.getDescription().contains("com.x.service"));
        Finding file = fs.stream().filter(f -> f.getRuleId().equals("sentinel-arq-ciclo-arquivos")).findFirst().orElseThrow();
        assertEquals("web/src/a.ts", file.getFilePath());
        assertEquals(Fingerprints.compute(pkg), Fingerprints.compute(pkg), "mesma identidade entre auditorias");
    }

    @Test
    void duplicacaoApontaOBlocoNaCopiaESoEmProducao(@TempDir Path root) throws Exception {
        String block = "int total = 0;\nfor (Item i : items) {\n  total += i.price();\n  if (i.discount() > 0) {\n"
                + "    total -= i.discount();\n  }\n}\nlog.info(\"total {}\", total);\nreturn applyTax(total, rate);\n"
                + "validate(total);\nregister(total);\nnotify(total);\naudit(total);\nflush(total);\nclose(total);\ncache(total);\nemit(total);\ndone(total);\n";
        write(root, "src/main/java/A.java", "class A {\n void m() {\n" + block + " }\n}");
        write(root, "src/main/java/B.java", "class B {\n void n() {\n" + block + " }\n}");
        write(root, "src/test/java/CTest.java", "class CTest {\n void n() {\n" + block + " }\n}");

        List<Finding> fs = new DuplicationAnalyzer().analyze(root, new AuditRun()).findings();

        assertEquals(1, fs.size());
        assertEquals("src/main/java/B.java", fs.get(0).getFilePath());
        assertEquals("sentinel-dup-bloco", fs.get(0).getRuleId());
        assertTrue(fs.get(0).getTitle().contains("src/main/java/A.java"));
    }

    @Test
    void codigoMortoSeparaMortoDeExportDesnecessarioEIgnoraBiblioteca(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"name\":\"app\",\"private\":true}");
        write(root, "src/util.ts", "export const used = 1;\nexport function dead() {}\nexport interface Props { a: number }\nconst p: Props = { a: 1 };\np;");
        write(root, "src/app.ts", "import { used } from './util';\nused;");

        List<Finding> fs = new DeadCodeAnalyzer().analyze(root, new AuditRun()).findings();

        assertEquals(2, fs.size());
        assertEquals(Severity.LOW, fs.stream().filter(f -> f.getRuleId().equals("sentinel-morto-export")).findFirst().orElseThrow().getSeverity());
        assertEquals(Severity.INFO, fs.stream().filter(f -> f.getRuleId().equals("sentinel-morto-export-interno")).findFirst().orElseThrow().getSeverity());

        write(root, "package.json", "{\"name\":\"lib\",\"main\":\"dist/index.js\"}");
        assertTrue(new DeadCodeAnalyzer().analyze(root, new AuditRun()).findings().isEmpty(), "biblioteca: exports são API pública");
    }

    @Test
    void pontoQuenteCruzaHistoricoDoGitComComplexidade(@TempDir Path root) throws Exception {
        Assumptions.assumeTrue(git(root, "init", "-q"), "git indisponível");
        git(root, "config", "user.email", "t@t");
        git(root, "config", "user.name", "t");
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 18; i++) {
            body.append("    if (x == ").append(i).append(") { run(").append(i).append("); }\n");
        }
        for (int c = 0; c < 22; c++) {
            write(root, "src/main/java/Quente.java", "class Quente {\n  void m(int x) {\n" + body + "    // v" + c + "\n  }\n}\n");
            write(root, "src/main/java/Frio.java", "class Frio { void m(int x) {" + "if (x==1){} ".repeat(20) + "} }\n");   // complexo, mas nunca muda
            git(root, "add", "-A");
            git(root, "commit", "-q", "-m", "c" + c, "--allow-empty");
        }

        List<Finding> fs = new HotspotAnalyzer().analyze(root, new AuditRun()).findings();

        assertEquals(1, fs.size(), "só o arquivo que muda sempre: " + fs);
        assertEquals("src/main/java/Quente.java", fs.get(0).getFilePath());
        assertEquals("sentinel-hotspot", fs.get(0).getRuleId());
    }

    @Test
    void semHistoricoSuficienteNaoApontaNada(@TempDir Path root) throws Exception {
        write(root, "src/A.java", "class A { }");
        assertTrue(new HotspotAnalyzer().analyze(root, new AuditRun()).findings().isEmpty());
    }

    @Test
    void osQuatroSuportamAsStacksCertas() {
        assertTrue(new DependencyCyclesAnalyzer().supports(Stack.JAVA));
        assertTrue(new DuplicationAnalyzer().supports(Stack.MIXED));
        assertTrue(new HotspotAnalyzer().supports(Stack.JAVASCRIPT));
        assertFalse(new DeadCodeAnalyzer().supports(Stack.JAVA));
        assertTrue(new DeadCodeAnalyzer().supports(Stack.JAVASCRIPT));
    }

    private static boolean git(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", dir.toString()));
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
