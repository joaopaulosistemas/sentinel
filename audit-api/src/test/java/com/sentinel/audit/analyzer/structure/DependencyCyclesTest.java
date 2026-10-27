package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.structure.SourceFiles.Src;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyCyclesTest {

    @Test
    void achaCicloEntrePacotesJavaEIgnoraImportsDeFora() {
        List<Src> java = List.of(
                new Src("a/A.java", "package com.x.model;\nimport com.x.service.S;\nimport java.util.List;\nclass A {}"),
                new Src("b/S.java", "package com.x.service;\nimport com.x.model.A;\nclass S {}"),
                new Src("c/R.java", "package com.x.repo;\nimport com.x.model.A;\nimport static com.x.model.A.f;\nclass R {}"));

        List<List<String>> cycles = DependencyCycles.javaPackages(java);

        assertEquals(List.of(List.of("com.x.model", "com.x.service")), cycles);
    }

    @Test
    void semCicloNaoDevolveNada() {
        List<Src> java = List.of(
                new Src("a/A.java", "package com.x.model;\nclass A {}"),
                new Src("b/S.java", "package com.x.service;\nimport com.x.model.A;\nclass S {}"));
        assertTrue(DependencyCycles.javaPackages(java).isEmpty());
    }

    @Test
    void achaCicloEntreArquivosTypeScriptEIgnoraImportType() {
        List<Src> ts = List.of(
                new Src("src/a.ts", "import { b } from './b';\nexport const a = 1;"),
                new Src("src/b.ts", "import { c } from \"./c\";\nexport const b = 1;"),
                new Src("src/c/index.ts", "import type { X } from '../a';\nimport { b } from '../b';\nexport const c = 1;"),
                new Src("src/d.ts", "import { a } from './a';\nexport const d = 1;"));

        assertEquals(List.of(List.of("src/b.ts", "src/c/index.ts")), DependencyCycles.tsFiles(ts));
    }

    @Test
    void entendeImportEmVariasLinhasEIgnoraPacoteDeFora() {
        List<Src> ts = List.of(
                new Src("x.ts", "import {\n  a,\n  b\n} from './y';\nimport React from 'react';"),
                new Src("y.ts", "import { z } from './x';"));
        assertEquals(1, DependencyCycles.tsFiles(ts).size());
    }

    @Test
    void importQueSaiDaRaizNaoQuebra() {
        assertNull(DependencyCycles.resolve("a.ts", "../../fora", java.util.Set.of("a.ts")));
        assertEquals("src/util.ts", DependencyCycles.resolve("src/app/x.ts", "../util", java.util.Set.of("src/util.ts")));
    }

    @Test
    void grafoGrandeNaoEstouraAPilha() {
        java.util.Map<String, java.util.Set<String>> g = new java.util.TreeMap<>();
        int n = 50_000;
        for (int i = 0; i < n; i++) {
            g.put("n" + i, java.util.Set.of("n" + ((i + 1) % n)));
        }
        List<List<String>> comps = DependencyCycles.components(g);
        assertEquals(1, comps.size());
        assertEquals(n, comps.get(0).size());
    }
}
