package com.sentinel.audit.service;

import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.FindingStatus;
import org.jboss.logging.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ajustes que o próprio projeto auditado declara no arquivo {@code .sentinel.yml}, na raiz do repositório.
 * É o jeito de o time dizer, <b>dentro do código e com revisão</b>, o que não deve contar na nota:
 *
 * <pre>
 * version: 1
 * exclude:                      # caminhos (glob) que não entram na auditoria
 *   - "docs/**"
 *   - "audit-rules/packs/*&#47;tests/**"
 * suppress:                     # achados aceitos: continuam visíveis, mas não pesam na nota
 *   - rule: sentinel-java-retorna-null-colecao
 *     path: "src/main/java/pack/Yamls.java"      # opcional (glob); sem ele vale para o projeto todo
 *     reason: "null é o sinal de valor inválido" # obrigatório
 *     until: 2027-06-30                          # opcional; depois dessa data a supressão deixa de valer
 * </pre>
 *
 * <p>Transparência: o que sai por {@code exclude} é contado e informado; o que entra em {@code suppress} fica na lista
 * com o motivo, marcado como suprimido. Um arquivo inválido nunca derruba a auditoria: os problemas voltam em
 * {@link Result#problems()} e o que for ilegível é ignorado.
 *
 * <p>O repositório auditado é dado não confiável: o arquivo é lido só se for um arquivo comum (sem seguir link
 * simbólico), com tamanho limitado, e o YAML é interpretado com tipos básicos apenas.
 */
public final class ProjectConfig {

    private static final Logger LOG = Logger.getLogger(ProjectConfig.class);

    public static final String FILE_NAME = ".sentinel.yml";
    private static final long MAX_BYTES = 256L * 1024;
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_GLOB_LENGTH = 300;

    /** Supressão declarada no arquivo. {@code path} e {@code until} podem ser nulos. */
    public record Suppression(String rule, Pattern path, String reason, LocalDate until) {
        boolean expiredOn(LocalDate today) {
            return until != null && until.isBefore(today);
        }
    }

    /**
     * Resultado de aplicar a configuração.
     *
     * @param findings    o que segue para a nota e o relatório
     * @param excluded    quantos achados saíram por {@code exclude}
     * @param suppressed  quantos foram marcados como suprimidos
     * @param problems    avisos sobre o arquivo (entrada inválida, supressão vencida, ...)
     */
    public record Result(List<Finding> findings, int excluded, int suppressed, List<String> problems) {
        public boolean touched() {
            return excluded > 0 || suppressed > 0 || !problems.isEmpty();
        }

        /** Frase curta para o andamento da auditoria; vazia quando nada mudou. */
        public String summary() {
            if (!touched()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            if (excluded > 0) {
                parts.add(excluded + " ignorados por exclude");
            }
            if (suppressed > 0) {
                parts.add(suppressed + " suprimidos");
            }
            if (!problems.isEmpty()) {
                parts.add(problems.size() + " aviso(s) no " + FILE_NAME);
            }
            return " (" + FILE_NAME + ": " + String.join(", ", parts) + ")";
        }
    }

    private static final ProjectConfig EMPTY = new ProjectConfig(List.of(), List.of(), List.of());

    private final List<Pattern> excludes;
    private final List<Suppression> suppressions;
    private final List<String> problems;

    private ProjectConfig(List<Pattern> excludes, List<Suppression> suppressions, List<String> problems) {
        this.excludes = excludes;
        this.suppressions = suppressions;
        this.problems = problems;
    }

    public static ProjectConfig empty() {
        return EMPTY;
    }

    /** Lê o {@code .sentinel.yml} da raiz do projeto; ausente ou ilegível = configuração vazia (com aviso, se for o caso). */
    public static ProjectConfig load(Path root) {
        Path file = root.resolve(FILE_NAME);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return EMPTY;
        }
        try {
            if (Files.size(file) > MAX_BYTES) {
                return withProblem(FILE_NAME + " passa de " + (MAX_BYTES / 1024) + " KB e foi ignorado");
            }
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            LOG.warnf("Não foi possível ler o %s: %s", FILE_NAME, e.getMessage());
            return withProblem(FILE_NAME + " ilegível: " + e.getClass().getSimpleName());
        }
    }

    private static ProjectConfig withProblem(String message) {
        return new ProjectConfig(List.of(), List.of(), List.of(message));
    }

    /** Interpreta o texto do arquivo (público para teste e para a prévia de configuração). */
    public static ProjectConfig parse(String text) {
        List<String> problems = new ArrayList<>();
        Object root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(20);
            options.setCodePointLimit((int) MAX_BYTES);
            root = new Yaml(new SafeConstructor(options)).load(text);
        } catch (RuntimeException e) {
            return withProblem(FILE_NAME + " não é um YAML válido");
        }
        if (root == null) {
            return EMPTY;
        }
        if (!(root instanceof Map<?, ?> map)) {
            return withProblem(FILE_NAME + " deve ser um mapa com as chaves exclude e suppress");
        }
        List<Pattern> excludes = parseExcludes(map.get("exclude"), problems);
        List<Suppression> suppressions = parseSuppressions(map.get("suppress"), problems);
        return new ProjectConfig(excludes, suppressions, List.copyOf(problems));
    }

    private static List<Pattern> parseExcludes(Object value, List<String> problems) {
        List<Pattern> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        if (!(value instanceof List<?> list)) {
            problems.add("exclude deve ser uma lista de caminhos");
            return out;
        }
        for (Object item : list.stream().limit(MAX_ENTRIES).toList()) {
            Pattern p = item instanceof String s ? compileGlob(s) : null;
            if (p == null) {
                problems.add("exclude: entrada ignorada (use um texto de até " + MAX_GLOB_LENGTH + " caracteres): " + item);
            } else {
                out.add(p);
            }
        }
        return out;
    }

    private static List<Suppression> parseSuppressions(Object value, List<String> problems) {
        List<Suppression> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        if (!(value instanceof List<?> list)) {
            problems.add("suppress deve ser uma lista");
            return out;
        }
        for (Object item : list.stream().limit(MAX_ENTRIES).toList()) {
            Suppression s = item instanceof Map<?, ?> m ? parseSuppression(m, problems) : null;
            if (s != null) {
                out.add(s);
            } else if (!(item instanceof Map)) {
                problems.add("suppress: cada item deve ser um mapa com rule e reason");
            }
        }
        return out;
    }

    private static Suppression parseSuppression(Map<?, ?> m, List<String> problems) {
        String rule = text(m.get("rule"));
        String reason = text(m.get("reason"));
        if (rule == null || reason == null) {
            problems.add("suppress: rule e reason são obrigatórios (sem motivo não há supressão): " + (rule == null ? "?" : rule));
            return null;
        }
        Pattern path = null;
        String rawPath = text(m.get("path"));
        if (rawPath != null) {
            path = compileGlob(rawPath);
            if (path == null) {
                problems.add("suppress " + rule + ": path inválido, a entrada foi ignorada");
                return null;
            }
        }
        LocalDate until = null;
        Object rawUntil = m.get("until");
        if (rawUntil != null) {
            until = parseDate(rawUntil);
            if (until == null) {
                problems.add("suppress " + rule + ": until deve ser uma data AAAA-MM-DD, a entrada foi ignorada");
                return null;
            }
        }
        return new Suppression(rule, path, reason, until);
    }

    private static String text(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).strip();
        return s.isEmpty() ? null : s;
    }

    /** O SnakeYAML devolve data sem aspas como {@link java.util.Date}; com aspas, como texto. */
    private static LocalDate parseDate(Object o) {
        if (o instanceof java.util.Date d) {
            return d.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        }
        try {
            return LocalDate.parse(String.valueOf(o).strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Glob simples sobre caminhos com {@code /}: {@code *} não atravessa pastas, {@code **} atravessa, {@code ?} é um
     * caractere. Sem barra, vale para um arquivo ou pasta com esse nome em qualquer lugar; terminando em {@code /},
     * vale para tudo dentro da pasta. Devolve {@code null} se o texto for vazio ou grande demais.
     */
    static Pattern compileGlob(String glob) {
        String g = glob == null ? "" : glob.strip().replace('\\', '/');
        if (g.isEmpty() || g.length() > MAX_GLOB_LENGTH) {
            return null;
        }
        while (g.startsWith("./")) {
            g = g.substring(2);
        }
        boolean anywhere = !g.contains("/");
        if (g.endsWith("/")) {
            g = g + "**";
        }
        StringBuilder re = new StringBuilder(anywhere ? "^(?:.*/)?" : "^");
        for (int i = 0; i < g.length(); i++) {
            char c = g.charAt(i);
            if (c == '*' && i + 1 < g.length() && g.charAt(i + 1) == '*') {
                boolean slashAfter = i + 2 < g.length() && g.charAt(i + 2) == '/';
                re.append(slashAfter ? "(?:.*/)?" : ".*");
                i += slashAfter ? 2 : 1;
            } else if (c == '*') {
                re.append("[^/]*");
            } else if (c == '?') {
                re.append("[^/]");
            } else {
                re.append(Pattern.quote(String.valueOf(c)));
            }
        }
        re.append(anywhere ? "(?:/.*)?$" : "$");
        try {
            return Pattern.compile(re.toString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public boolean isEmpty() {
        return excludes.isEmpty() && suppressions.isEmpty() && problems.isEmpty();
    }

    public List<Suppression> suppressions() {
        return suppressions;
    }

    /** Aplica {@code exclude} (retira) e {@code suppress} (marca como suprimido, com o motivo na descrição). */
    public Result apply(List<Finding> findings, LocalDate today) {
        List<String> notes = new ArrayList<>(problems);
        List<Suppression> live = new ArrayList<>();
        for (Suppression s : suppressions) {
            if (s.expiredOn(today)) {
                notes.add("suppress " + s.rule() + ": venceu em " + s.until() + " e deixou de valer");
            } else {
                live.add(s);
            }
        }
        List<Finding> kept = new ArrayList<>();
        int excluded = 0;
        int suppressed = 0;
        for (Finding f : findings) {
            String path = normalize(f.getFilePath());
            if (matchesAny(excludes, path)) {
                excluded++;
                continue;
            }
            kept.add(f);
            Suppression hit = firstMatch(live, f, path);
            if (hit != null && f.getStatus() != FindingStatus.SUPPRESSED) {
                f.setStatus(FindingStatus.SUPPRESSED);
                f.setDescription(append(f.getDescription(), "Suprimido em " + FILE_NAME + ": " + hit.reason()
                        + (hit.until() == null ? "" : " (até " + hit.until() + ")")));
                suppressed++;
            }
        }
        return new Result(kept, excluded, suppressed, List.copyOf(notes));
    }

    private static Suppression firstMatch(List<Suppression> live, Finding f, String path) {
        for (Suppression s : live) {
            if (ruleMatches(s.rule(), f.getRuleId()) && (s.path() == null || s.path().matcher(path).matches())) {
                return s;
            }
        }
        return null;
    }

    /**
     * O id da regra pode vir com prefixo do pacote (ex.: {@code audit-rules.rules.x.minha-regra}); vale o final.
     * Terminando em {@code *}, vale como prefixo (ex.: {@code gitleaks-*} cobre todas as regras do Gitleaks).
     */
    static boolean ruleMatches(String wanted, String actual) {
        if (actual == null) {
            return false;
        }
        if (wanted.endsWith("*")) {
            String prefix = wanted.substring(0, wanted.length() - 1);
            return actual.startsWith(prefix) || actual.contains("." + prefix);
        }
        return actual.equals(wanted) || actual.endsWith("." + wanted);
    }

    private static boolean matchesAny(List<Pattern> patterns, String path) {
        for (Pattern p : patterns) {
            if (p.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String path) {
        String p = path == null ? "" : path.replace('\\', '/');
        while (p.startsWith("./") || p.startsWith("/")) {
            p = p.startsWith("./") ? p.substring(2) : p.substring(1);
        }
        return p;
    }

    private static String append(String text, String extra) {
        return text == null || text.isBlank() ? extra : text + "\n" + extra;
    }
}
