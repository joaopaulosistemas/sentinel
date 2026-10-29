package com.sentinel.audit.analyzer.evidence;

import com.sentinel.audit.analyzer.evidence.SchemaParser.Column;
import com.sentinel.audit.analyzer.evidence.SchemaParser.ForeignKey;
import com.sentinel.audit.analyzer.evidence.SchemaParser.Index;
import com.sentinel.audit.analyzer.evidence.SchemaParser.Schema;
import com.sentinel.audit.analyzer.evidence.SchemaParser.Table;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Verificações no esquema do banco (a partir do dump de DDL): modelagem e índices. */
final class SchemaChecks {

    private static final String PUBLIC_PREFIX = "public.";


    static final int WIDE_TABLE_COLUMNS = 50;
    static final int MANY_INDEXES = 10;
    private static final Pattern INFRA_TABLE = Pattern.compile(
            "(^|\\.)(flyway_schema_history|schema_version|databasechangelog\\w*|django_migrations|__efmigrationshistory|spring_session\\w*|qrtz_\\w+|shedlock)$");

    private SchemaChecks() {
    }

    static List<Finding> run(String sql, String file, AuditRun run) {
        Schema schema = SchemaParser.parse(sql);
        List<Finding> out = new ArrayList<>();
        for (Table t : schema.tables().values()) {
            if (INFRA_TABLE.matcher(t.name).find() || t.name.startsWith("pg_catalog.") || t.name.startsWith("information_schema.")) {
                continue;
            }
            semChavePrimaria(t, file, run, out);
            fkSemIndice(t, schema, file, run, out);
            tipos(t, file, run, out);
            tabelaLarga(t, file, run, out);
        }
        indices(schema, file, run, out);
        return out;
    }

    private static String display(String tableKey) {
        return tableKey.startsWith(PUBLIC_PREFIX) ? tableKey.substring(PUBLIC_PREFIX.length()) : tableKey;
    }

    private static void semChavePrimaria(Table t, String file, AuditRun run, List<Finding> out) {
        if (!t.primaryKey.isEmpty()) {
            return;
        }
        out.add(Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, Severity.MEDIUM, "evidencia-schema-tabela-sem-pk"), new Ev.Where(file, t.line, "CREATE TABLE " + display(t.name)), "Tabela sem chave primária: " + display(t.name), "A tabela " + display(t.name) + " não tem chave primária. Sem ela não há identidade de linha: o ORM não "
                        + "atualiza com segurança, a replicação lógica não funciona e duplicatas entram sem aviso. "
                        + "Crie uma chave primária (de preferência bigint com identidade)."));
    }

    private static void fkSemIndice(Table t, Schema schema, String file, AuditRun run, List<Finding> out) {
        List<List<String>> covering = new ArrayList<>();
        if (!t.primaryKey.isEmpty()) {
            covering.add(t.primaryKey);
        }
        covering.addAll(t.uniques);
        for (Index ix : schema.indexes()) {
            if (ix.table().equals(t.name) && ix.method().equals("btree")) {
                covering.add(ix.columns());
            }
        }
        for (ForeignKey fk : t.foreignKeys) {
            boolean covered = false;
            for (List<String> cols : covering) {
                if (cols.size() >= fk.columns().size()
                        && new HashSet<>(cols.subList(0, fk.columns().size())).equals(new HashSet<>(fk.columns()))) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                out.add(Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, Severity.MEDIUM, "evidencia-schema-fk-sem-indice"), new Ev.Where(file, fk.line(), "FOREIGN KEY (" + String.join(", ", fk.columns()) + ") REFERENCES " + display(fk.refTable())), "Chave estrangeira sem índice: " + display(t.name) + " (" + String.join(", ", fk.columns()) + ")", "A coluna " + String.join(", ", fk.columns()) + " de " + display(t.name) + " referencia "
                                + display(fk.refTable()) + ", mas nenhum índice começa por ela. O Postgres não cria esse "
                                + "índice sozinho: joins e exclusões na tabela pai varrem a tabela inteira e seguram "
                                + "bloqueios por mais tempo. Crie o índice (CREATE INDEX CONCURRENTLY)."));
            }
        }
    }

    private static void tipos(Table t, String file, AuditRun run, List<Finding> out) {
        for (Column c : t.columns) {
            Finding f = columnTypeFinding(t, c, file, run);
            if (f != null) {
                out.add(f);
            }
        }
        if (t.primaryKey.size() == 1) {
            Column pk = t.column(t.primaryKey.get(0));
            if (pk != null && isSmallIntegerType(pk.type())) {
                out.add(Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, Severity.LOW, "evidencia-schema-pk-int"), new Ev.Where(file, t.line, display(t.name) + "." + pk.name() + " " + pk.type()), "Chave primária " + pk.type() + ": " + display(t.name) + "." + pk.name(), "Uma chave " + pk.type() + " esgota em cerca de 2,1 bilhões de linhas (smallint, em 32 mil) e trocar o "
                                + "tipo depois reescreve a tabela. Em tabelas que crescem, use bigint."));
            }
        }
    }

    private static boolean isSmallIntegerType(String type) {
        return type.equals("integer") || type.equals("int") || type.equals("int4")
                || type.equals("smallint") || type.equals("int2") || type.equals("serial");
    }

    /** Problema de tipo na coluna (data sem fuso, money, json, char fixo); nulo se o tipo está bem. */
    private static Finding columnTypeFinding(Table t, Column c, String file, AuditRun run) {
        String type = c.type();
        String where = display(t.name) + "." + c.name();
        if (type.equals("timestamp") || type.startsWith("timestamp(") || type.contains("timestamp without time zone")
                    || type.matches("timestamp\\s*\\(\\d+\\)\\s*without time zone")) {
            return Ev.finding(run, new Ev.Kind(Category.QUALITY, Severity.LOW, "evidencia-schema-timestamp-sem-fuso"), new Ev.Where(file, t.line, where + " " + type), "Data/hora sem fuso horário: " + where, "A coluna " + where + " é " + type + ". Sem fuso, o mesmo valor significa horários diferentes "
                                + "conforme o servidor ou a sessão, e o horário de verão quebra comparações. Use timestamptz.");
        }
        if (type.equals("money")) {
            return Ev.finding(run, new Ev.Kind(Category.QUALITY, Severity.LOW, "evidencia-schema-money"), new Ev.Where(file, t.line, where + " money"), "Tipo money: " + where, "O tipo money depende da configuração de localidade do servidor e perde precisão em divisões. "
                                + "Use numeric(precisão, escala) para valores monetários.");
        }
        if (type.equals("json")) {
            return Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, Severity.LOW, "evidencia-schema-json-nao-jsonb"), new Ev.Where(file, t.line, where + " json"), "Coluna json em vez de jsonb: " + where, "json guarda o texto e precisa ser lido de novo a cada consulta; não aceita índice GIN. "
                                + "Prefira jsonb, salvo se a ordem e a formatação originais importarem.");
        }
        if (type.startsWith("character(") || type.startsWith("char(") || type.startsWith("bpchar")) {
            return Ev.finding(run, new Ev.Kind(Category.QUALITY, Severity.INFO, "evidencia-schema-char-fixo"), new Ev.Where(file, t.line, where + " " + type), "Coluna de tamanho fixo (char): " + where, "No Postgres char(n) preenche com espaços e não é mais rápido que text ou varchar; costuma causar "
                                + "falhas de comparação. Prefira text ou varchar(n).");
        }
        return null;
    }

    private static void tabelaLarga(Table t, String file, AuditRun run, List<Finding> out) {
        if (t.columns.size() >= WIDE_TABLE_COLUMNS) {
            out.add(Ev.finding(run, new Ev.Kind(Category.QUALITY, Severity.LOW, "evidencia-schema-tabela-larga"), new Ev.Where(file, t.line, display(t.name) + " (" + t.columns.size() + " colunas)"), "Tabela com " + t.columns.size() + " colunas: " + display(t.name), "Tabelas com dezenas de colunas costumam misturar responsabilidades e leem mais dados do que a consulta "
                            + "precisa. Considere separar grupos de colunas em tabelas relacionadas."));
        }
    }

    /** Para onde vão os achados: arquivo do esquema, execução e lista de saída. */
    private record Sink(String file, AuditRun run, List<Finding> out) { }

    private static void indices(Schema schema, String file, AuditRun run, List<Finding> out) {
        Sink sink = new Sink(file, run, out);
        Set<String> tables = new HashSet<>();
        for (Index ix : schema.indexes()) {
            tables.add(ix.table());
        }
        for (String table : tables) {
            Table def = schema.tables().get(table);
            if (def != null && INFRA_TABLE.matcher(def.name).find()) {
                continue;
            }
            List<Index> list = schema.indexes().stream().filter(i -> i.table().equals(table)).toList();
            tooManyIndexes(table, list, sink);
            for (Index ix : list) {
                if (!ix.columns().isEmpty() && !ix.columns().contains(null)) {
                    booleanIndex(ix, def, sink);
                    redundantIndex(ix, list, def, sink);
                }
            }
        }
    }

    private static void tooManyIndexes(String table, List<Index> list, Sink sink) {
        if (list.size() > MANY_INDEXES) {
            Index first = list.get(0);
            String file = sink.file();
            AuditRun run = sink.run();
            sink.out().add(Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, Severity.LOW, "evidencia-schema-muitos-indices"), new Ev.Where(file, first.line(), display(table)), list.size() + " índices em " + display(table), "Cada índice custa espaço e deixa INSERT, UPDATE e DELETE mais lentos. Confira, com as estatísticas "
                            + "de uso, quais realmente servem consultas e elimine os demais."));
        }
    }

    private static void booleanIndex(Index ix, Table def, Sink sink) {
        if (ix.columns().size() != 1 || ix.unique() || !ix.method().equals("btree") || def == null) {
            return;
        }
        Column c = def.column(ix.columns().get(0));
        if (c != null && (c.type().equals("boolean") || c.type().equals("bool"))) {
            String file = sink.file();
            AuditRun run = sink.run();
            String table = ix.table();
            sink.out().add(Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, Severity.LOW, "evidencia-schema-indice-em-booleano"), new Ev.Where(file, ix.line(), ix.name() + " em " + display(table) + "(" + ix.columns().get(0) + ")"), "Índice em coluna booleana: " + ix.name(), "Uma coluna booleana tem só dois valores; o planejador quase nunca usa um índice assim e ele "
                            + "custa escrita. Se a consulta busca o valor raro, use um índice parcial (WHERE)."));
        }
    }

    private static void redundantIndex(Index ix, List<Index> list, Table def, Sink sink) {
        Index other = findCovering(ix, list, def);
        if (other == null) {
            return;
        }
        String file = sink.file();
        AuditRun run = sink.run();
        String table = ix.table();
        boolean same = other.columns().equals(ix.columns());
        sink.out().add(Ev.finding(run, new Ev.Kind(Category.PERFORMANCE, Severity.LOW, same ? "evidencia-schema-indice-duplicado" : "evidencia-schema-indice-redundante"), new Ev.Where(file, ix.line(), ix.name()), (same ? "Índice duplicado: " : "Índice redundante: ") + ix.name(), "O índice " + ix.name() + " em " + display(table) + "(" + String.join(", ", ix.columns()) + ") "
                        + (same ? "repete " : "já é atendido por ") + other.name()
                        + "(" + String.join(", ", other.columns()) + "). "
                        + "Índices a mais só adicionam custo de escrita e espaço; remova o desnecessário."));
    }

    /** Outro índice (ou a chave primária/única) que torna este desnecessário; nulo se ninguém o cobre. */
    private static Index findCovering(Index ix, List<Index> all, Table def) {
        if (ix.unique() || ix.partial() || !ix.method().equals("btree")) {
            return null;
        }
        Index sibling = coveringSibling(ix, all);
        if (sibling != null) {
            return sibling;
        }
        return def == null ? null : coveringConstraint(ix, def);
    }

    private static Index coveringSibling(Index ix, List<Index> all) {
        for (Index other : all) {
            boolean candidate = other != ix && !other.partial() && other.method().equals("btree") && !other.columns().contains(null);
            if (candidate && isPrefix(ix.columns(), other.columns())
                    && (other.columns().size() > ix.columns().size() || other.line() < ix.line() || other.unique())) {
                return other;
            }
        }
        return null;
    }

    /** A chave primária ou uma restrição única que começa pelas mesmas colunas do índice. */
    private static Index coveringConstraint(Index ix, Table def) {
        if (!def.primaryKey.isEmpty() && isPrefix(ix.columns(), def.primaryKey)) {
            return new Index("a chave primária", ix.table(), def.primaryKey, true, "btree", false, 0);
        }
        for (List<String> u : def.uniques) {
            if (isPrefix(ix.columns(), u)) {
                return new Index("a restrição única", ix.table(), u, true, "btree", false, 0);
            }
        }
        return null;
    }

    private static boolean isPrefix(List<String> shorter, List<String> longer) {
        if (shorter.size() > longer.size()) {
            return false;
        }
        for (int i = 0; i < shorter.size(); i++) {
            if (!Objects.equals(shorter.get(i), longer.get(i))) {
                return false;
            }
        }
        return true;
    }

    static boolean looksLikeSchema(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".sql");
    }
}
