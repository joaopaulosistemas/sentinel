package com.sentinel.audit.analyzer.quality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceSanitizerTest {

    private static long lines(String s) {
        return s.chars().filter(c -> c == '\n').count();
    }

    @Test
    void mantemTamanhoEQuebrasDeLinha() {
        String src = "int a = 1; // if (x) {\n/* for (;;) {\n } */ String s = \"{ if }\";\nchar c = '}';\n";
        String clean = SourceSanitizer.strip(src);
        assertEquals(src.length(), clean.length());
        assertEquals(lines(src), lines(clean));
    }

    @Test
    void removeComentariosEConteudoDeStrings() {
        String clean = SourceSanitizer.strip("a(); // if (x) { b(); }\nc(\"if { while }\"); /* case */ d('}');");
        assertFalse(clean.contains("if"));
        assertFalse(clean.contains("while"));
        assertFalse(clean.contains("case"));
        assertFalse(clean.contains("}"));
        assertTrue(clean.contains("a();"));
        assertTrue(clean.contains("c("));
        assertTrue(clean.contains("d("));
    }

    @Test
    void textBlockETemplateLiteralMultilinha() {
        String java = "String t = \"\"\"\n  if (x) { for }\n  \"\"\"; int y = 1;";
        String cj = SourceSanitizer.strip(java);
        assertFalse(cj.contains("if"));
        assertTrue(cj.contains("int y = 1;"));
        assertEquals(lines(java), lines(cj));

        String ts = "const t = `linha ${a} {\n if }`; const z = 2;";
        String cts = SourceSanitizer.strip(ts);
        assertFalse(cts.contains("if"));
        assertTrue(cts.contains("const z = 2;"));
        assertEquals(lines(ts), lines(cts));
    }

    @Test
    void aspaEscapadaNaoEncerraAString() {
        String clean = SourceSanitizer.strip("x = \"a \\\" if { b\"; y();");
        assertFalse(clean.contains("if"));
        assertTrue(clean.contains("y();"));
    }

    @Test
    void stringNaoFechadaNaoEngoleOResto() {
        String clean = SourceSanitizer.strip("x = \"aberta;\nint depois = 1;\n");
        assertTrue(clean.contains("int depois = 1;"));
    }

    @Test
    void entradaVaziaOuSoComentario() {
        assertEquals("", SourceSanitizer.strip(""));
        assertEquals("      ", SourceSanitizer.strip("/* x */").substring(0, 6));
        assertDoesNotThrow(() -> SourceSanitizer.strip("/* nunca fecha"));
        assertDoesNotThrow(() -> SourceSanitizer.strip("\"\"\" nunca fecha"));
    }
}
