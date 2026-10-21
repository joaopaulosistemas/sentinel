package com.sentinel.audit.cli;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Converte os findings em JUnit XML: cada finding vira um teste que falhou.
 * Útil para Jenkins, GitLab e Azure DevOps mostrarem os problemas na aba de testes.
 */
final class JUnitWriter {

    private JUnitWriter() {}

    static String write(JsonNode findings, String suiteName) {
        int count = findings.size();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuites name=\"").append(esc(suiteName)).append("\" tests=\"").append(count)
           .append("\" failures=\"").append(count).append("\">\n");
        xml.append("  <testsuite name=\"").append(esc(suiteName)).append("\" tests=\"").append(count)
           .append("\" failures=\"").append(count).append("\" errors=\"0\" skipped=\"0\">\n");
        for (JsonNode f : findings) {
            String category = f.path("category").asText("FINDING");
            String title = f.path("title").asText("(sem título)");
            String severity = f.path("severity").asText("MEDIUM");
            String ruleId = f.path("ruleId").asText("");
            String file = f.path("filePath").asText("");
            int line = f.path("lineNumber").asInt(0);
            String location = file.isEmpty() ? "" : file + (line > 0 ? ":" + line : "");

            xml.append("    <testcase classname=\"").append(esc(category)).append("\" name=\"")
               .append(esc("[" + severity + "] " + title + (location.isEmpty() ? "" : " (" + location + ")")))
               .append("\">\n");
            xml.append("      <failure message=\"").append(esc(title)).append("\" type=\"").append(esc(severity)).append("\">");
            xml.append(esc("Regra: " + ruleId + "\nLocal: " + location + "\n" + f.path("description").asText("")));
            xml.append("</failure>\n    </testcase>\n");
        }
        xml.append("  </testsuite>\n</testsuites>\n");
        return xml.toString();
    }

    /** Escapa XML e remove caracteres de controle inválidos em XML 1.0. */
    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (c == '\n' || c == '\r' || c == '\t' || c >= 0x20) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
