package com.sentinel.audit.analyzer.structure;

import com.sentinel.audit.analyzer.structure.Duplication.Clone;
import com.sentinel.audit.analyzer.structure.SourceFiles.Src;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DuplicationTest {

    private static final String BLOCK = "int total = 0;\nfor (Item i : items) {\n  total += i.price();\n"
            + "  if (i.discount() > 0) {\n    total -= i.discount();\n  }\n}\nlog.info(\"total {}\", total);\n"
            + "return applyTax(total, rate);\nvalidate(total);\nregister(total);\nnotify(total);\n";

    @Test
    void achaBlocoIgualEmDoisArquivosIgnorandoLicencaImportsEComentarios() {
        List<Src> files = List.of(
                new Src("A.java", "/* licença */\npackage x;\nimport y.Z;\nclass A {\n void m(){\n" + BLOCK + " }\n}"),
                new Src("B.java", "package x;\nclass B {\n // outro\n void n(){\n" + BLOCK + " }\n}"),
                new Src("C.java", "class C { void q(){ int z = 1; int w = 2; foo(z); } }"));

        List<Clone> clones = Duplication.find(files, 6, 10, 50);

        assertEquals(1, clones.size());
        assertEquals("A.java", clones.get(0).fileA());
        assertEquals("B.java", clones.get(0).fileB());
        assertEquals(10, clones.get(0).lines());
    }

    @Test
    void cabecalhoDeLicencaERepeticaoTrivialNaoViramClone() {
        String lic = "/*\n * Copyright 2026 Fulano\n * Licensed under the Apache License\n * you may not use this file\n"
                + " * distributed under the License\n * WITHOUT WARRANTIES\n * See the License\n * limitations\n"
                + " * under the License\n * more text\n * more text\n * more text\n */\n";
        List<Src> files = List.of(
                new Src("A.java", lic + "class A {}"),
                new Src("B.java", lic + "class B {}"),
                new Src("T.java", "a.b();\n".repeat(40)));
        assertTrue(Duplication.find(files, 6, 10, 50).isEmpty());
    }

    @Test
    void gettersESettersNaoContamComoDuplicacao() {
        StringBuilder acc = new StringBuilder();
        for (String n : List.of("Nome", "Idade", "Cidade", "Pais", "Rua", "Numero", "Cep", "Bairro", "Estado", "Fone", "Email", "Cpf")) {
            acc.append("public String get").append(n).append("() {\n  return ").append(n.toLowerCase()).append(";\n}\n");
            acc.append("public void set").append(n).append("(String v) {\n  this.").append(n.toLowerCase()).append(" = v;\n}\n");
        }
        List<Src> files = List.of(new Src("A.java", "class A {\n" + acc + "}"), new Src("B.java", "class B {\n" + acc + "}"));
        assertTrue(Duplication.find(files, 6, 10, 50).isEmpty());
    }

    @Test
    void respeitaOLimiteDeAchados() {
        List<Src> files = List.of(
                new Src("A.java", "class A {\n" + BLOCK + "}"),
                new Src("B.java", "class B {\n" + BLOCK + "}"),
                new Src("C.java", "class C {\n" + BLOCK + "}"));
        assertEquals(1, Duplication.find(files, 6, 10, 1).size());
        assertEquals(2, Duplication.find(files, 6, 10, 10).size());
    }
}
