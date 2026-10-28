package com.sentinel.audit.analyzer.evidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê um dump só do esquema ({@code pg_dump --schema-only}) ou um script DDL e monta um modelo simples:
 * tabelas, colunas, chaves primárias, estrangeiras, únicas e índices. Não é um parser SQL completo: entende o que
 * as verificações precisam e ignora o resto (funções, views, triggers, permissões...).
 */
final class SchemaParser {

    private static final String COPY_PREFIX = "COPY ";
    private static final int INDEX_METHOD_GROUP = 4;
    private static final int INDEX_TABLE_GROUP = 3;


    record Column(String name, String type, boolean notNull) {}

    record ForeignKey(List<String> columns, String refTable, int line) {}

    static final class Table {
        final String name;
        final int line;
        final List<Column> columns = new ArrayList<>();
        List<String> primaryKey = List.of();
        final List<ForeignKey> foreignKeys = new ArrayList<>();
        final List<List<String>> uniques = new ArrayList<>();

        Table(String name, int line) {
            this.name = name;
            this.line = line;
        }

        Column column(String col) {
            for (Column c : columns) {
                if (c.name().equals(col)) {
                    return c;
                }
            }
            return null;
        }
    }

    /** Índice explícito. {@code columns} traz {@code null} no lugar de expressões ({@code lower(nome)}). */
    record Index(String name, String table, List<String> columns, boolean unique, String method, boolean partial, int line) {}

    record Schema(Map<String, Table> tables, List<Index> indexes) {}

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "^CREATE\\s+(?:GLOBAL\\s+|LOCAL\\s+)?(?:UNLOGGED\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.\"$]+)\\s*\\(",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "^CREATE\\s+(UNIQUE\\s+)?INDEX\\s+(?:CONCURRENTLY\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.\"$]+)\\s+ON\\s+(?:ONLY\\s+)?([\\w.\"$]+)\\s*(?:USING\\s+(\\w+)\\s*)?\\(",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ALTER_TABLE = Pattern.compile(
            "^ALTER\\s+TABLE\\s+(?:ONLY\\s+)?(?:IF\\s+EXISTS\\s+)?([\\w.\"$]+)\\s+ADD\\s+(?:CONSTRAINT\\s+[\\w\"$]+\\s+)?(PRIMARY\\s+KEY|FOREIGN\\s+KEY|UNIQUE)\\s*\\(",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern REFERENCES = Pattern.compile(
            "REFERENCES\\s+([\\w.\"$]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_END = Pattern.compile(
            "\\b(NOT\\s+NULL|NULL|DEFAULT|PRIMARY\\s+KEY|REFERENCES|UNIQUE|CHECK|CONSTRAINT|GENERATED|COLLATE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_INDEX_ELEMENT = Pattern.compile(
            "^(\"?[\\w$]+\"?)(\\s+(?:[\\w.]+_ops|ASC|DESC|NULLS\\s+(?:FIRST|LAST)|COLLATE\\s+\\S+))*$",
            Pattern.CASE_INSENSITIVE);

    private SchemaParser() {
    }

    // ------------------------------------------------------------------ divisão em comandos

    record Statement(String text, int line) {}

    private static final Pattern DOLLAR_TAG = Pattern.compile("\\$([A-Za-z_]\\w*)?\\$");

    /** Divide o script em comandos terminados por ';', respeitando aspas, comentários, $$...$$ e blocos COPY. */
    static List<Statement> split(String sql) {
        return new Splitter(sql).run();
    }

    /** Percorre o script um trecho por vez; cada {@code try...} consome o que reconhece e diz se o fez. */
    private static final class Splitter {
        private final String sql;
        private final int n;
        private final List<Statement> out = new ArrayList<>();
        private final StringBuilder cur = new StringBuilder();
        private int i;
        private int line = 1;
        private int startLine = -1;
        private boolean atLineStart = true;

        Splitter(String sql) {
            this.sql = sql;
            this.n = sql.length();
        }

        List<Statement> run() {
            while (i < n) {
                char c = sql.charAt(i);
                if (!consumeSpecial(c)) {
                    cur.append(c);
                    i++;
                }
            }
            String rest = cur.toString().strip();
            if (!rest.isEmpty()) {
                out.add(new Statement(rest, startLine < 0 ? line : startLine));
            }
            return out;
        }

        /** Meta-comando, quebra de linha, comentário, texto entre aspas, $$...$$ ou ';'. */
        private boolean consumeSpecial(char c) {
            if (atLineStart && c == '\\' && cur.toString().isBlank()) {       // meta-comando do psql (\connect, \restrict)
                skipToEndOfLine();
                return true;
            }
            if (c == '\n') {
                line++;
                atLineStart = true;
                cur.append(c);
                i++;
                return true;
            }
            if (!Character.isWhitespace(c)) {
                atLineStart = false;
            }
            if (isCommentStart(c)) {
                skipComment(c);
                return true;
            }
            if (startLine < 0 && !Character.isWhitespace(c)) {
                startLine = line;
            }
            return consumeQuoted(c) || consumeDollarQuoted(c) || consumeSemicolon(c);
        }

        private boolean isCommentStart(char c) {
            return i + 1 < n && ((c == '-' && sql.charAt(i + 1) == '-') || (c == '/' && sql.charAt(i + 1) == '*'));
        }

        private void skipToEndOfLine() {
            while (i < n && sql.charAt(i) != '\n') {
                i++;
            }
        }

        private void skipComment(char c) {
            if (c == '-') {
                skipToEndOfLine();
                return;
            }
            int end = sql.indexOf("*/", i + 2);
            int stop = end < 0 ? n : end + 2;
            line += countNewlines(i, stop);
            i = stop;
        }

        private int countNewlines(int from, int to) {
            int count = 0;
            for (int k = from; k < to; k++) {
                if (sql.charAt(k) == '\n') {
                    count++;
                }
            }
            return count;
        }

        /** 'texto' ou "identificador", com a aspa duplicada como escape. */
        private boolean consumeQuoted(char c) {
            if (c != '\'' && c != '"') {
                return false;
            }
            int j = i + 1;
            while (j < n) {
                if (sql.charAt(j) == c) {
                    if (j + 1 < n && sql.charAt(j + 1) == c) {
                        j += 2;
                        continue;
                    }
                    break;
                }
                if (sql.charAt(j) == '\n') {
                    line++;
                }
                j++;
            }
            cur.append(sql, i, Math.min(n, j + 1));
            i = j + 1;
            return true;
        }

        private boolean consumeDollarQuoted(char c) {
            if (c != '$') {
                return false;
            }
            Matcher m = DOLLAR_TAG.matcher(sql);
            m.region(i, n);
            if (!m.lookingAt()) {
                return false;
            }
            String tag = m.group();
            int end = sql.indexOf(tag, m.end());
            int stop = end < 0 ? n : end + tag.length();
            line += countNewlines(i, stop);
            cur.append(sql, i, stop);
            i = stop;
            return true;
        }

        private boolean consumeSemicolon(char c) {
            if (c != ';') {
                return false;
            }
            String text = cur.toString().strip();
            boolean copyData = false;
            if (!text.isEmpty()) {
                out.add(new Statement(text, startLine < 0 ? line : startLine));
                copyData = text.regionMatches(true, 0, COPY_PREFIX, 0, COPY_PREFIX.length()) && text.toLowerCase(Locale.ROOT).contains("from stdin");
            }
            cur.setLength(0);
            startLine = -1;
            if (copyData) {
                i = skipCopyData(sql, i + 1);
                line = 1 + SchemaParser.countNewlines(sql, i);
                atLineStart = true;
            } else {
                i++;
            }
            return true;
        }
    }

    private static int skipCopyData(String sql, int from) {
        int idx = sql.indexOf("\n\\.", from);
        if (idx < 0) {
            return sql.length();
        }
        int eol = sql.indexOf('\n', idx + 1);
        return eol < 0 ? sql.length() : eol + 1;
    }

    private static int countNewlines(String s, int upTo) {
        int c = 0;
        for (int i = 0; i < upTo && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                c++;
            }
        }
        return c;
    }

    // ------------------------------------------------------------------ modelo

    static Schema parse(String sql) {
        Map<String, Table> tables = new LinkedHashMap<>();
        List<Index> indexes = new ArrayList<>();
        for (Statement st : split(sql)) {
            String t = st.text();
            Matcher m = CREATE_TABLE.matcher(t);
            if (m.find()) {
                parseCreateTable(t, m, st.line(), tables);
                continue;
            }
            m = CREATE_INDEX.matcher(t);
            if (m.find()) {
                parseIndex(t, m, st.line(), indexes);
                continue;
            }
            m = ALTER_TABLE.matcher(t);
            if (m.find()) {
                parseAlter(t, m, st.line(), tables);
            }
        }
        return new Schema(tables, indexes);
    }

    static String tableKey(String raw) {
        String n = ident(raw);
        return n.contains(".") ? n : "public." + n;
    }

    static String ident(String raw) {
        String s = raw.strip();
        StringBuilder b = new StringBuilder();
        for (String part : splitDots(s)) {
            if (b.length() > 0) {
                b.append('.');
            }
            part = part.strip();
            if (part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2) {
                b.append(part, 1, part.length() - 1);
            } else {
                b.append(part.toLowerCase(Locale.ROOT));
            }
        }
        return b.toString();
    }

    private static List<String> splitDots(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (char c : s.toCharArray()) {
            if (c == '"') {
                q = !q;
            }
            if (c == '.' && !q) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts;
    }

    /** Conteúdo entre o parêntese aberto na posição {@code open} e o seu par (respeitando aspas e aninhamento). */
    private static String balanced(String s, int open) {
        int depth = 0;
        int i = open;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') {
                int j = s.indexOf(c, i + 1);
                i = (j < 0 ? s.length() : j) + 1;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return s.substring(open + 1, i);
                }
            }
            i++;
        }
        return s.substring(open + 1);
    }

    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\'' || c == '"') {
                int j = body.indexOf(c, i + 1);
                int stop = j < 0 ? body.length() : j + 1;
                cur.append(body, i, stop);
                i = stop;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(cur.toString().strip());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
            i++;
        }
        if (!cur.toString().isBlank()) {
            parts.add(cur.toString().strip());
        }
        return parts;
    }

    private static List<String> columnList(String body) {
        List<String> cols = new ArrayList<>();
        for (String p : splitTopLevel(body)) {
            cols.add(ident(p));
        }
        return cols;
    }

    private static void parseCreateTable(String t, Matcher m, int line, Map<String, Table> tables) {
        Table table = new Table(tableKey(m.group(1)), line);
        String body = balanced(t, m.end() - 1);
        for (String item : splitTopLevel(body)) {
            String up = item.toUpperCase(Locale.ROOT);
            if (up.startsWith("CONSTRAINT ") || up.startsWith("PRIMARY KEY") || up.startsWith("FOREIGN KEY")
                    || up.startsWith("UNIQUE") || up.startsWith("CHECK") || up.startsWith("EXCLUDE") || up.startsWith("LIKE ")) {
                tableConstraint(table, item, line);
                continue;
            }
            parseColumn(table, item, line);
        }
        tables.put(table.name, table);
    }

    private static void tableConstraint(Table table, String item, int line) {
        String s = item.replaceFirst("(?is)^CONSTRAINT\\s+[\\w\"$]+\\s+", "");
        applyConstraint(table, s, line);
    }

    private static void applyConstraint(Table table, String s, int line) {
        Matcher kind = Pattern.compile("^(PRIMARY\\s+KEY|FOREIGN\\s+KEY|UNIQUE)\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(s);
        if (!kind.find()) {
            return;
        }
        List<String> cols = columnList(balanced(s, kind.end() - 1));
        String k = kind.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (k.equals("PRIMARY KEY")) {
            table.primaryKey = cols;
        } else if (k.equals("UNIQUE")) {
            table.uniques.add(cols);
        } else {
            Matcher ref = REFERENCES.matcher(s);
            table.foreignKeys.add(new ForeignKey(cols, ref.find() ? tableKey(ref.group(1)) : "?", line));
        }
    }

    private static void parseColumn(Table table, String item, int line) {
        String name;
        String rest;
        if (item.startsWith("\"")) {
            int end = item.indexOf('"', 1);
            if (end < 0) {
                return;
            }
            name = item.substring(1, end);
            rest = item.substring(end + 1).strip();
        } else {
            int sp = 0;
            while (sp < item.length() && !Character.isWhitespace(item.charAt(sp))) {
                sp++;
            }
            name = item.substring(0, sp).toLowerCase(Locale.ROOT);
            rest = item.substring(sp).strip();
        }
        Matcher end = TYPE_END.matcher(rest);
        String type = (end.find() ? rest.substring(0, end.start()) : rest).strip().toLowerCase(Locale.ROOT);
        String attrs = end.find(0) ? rest.substring(end.start()) : "";
        boolean notNull = Pattern.compile("\\bNOT\\s+NULL\\b", Pattern.CASE_INSENSITIVE).matcher(attrs).find();
        if (Pattern.compile("\\bPRIMARY\\s+KEY\\b", Pattern.CASE_INSENSITIVE).matcher(attrs).find()) {
            table.primaryKey = List.of(name);
            notNull = true;
        }
        if (Pattern.compile("\\bUNIQUE\\b", Pattern.CASE_INSENSITIVE).matcher(attrs).find()) {
            table.uniques.add(List.of(name));
        }
        Matcher ref = REFERENCES.matcher(attrs);
        if (ref.find()) {
            table.foreignKeys.add(new ForeignKey(List.of(name), tableKey(ref.group(1)), line));
        }
        table.columns.add(new Column(name, type, notNull));
    }

    private static void parseIndex(String t, Matcher m, int line, List<Index> indexes) {
        boolean unique = m.group(1) != null;
        String method = m.group(INDEX_METHOD_GROUP) == null ? "btree" : m.group(INDEX_METHOD_GROUP).toLowerCase(Locale.ROOT);
        String body = balanced(t, m.end() - 1);
        List<String> cols = new ArrayList<>();
        for (String element : splitTopLevel(body)) {
            Matcher e = PLAIN_INDEX_ELEMENT.matcher(element.strip());
            cols.add(e.matches() ? ident(e.group(1)) : null);
        }
        boolean partial = Pattern.compile("\\)\\s*(?:INCLUDE\\s*\\([^)]*\\)\\s*)?(?:WITH\\s*\\([^)]*\\)\\s*)?(?:TABLESPACE\\s+\\S+\\s*)?WHERE\\b",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(t).find();
        indexes.add(new Index(ident(m.group(2)), tableKey(m.group(INDEX_TABLE_GROUP)), cols, unique, method, partial, line));
    }

    private static void parseAlter(String t, Matcher m, int line, Map<String, Table> tables) {
        Table table = tables.get(tableKey(m.group(1)));
        if (table == null) {
            return;
        }
        String constraint = t.substring(m.start(2));
        applyConstraint(table, constraint, line);
    }
}
