package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.structure.SourceFiles.Src;
import com.sentinel.audit.analyzer.structure.UnusedExports.Unused;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnusedExportsTest {

    @Test
    void separaCodigoMortoDeExportDesnecessarioEIgnoraFrameworkEPadrao() {
        List<Src> prod = List.of(
                new Src("src/util.ts", "export const used = 1;\nexport function dead() {}\nexport interface Props {}\n"
                        + "export default function x() {}"),
                new Src("src/app.ts", "import { used } from './util';\nconst p: Props = {};\nused;"),
                new Src("src/svc.ts", "@Injectable()\nexport class Svc {}\nexport class Orphan {}"));
        List<Src> all = new ArrayList<>(prod);
        all.add(new Src("src/x.test.ts", "import { dead2 } from './util';"));

        List<Unused> found = UnusedExports.find(prod, all, 50);

        assertEquals(List.of("dead", "Orphan"), found.stream().map(Unused::name).toList());
        assertFalse(found.get(0).usedInside());
    }

    @Test
    void exportUsadoSoNoProprioArquivoPedeApenasTirarOExport() {
        List<Src> prod = List.of(new Src("src/a.ts", "export interface Opcoes { x: number }\nconst o: Opcoes = { x: 1 };\no;"));
        List<Unused> found = UnusedExports.find(prod, prod, 50);
        assertEquals(1, found.size());
        assertTrue(found.get(0).usedInside());
    }

    @Test
    void usoSoEmTesteContaComoUso() {
        List<Src> prod = List.of(new Src("src/util.ts", "export function soTeste() {}"));
        List<Src> all = List.of(prod.get(0), new Src("src/util.test.ts", "import { soTeste } from './util'; soTeste();"));
        assertTrue(UnusedExports.find(prod, all, 50).isEmpty());
    }

    @Test
    void arquivosDeEntradaEConfiguracaoFicamDeFora() {
        List<Src> prod = List.of(new Src("src/index.ts", "export const a = 1;"), new Src("vite.config.ts", "export const b = 1;"));
        assertTrue(UnusedExports.find(prod, prod, 50).isEmpty());
    }

    @Test
    void reconheceBibliotecaPublicada() {
        assertTrue(UnusedExports.looksLikeLibrary("{\"name\":\"x\",\"main\":\"dist/index.js\"}"));
        assertFalse(UnusedExports.looksLikeLibrary("{\"private\":true,\"main\":\"a\"}"));
        assertFalse(UnusedExports.looksLikeLibrary("{\"name\":\"app\"}"));
        assertFalse(UnusedExports.looksLikeLibrary(null));
    }
}
