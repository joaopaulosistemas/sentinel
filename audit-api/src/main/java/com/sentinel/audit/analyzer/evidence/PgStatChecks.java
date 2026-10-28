package com.sentinel.audit.analyzer.evidence;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Consultas caras e uso de tabelas/índices, a partir de exportações CSV das visões do Postgres
 * ({@code pg_stat_statements}, {@code pg_stat_user_tables}, {@code pg_stat_user_indexes}). O script
 * {@code docs/evidencias/coletar-evidencias.sql} gera os arquivos; nada é consultado no banco.
 */
final class PgStatChecks {

    private static final double HIGH_SEQ_TUPLES_READ = 10_000_000;
    private static final long UNUSED_INDEX_MIN_BYTES = 1_048_576;
    private static final long UNUSED_INDEX_MEDIUM_BYTES = 104_857_600;


    static final double SLOW_MEAN_MS = 500;
    static final double VERY_SLOW_MEAN_MS = 2000;
    static final double TOP_SHARE_LOW = 0.05;
    static final double TOP_SHARE_MEDIUM = 0.10;
    static final double TOP_SHARE_HIGH = 0.25;
    static final int MAX_PER_RULE = 15;
    /** Tamanho de um bloco do PostgreSQL, para converter "blocos" em bytes. */
    private static final long PG_BLOCK_BYTES = 8192;
    /** Tamanho do trecho da consulta guardado como evidência e nos títulos (completo e abreviado). */
    private static final int SNIPPET_CHARS = 600;
    private static final int TITLE_QUERY_CHARS = 90;
    private static final int SHORT_TITLE_QUERY_CHARS = 80;
    /** Consulta lenta só vale com chamadas suficientes; muito chamada e rápida é "tagarela". */
    private static final double MIN_CALLS_FOR_SLOW = 5;
    private static final double MIN_CALLS_CHATTY = 500_000;
    private static final double CHATTY_MEAN_MS = 5;
    private static final double MIN_TEMP_BLOCKS = 10_000;
    private static final double MIN_READ_BLOCKS = 100_000;
    private static final double MIN_MISS_RATIO = 0.10;
    private static final double MIN_ROWS_PER_CALL = 10_000;
    /** Tabelas: varredura sequencial frequente em tabela grande, e muitas linhas mortas. */
    private static final double MIN_SEQ_SCANS = 100;
    private static final double MIN_LIVE_ROWS = 10_000;
    private static final double MIN_DEAD_ROWS = 10_000;
    private static final double MIN_DEAD_RATIO = 0.20;
    private static final Pattern NOISE = Pattern.compile(
            "^(begin|commit|rollback|set|show|reset|deallocate|discard|listen|notify|savepoint|release|start transaction|"
                    + "fetch|close|declare|vacuum|analyze|checkpoint|explain|copy|create|alter|drop|truncate|grant|revoke)\\b"
                    + "|\\bpg_stat_|\\bpg_catalog\\b|\\binformation_schema\\b|\\bpg_class\\b|\\bpg_settings\\b",
            Pattern.CASE_INSENSITIVE);

    private PgStatChecks() {
    }

    enum Kind { STATEMENTS, TABLES, INDEXES, UNKNOWN }

    static Kind detect(List<Csv.Row> rows) {
        if (rows.isEmpty()) {
            return Kind.UNKNOWN;
        }
        Csv.Row r = rows.get(0);
        if (r.has("query") && r.has("calls")) {
            return Kind.STATEMENTS;
        }
        if (r.has("seq_scan") && r.has("n_live_tup")) {
            return Kind.TABLES;
        }
        if (r.has("idx_scan") && (r.has("indexrelname") || r.has("indexname"))) {
            return Kind.INDEXES;
        }
        return Kind.UNKNOWN;
    }

    // ------------------------------------------------------------------ pg_stat_statements

    private record Stmt(Csv.Row row, String query, double calls, double total, double mean, double rows,
                        double read, double hit, double temp) {}

    static List<Finding> statements(List<Csv.Row> rows, String file, AuditRun run) {
        List<Stmt> stmts = new ArrayList<>();
        double grand = 0;
        for (Csv.Row r : rows) {
            String q = r.get("query").strip();
            if (q.isEmpty()) {
                continue;
            }
            double calls = r.number("calls");
            double total = r.number("total_exec_time", "total_time");
            double mean = r.number("mean_exec_time", "mean_time");
            if (mean == 0 && calls > 0) {
                mean = total / calls;
            }
            grand += total;
            if (NOISE.matcher(q).find()) {
                continue;
            }
            stmts.add(new Stmt(r, q, calls, total, mean, r.number("rows"), r.number("shared_blks_read"),
                    r.number("shared_blks_hit"), r.number("temp_blks_written")));
        }
        List<Finding> out = new ArrayList<>();
        final double totalTime = grand;

        stmts.stream().sorted(Comparator.comparingDouble(Stmt::total).reversed()).limit(MAX_PER_RULE).forEach(s -> {
            double share = totalTime <= 0 ? 0 : s.total() / totalTime;
            if (share < TOP_SHARE_LOW) {
                return;
            }
            Severity sev = share >= TOP_SHARE_HIGH ? Severity.HIGH : share >= TOP_SHARE_MEDIUM ? Severity.MEDIUM : Severity.LOW;
            out.add(Ev.finding(run, Category.PERFORMANCE, sev, "evidencia-consulta-mais-cara",
                    "Consulta responde por " + Math.round(share * 100) + "% do tempo do banco: " + Ev.oneLine(s.query(), TITLE_QUERY_CHARS),
                    "Esta consulta somou " + Ev.fmt(s.total() / 1000) + " s em " + Ev.fmt(s.calls()) + " chamadas (média "
                            + Ev.fmt(s.mean()) + " ms), " + Math.round(share * 100) + "% do tempo total de execução registrado. "
                            + "Otimizá-la (índice, menos colunas, paginação, cache) rende mais que qualquer outra mudança no banco.",
                    file, s.row().line(), Ev.oneLine(s.query(), SNIPPET_CHARS)));
        });

        stmts.stream().filter(s -> s.mean() >= SLOW_MEAN_MS && s.calls() >= MIN_CALLS_FOR_SLOW)
                .sorted(Comparator.comparingDouble(Stmt::mean).reversed()).limit(MAX_PER_RULE).forEach(s ->
                        out.add(Ev.finding(run, Category.PERFORMANCE,
                                s.mean() >= VERY_SLOW_MEAN_MS ? Severity.HIGH : Severity.MEDIUM, "evidencia-consulta-lenta",
                                "Consulta lenta (média " + Ev.fmt(s.mean()) + " ms): " + Ev.oneLine(s.query(), TITLE_QUERY_CHARS),
                                "Em média esta consulta leva " + Ev.fmt(s.mean()) + " ms por chamada (" + Ev.fmt(s.calls())
                                        + " chamadas). Veja o plano com EXPLAIN (ANALYZE, BUFFERS) e procure varredura "
                                        + "sequencial, ordenação em disco e junções sem índice.",
                                file, s.row().line(), Ev.oneLine(s.query(), SNIPPET_CHARS))));

        stmts.stream().filter(s -> s.temp() >= MIN_TEMP_BLOCKS)
                .sorted(Comparator.comparingDouble(Stmt::temp).reversed()).limit(MAX_PER_RULE).forEach(s ->
                        out.add(Ev.finding(run, Category.PERFORMANCE, Severity.MEDIUM, "evidencia-consulta-usa-disco-temporario",
                                "Consulta grava arquivos temporários (" + Ev.bytes(s.temp() * PG_BLOCK_BYTES) + "): " + Ev.oneLine(s.query(), SHORT_TITLE_QUERY_CHARS),
                                "Ordenações ou junções por hash não couberam em work_mem e foram para o disco. Reduza o volume "
                                        + "(filtro, colunas, índice que entregue já ordenado) ou aumente work_mem para esta carga.",
                                file, s.row().line(), Ev.oneLine(s.query(), SNIPPET_CHARS))));

        stmts.stream().filter(s -> s.read() >= MIN_READ_BLOCKS && s.read() / Math.max(1, s.read() + s.hit()) > MIN_MISS_RATIO)
                .sorted(Comparator.comparingDouble(Stmt::read).reversed()).limit(MAX_PER_RULE).forEach(s ->
                        out.add(Ev.finding(run, Category.PERFORMANCE, Severity.LOW, "evidencia-consulta-le-muito-disco",
                                "Consulta lê muito do disco (" + Ev.bytes(s.read() * PG_BLOCK_BYTES) + "): " + Ev.oneLine(s.query(), SHORT_TITLE_QUERY_CHARS),
                                "Mais de 10% dos blocos lidos não estavam na memória do banco. Confira índices, o tamanho do "
                                        + "shared_buffers e se a consulta lê colunas e linhas a mais.",
                                file, s.row().line(), Ev.oneLine(s.query(), SNIPPET_CHARS))));

        stmts.stream().filter(s -> s.calls() > 0 && s.rows() / s.calls() >= MIN_ROWS_PER_CALL)
                .sorted(Comparator.comparingDouble((Stmt s) -> s.rows() / s.calls()).reversed()).limit(MAX_PER_RULE).forEach(s ->
                        out.add(Ev.finding(run, Category.PERFORMANCE, Severity.LOW, "evidencia-consulta-retorna-muitas-linhas",
                                "Consulta devolve ~" + Ev.fmt(s.rows() / s.calls()) + " linhas por chamada: " + Ev.oneLine(s.query(), SHORT_TITLE_QUERY_CHARS),
                                "Devolver dezenas de milhares de linhas por chamada pressiona rede, memória da aplicação e do banco. "
                                        + "Pagine, filtre no banco ou agregue na própria consulta.",
                                file, s.row().line(), Ev.oneLine(s.query(), SNIPPET_CHARS))));

        stmts.stream().filter(s -> s.calls() >= MIN_CALLS_CHATTY && s.mean() < CHATTY_MEAN_MS)
                .sorted(Comparator.comparingDouble(Stmt::calls).reversed()).limit(MAX_PER_RULE).forEach(s ->
                        out.add(Ev.finding(run, Category.PERFORMANCE, Severity.LOW, "evidencia-consulta-chamada-em-excesso",
                                "Consulta chamada " + Ev.fmt(s.calls()) + " vezes: " + Ev.oneLine(s.query(), SHORT_TITLE_QUERY_CHARS),
                                "Milhões de chamadas rápidas indicam o problema N+1 (uma consulta por item) ou falta de cache. "
                                        + "Busque os dados em lote (IN, JOIN FETCH, @EntityGraph) ou guarde o resultado.",
                                file, s.row().line(), Ev.oneLine(s.query(), SNIPPET_CHARS))));
        return out;
    }

    // ------------------------------------------------------------------ pg_stat_user_tables

    static List<Finding> tables(List<Csv.Row> rows, String file, AuditRun run) {
        List<Finding> out = new ArrayList<>();
        for (Csv.Row r : rows) {
            String name = qualified(r.get("schemaname"), r.first("relname", "tablename"));
            double live = r.number("n_live_tup");
            double dead = r.number("n_dead_tup");
            double seq = r.number("seq_scan");
            double seqRead = r.number("seq_tup_read");
            double idx = r.number("idx_scan");
            if (seq >= MIN_SEQ_SCANS && live >= MIN_LIVE_ROWS && seq > idx) {
                out.add(Ev.finding(run, Category.PERFORMANCE, seqRead >= HIGH_SEQ_TUPLES_READ ? Severity.HIGH : Severity.MEDIUM,
                        "evidencia-tabela-varredura-sequencial",
                        "Tabela lida por varredura sequencial: " + name,
                        name + " tem " + Ev.fmt(live) + " linhas e sofreu " + Ev.fmt(seq) + " varreduras sequenciais ("
                                + Ev.fmt(seqRead) + " linhas lidas) contra " + Ev.fmt(idx) + " por índice. Falta índice para o "
                                + "filtro mais comum, ou o filtro não consegue usá-lo (função na coluna ou busca com curinga no início do texto).",
                        file, r.line(), name));
            }
            if (dead >= MIN_DEAD_ROWS && live + dead > 0 && dead / (live + dead) >= MIN_DEAD_RATIO) {
                out.add(Ev.finding(run, Category.PERFORMANCE, Severity.MEDIUM, "evidencia-tabela-inchada",
                        "Tabela com muitas linhas mortas: " + name,
                        name + " tem " + Ev.fmt(dead) + " linhas mortas (" + Math.round(100 * dead / (live + dead))
                                + "% do total). O autovacuum não está dando conta: a tabela ocupa mais espaço e as consultas "
                                + "ficam mais lentas. Ajuste autovacuum_vacuum_scale_factor para esta tabela e evite "
                                + "transações longas.",
                        file, r.line(), name));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ pg_stat_user_indexes

    static List<Finding> indexes(List<Csv.Row> rows, String file, AuditRun run) {
        List<Finding> out = new ArrayList<>();
        for (Csv.Row r : rows) {
            String index = r.first("indexrelname", "indexname");
            String table = qualified(r.get("schemaname"), r.first("relname", "tablename"));
            double scans = r.number("idx_scan");
            double size = r.number("size_bytes", "index_size", "pg_relation_size");
            String def = r.get("indexdef").toLowerCase(Locale.ROOT);
            boolean unique = def.contains("create unique") || index.endsWith("_pkey");
            if (scans == 0 && !unique && size >= UNUSED_INDEX_MIN_BYTES) {
                out.add(Ev.finding(run, Category.PERFORMANCE, size >= UNUSED_INDEX_MEDIUM_BYTES ? Severity.MEDIUM : Severity.LOW,
                        "evidencia-indice-nao-usado",
                        "Índice nunca usado (" + Ev.bytes(size) + "): " + index,
                        "O índice " + index + " em " + table + " ocupa " + Ev.bytes(size) + " e não foi usado em nenhuma consulta "
                                + "desde o último reset das estatísticas. Custa escrita e espaço. Confirme que as estatísticas "
                                + "cobrem um período completo (fechamentos mensais, relatórios) e então remova.",
                        file, r.line(), index));
            }
        }
        return out;
    }

    private static String qualified(String schema, String name) {
        return schema == null || schema.isBlank() || schema.equals("public") ? name : schema + "." + name;
    }
}
