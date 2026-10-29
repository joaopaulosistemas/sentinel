package com.sentinel.audit.analyzer.evidence;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Histograma de memória salvo ({@code jcmd <pid> GC.class_histogram} ou {@code jmap -histo}): mostra quem está
 * ocupando o heap. Classes do próprio projeto com milhões de instâncias, ou mapas gigantes, indicam acúmulo.
 */
final class HeapChecks {

    private static final int CLASS_GROUP = 3;
    private static final int TOP_CLASSES = 8;
    /** Classe da aplicação com tantas vezes o limite de bytes ou mais vira alta. */
    private static final int HIGH_BYTES_MULTIPLIER = 5;


    private static final Pattern ROW = Pattern.compile("^\\s*\\d+:\\s+(\\d+)\\s+(\\d+)\\s+(\\S+)");
    static final long APP_INSTANCES = 100_000;
    static final long APP_BYTES = 100L * 1024 * 1024;
    static final long MAP_NODES = 5_000_000;
    private static final Set<String> MAP_CLASSES = Set.of(
            "java.util.HashMap$Node", "java.util.concurrent.ConcurrentHashMap$Node", "java.util.LinkedHashMap$Entry");

    private HeapChecks() {
    }

    record Entry(String clazz, long instances, long bytes) {}

    static List<Entry> parse(String text) {
        List<Entry> entries = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher m = ROW.matcher(line);
            if (m.find()) {
                entries.add(new Entry(m.group(CLASS_GROUP), Long.parseLong(m.group(1)), Long.parseLong(m.group(2))));
            }
        }
        return entries;
    }

    static List<Finding> run(List<Entry> entries, Set<String> appPackages, String file, AuditRun run) {
        List<Finding> out = new ArrayList<>();
        if (entries.isEmpty()) {
            return out;
        }
        long total = entries.stream().mapToLong(Entry::bytes).sum();
        StringBuilder top = new StringBuilder();
        entries.stream().sorted(Comparator.comparingLong(Entry::bytes).reversed()).limit(TOP_CLASSES).forEach(e ->
                top.append("\n").append(Ev.bytes(e.bytes())).append("  ").append(Ev.fmt(e.instances())).append("×  ").append(e.clazz()));
        out.add(Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, Severity.INFO, "evidencia-memoria-maiores-consumidores"), new Ev.Where(file, 1, null), "Heap de " + Ev.bytes(total) + ": maiores consumidores", "Quem mais ocupa memória no histograma " + file + " (de " + Ev.bytes(total) + " no total):" + top));
        for (Entry e : entries) {
            if (isApp(e.clazz(), appPackages) && (e.instances() >= APP_INSTANCES || e.bytes() >= APP_BYTES)) {
                out.add(Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, e.bytes() >= HIGH_BYTES_MULTIPLIER * APP_BYTES ? Severity.HIGH : Severity.MEDIUM, "evidencia-memoria-classe-acumulada"), new Ev.Where(file, 1, e.clazz()), Ev.fmt(e.instances()) + " instâncias de " + shortName(e.clazz()) + " (" + Ev.bytes(e.bytes()) + ")", "A classe do projeto " + e.clazz() + " tem " + Ev.fmt(e.instances()) + " instâncias vivas, ocupando "
                                + Ev.bytes(e.bytes()) + ". Se o número cresce a cada captura, há acúmulo: coleção estática, "
                                + "cache sem limite, listener nunca removido ou sessão guardada. Compare duas capturas "
                                + "separadas por minutos de carga."));
            } else if (MAP_CLASSES.contains(e.clazz()) && e.instances() >= MAP_NODES) {
                out.add(Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, Severity.MEDIUM, "evidencia-memoria-colecao-gigante"), new Ev.Where(file, 1, e.clazz()), Ev.fmt(e.instances()) + " entradas de mapa em memória (" + Ev.bytes(e.bytes()) + ")", "Há " + Ev.fmt(e.instances()) + " objetos " + shortName(e.clazz()) + ". É sinal de cache ou índice em "
                                + "memória sem limite. Use um cache com tamanho máximo e expiração (Caffeine) ou mova o dado "
                                + "para o banco."));
            }
        }
        return out;
    }

    private static boolean isApp(String clazz, Set<String> appPackages) {
        for (String p : appPackages) {
            if (clazz.startsWith(p + ".") || clazz.startsWith(p + "$")) {
                return true;
            }
        }
        return false;
    }

    private static String shortName(String clazz) {
        int dot = clazz.lastIndexOf('.');
        return dot >= 0 ? clazz.substring(dot + 1) : clazz;
    }
}
