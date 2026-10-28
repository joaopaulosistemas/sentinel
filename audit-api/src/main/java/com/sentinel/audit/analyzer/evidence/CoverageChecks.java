package com.sentinel.audit.analyzer.evidence;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Cobertura de testes a partir de relatórios já gerados pelo CI do projeto: JaCoCo (XML ou CSV) e lcov
 * (Jest/Karma/Vitest). Nada é executado: o relatório é só lido.
 */
final class CoverageChecks {

    static final int MAX_CLASS_FINDINGS = 25;
    private static final int MIN_UNTESTED_LINES = 40;

    private CoverageChecks() {
    }

    /** Cobertura de uma unidade (classe ou arquivo). */
    record Unit(String name, String path, long linesMissed, long linesCovered) {
        long lines() {
            return linesMissed + linesCovered;
        }
    }

    // ------------------------------------------------------------------ leitura

    static List<Unit> jacocoXml(String xml) {
        List<Unit> units = new ArrayList<>();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            Document d = b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList classes = d.getElementsByTagName("class");
            for (int i = 0; i < classes.getLength(); i++) {
                Element c = (Element) classes.item(i);
                String name = c.getAttribute("name");
                if (name.contains("$")) {
                    continue;                                            // classes internas somam na externa pelo sourcefile
                }
                long missed = 0;
                long covered = 0;
                for (Node ch = c.getFirstChild(); ch != null; ch = ch.getNextSibling()) {
                    if (ch instanceof Element e && e.getTagName().equals("counter") && "LINE".equals(e.getAttribute("type"))) {
                        missed = parse(e.getAttribute("missed"));
                        covered = parse(e.getAttribute("covered"));
                    }
                }
                units.add(new Unit(name.replace('/', '.'), "src/main/java/" + name + ".java", missed, covered));
            }
        } catch (Exception e) {
            return List.of();
        }
        return units;
    }

    static List<Unit> jacocoCsv(List<Csv.Row> rows) {
        List<Unit> units = new ArrayList<>();
        for (Csv.Row r : rows) {
            String pkg = r.get("package");
            String cls = r.get("class");
            if (cls.isBlank() || cls.contains("$")) {
                continue;
            }
            String name = pkg + "." + cls;
            units.add(new Unit(name, "src/main/java/" + name.replace('.', '/') + ".java",
                    (long) r.number("line_missed"), (long) r.number("line_covered")));
        }
        return units;
    }

    static List<Unit> lcov(String text) {
        List<Unit> units = new ArrayList<>();
        String file = "";
        boolean open = false;
        long lf = 0;
        long lh = 0;
        for (String line : text.split("\\R")) {
            if (line.startsWith("SF:")) {
                file = line.substring(3).strip();
                open = true;
                lf = 0;
                lh = 0;
            } else if (line.startsWith("LF:")) {
                lf = parse(line.substring(3));
            } else if (line.startsWith("LH:")) {
                lh = parse(line.substring(3));
            } else if (line.startsWith("end_of_record") && open) {
                String f = file.replace('\\', '/');
                units.add(new Unit(f, f, lf - lh, lh));
                open = false;
            }
        }
        return units;
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ verificações

    static List<Finding> run(List<Unit> units, String reportFile, double minLineCoverage, AuditRun run) {
        List<Finding> out = new ArrayList<>();
        long missed = units.stream().mapToLong(Unit::linesMissed).sum();
        long covered = units.stream().mapToLong(Unit::linesCovered).sum();
        long total = missed + covered;
        if (total == 0) {
            return out;
        }
        double pct = 100.0 * covered / total;
        if (pct < minLineCoverage) {
            out.add(Ev.finding(run, Category.QUALITY, pct < minLineCoverage / 2 ? Severity.HIGH : Severity.MEDIUM,
                    "evidencia-cobertura-baixa",
                    "Cobertura de testes de " + Ev.fmt(Math.round(pct * 10) / 10.0) + "% (mínimo " + Ev.fmt(minLineCoverage) + "%)",
                    "O relatório " + reportFile + " mostra " + Ev.fmt(covered) + " de " + Ev.fmt(total) + " linhas cobertas por teste. "
                            + "Abaixo do mínimo combinado, refatorar e corrigir bugs fica arriscado. Comece pelas classes "
                            + "maiores sem teste, listadas nos achados seguintes.",
                    reportFile, 1, null));
        }
        units.stream().filter(u -> u.linesCovered() == 0 && u.lines() >= MIN_UNTESTED_LINES)
                .sorted(Comparator.comparingLong(Unit::lines).reversed()).limit(MAX_CLASS_FINDINGS).forEach(u ->
                        out.add(Ev.finding(run, Category.QUALITY, u.lines() >= 150 ? Severity.MEDIUM : Severity.LOW,
                                "evidencia-cobertura-classe-sem-teste",
                                "Nenhum teste exercita " + shortName(u.name()) + " (" + u.lines() + " linhas)",
                                u.name() + " tem " + u.lines() + " linhas e 0% de cobertura no relatório. Quanto maior a "
                                        + "classe sem teste, maior o risco de uma mudança quebrar algo sem aviso.",
                                u.path(), 1, null)));
        return out;
    }

    private static String shortName(String name) {
        int dot = Math.max(name.lastIndexOf('.'), name.lastIndexOf('/'));
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
