package com.sentinel.audit.analyzer.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.audit.analyzer.AnalyzerResult;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceTest {

    @TempDir
    Path dir;

    private static final AuditRun RUN = new AuditRun();

    private static Set<String> ids(List<Finding> f) {
        return f.stream().map(Finding::getRuleId).collect(Collectors.toSet());
    }

    private static long count(List<Finding> f, String rule) {
        return f.stream().filter(x -> x.getRuleId().equals(rule)).count();
    }

    // ------------------------------------------------------------------ esquema

    static final String DUMP = """
            --
            -- PostgreSQL database dump
            --
            \\restrict abc123
            SET statement_timeout = 0;
            CREATE TABLE public.cliente (
                id integer NOT NULL,
                nome character varying(120) NOT NULL,
                criado_em timestamp without time zone DEFAULT now(),
                ativo boolean DEFAULT true NOT NULL,
                dados json,
                saldo money
            );
            CREATE TABLE public.pedido (
                id bigint NOT NULL,
                cliente_id bigint NOT NULL,
                valor numeric(12,2),
                observacao text DEFAULT 'a;b'
            );
            CREATE TABLE public.log_evento (
                msg text
            );
            CREATE TABLE public.flyway_schema_history (installed_rank integer NOT NULL, script text);
            ALTER TABLE ONLY public.cliente ADD CONSTRAINT cliente_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY public.pedido ADD CONSTRAINT pedido_pkey PRIMARY KEY (id);
            ALTER TABLE ONLY public.pedido ADD CONSTRAINT fk_pedido_cliente FOREIGN KEY (cliente_id) REFERENCES public.cliente(id);
            CREATE INDEX idx_cliente_nome ON public.cliente USING btree (nome);
            CREATE INDEX idx_cliente_nome_dup ON public.cliente USING btree (nome);
            CREATE INDEX idx_cliente_id ON public.cliente USING btree (id);
            CREATE INDEX idx_cliente_ativo ON public.cliente USING btree (ativo);
            CREATE INDEX idx_cliente_lower ON public.cliente USING btree (lower((nome)::text));
            CREATE UNIQUE INDEX uk_cliente_nome ON public.cliente USING btree (nome, id);
            CREATE OR REPLACE FUNCTION public.f() RETURNS void AS $$
            BEGIN
              CREATE TABLE nao_deve_parsear (x int);
            END;
            $$ LANGUAGE plpgsql;
            """;

    @Test
    void esquemaApontaOsProblemasDeModelagemEIndices() {
        List<Finding> f = SchemaChecks.run(DUMP, "schema.sql", RUN);

        assertTrue(ids(f).containsAll(Set.of(
                "evidencia-schema-tabela-sem-pk", "evidencia-schema-fk-sem-indice", "evidencia-schema-timestamp-sem-fuso",
                "evidencia-schema-json-nao-jsonb", "evidencia-schema-money", "evidencia-schema-pk-int",
                "evidencia-schema-indice-duplicado", "evidencia-schema-indice-redundante",
                "evidencia-schema-indice-em-booleano")), ids(f).toString());
        // só log_evento está sem PK (flyway_schema_history é ignorada; nao_deve_parsear está dentro de $$)
        assertEquals(1, count(f, "evidencia-schema-tabela-sem-pk"));
        assertTrue(f.stream().anyMatch(x -> x.getTitle().contains("log_evento")));
        assertFalse(f.stream().anyMatch(x -> x.getTitle().contains("nao_deve_parsear")));
        // a FK é de pedido.cliente_id
        assertTrue(f.stream().filter(x -> x.getRuleId().equals("evidencia-schema-fk-sem-indice"))
                .allMatch(x -> x.getTitle().contains("pedido")));
    }

    @Test
    void indiceDuplicadoERedundanteSaoDistinguidos() {
        List<Finding> f = SchemaChecks.run(DUMP, "schema.sql", RUN);
        List<String> dup = f.stream().filter(x -> x.getRuleId().equals("evidencia-schema-indice-duplicado"))
                .map(Finding::getTitle).toList();
        List<String> red = f.stream().filter(x -> x.getRuleId().equals("evidencia-schema-indice-redundante"))
                .map(Finding::getTitle).toList();

        assertEquals(List.of("Índice duplicado: idx_cliente_nome_dup", "Índice duplicado: idx_cliente_id"), dup);
        assertTrue(f.stream().anyMatch(x -> x.getDescription().contains("repete a chave primária")));    // idx_cliente_id
        assertTrue(red.contains("Índice redundante: idx_cliente_nome"), red.toString());    // coberto por uk_cliente_nome (nome, id)
    }

    @Test
    void chaveEstrangeiraComIndiceNaoEApontada() {
        String sql = DUMP + "CREATE INDEX idx_pedido_cliente ON public.pedido USING btree (cliente_id);\n";

        assertEquals(0, count(SchemaChecks.run(sql, "s.sql", RUN), "evidencia-schema-fk-sem-indice"));
    }

    @Test
    void ddlSimplesComChavesNaPropriaColuna() {
        String sql = """
                CREATE TABLE conta (id serial PRIMARY KEY, titular_id int REFERENCES titular(id), cpf char(11) UNIQUE);
                CREATE TABLE titular (id bigint primary key);
                """;
        List<Finding> f = SchemaChecks.run(sql, "ddl.sql", RUN);

        assertEquals(1, count(f, "evidencia-schema-fk-sem-indice"));
        assertEquals(1, count(f, "evidencia-schema-pk-int"));
        assertEquals(1, count(f, "evidencia-schema-char-fixo"));
        assertEquals(0, count(f, "evidencia-schema-tabela-sem-pk"));
    }

    @Test
    void dumpComDadosNaoQuebraNemPulaOQueVemDepois() {
        String sql = "CREATE TABLE a (x int);\nCOPY public.a (x) FROM stdin;\n1\n2;3\n\\.\n\nCREATE TABLE b (id bigint PRIMARY KEY);\n";
        List<Finding> f = SchemaChecks.run(sql, "d.sql", RUN);

        assertEquals(1, count(f, "evidencia-schema-tabela-sem-pk"));
        assertTrue(f.get(0).getTitle().contains("a"));
    }

    // ------------------------------------------------------------------ estatísticas

    static final String STATEMENTS = """
            userid,query,calls,total_exec_time,mean_exec_time,rows,shared_blks_hit,shared_blks_read,temp_blks_written
            10,"SELECT * FROM pedido p
            WHERE p.cliente_id = $1",900000,600000,0.66,900000,100,0,0
            10,SELECT c.* FROM cliente c ORDER BY c.nome,40,800000,20000,400000000,1000,500000,50000
            10,BEGIN,1000,10,0.01,0,0,0,0
            10,UPDATE cliente SET nome = $1 WHERE id = $2,100,50,0.5,100,10,0,0
            """;

    @Test
    void consultasCarasLentasETemporarios() {
        List<Csv.Row> rows = Csv.parse(STATEMENTS);
        assertEquals(PgStatChecks.Kind.STATEMENTS, PgStatChecks.detect(rows));

        List<Finding> f = PgStatChecks.statements(rows, "pg_stat_statements.csv", RUN);

        assertTrue(ids(f).containsAll(Set.of("evidencia-consulta-mais-cara", "evidencia-consulta-lenta",
                "evidencia-consulta-usa-disco-temporario", "evidencia-consulta-le-muito-disco",
                "evidencia-consulta-retorna-muitas-linhas", "evidencia-consulta-chamada-em-excesso")), ids(f).toString());
        // a consulta de 2 linhas (campo com quebra de linha) é lida inteira e o BEGIN é ignorado
        assertTrue(f.stream().anyMatch(x -> x.getCodeSnippet().contains("WHERE p.cliente_id = $1")));
        assertFalse(f.stream().anyMatch(x -> x.getCodeSnippet().equals("BEGIN")));
        Finding lenta = f.stream().filter(x -> x.getRuleId().equals("evidencia-consulta-lenta")).findFirst().orElseThrow();
        assertEquals(Severity.HIGH, lenta.getSeverity());      // 20 s de média
    }

    @Test
    void tabelasEIndicesUsados() {
        List<Csv.Row> tabelas = Csv.parse("""
                schemaname,relname,seq_scan,seq_tup_read,idx_scan,n_live_tup,n_dead_tup
                public,pedido,5000,50000000,10,1000000,10
                public,cliente,5,100,10,1000,5000000
                public,pequena,9999,99999,0,50,0
                """);
        assertEquals(PgStatChecks.Kind.TABLES, PgStatChecks.detect(tabelas));
        List<Finding> t = PgStatChecks.tables(tabelas, "t.csv", RUN);
        assertEquals(1, count(t, "evidencia-tabela-varredura-sequencial"));
        assertEquals(Severity.HIGH, t.stream().filter(x -> x.getRuleId().equals("evidencia-tabela-varredura-sequencial")).findFirst().orElseThrow().getSeverity());
        assertEquals(1, count(t, "evidencia-tabela-inchada"));

        List<Csv.Row> indices = Csv.parse("""
                schemaname,relname,indexrelname,idx_scan,size_bytes,indexdef
                public,pedido,idx_pedido_x,0,209715200,CREATE INDEX idx_pedido_x ON public.pedido USING btree (x)
                public,pedido,pedido_pkey,0,209715200,CREATE UNIQUE INDEX pedido_pkey ON public.pedido USING btree (id)
                public,pedido,idx_pequeno,0,100,CREATE INDEX idx_pequeno ON public.pedido USING btree (y)
                public,pedido,idx_usado,500,209715200,CREATE INDEX idx_usado ON public.pedido USING btree (z)
                """);
        assertEquals(PgStatChecks.Kind.INDEXES, PgStatChecks.detect(indices));
        List<Finding> i = PgStatChecks.indexes(indices, "i.csv", RUN);
        assertEquals(1, i.size());
        assertEquals(Severity.MEDIUM, i.get(0).getSeverity());
    }

    // ------------------------------------------------------------------ plano

    @Test
    void planoComVarreduraOrdenacaoEmDiscoEEstimativaErrada() {
        String json = """
                [{"Plan": {"Node Type": "Sort", "Sort Method": "external merge", "Sort Space Used": 90000, "Sort Key": ["c.nome"],
                    "Plan Rows": 10, "Actual Rows": 500000, "Actual Loops": 1,
                    "Plans": [{"Node Type": "Seq Scan", "Relation Name": "cliente", "Filter": "(ativo = false)",
                       "Rows Removed by Filter": 2000000, "Actual Loops": 1, "Plan Rows": 100, "Actual Rows": 100}]},
                  "Execution Time": 3200.5}]
                """;
        List<Finding> f = PlanChecks.run(json, "explain-1.json", RUN);

        assertTrue(ids(f).containsAll(Set.of("evidencia-plano-lento", "evidencia-plano-varredura-sequencial",
                "evidencia-plano-ordenacao-em-disco", "evidencia-plano-estimativa-errada")), ids(f).toString());
        assertEquals(Severity.MEDIUM, f.stream().filter(x -> x.getRuleId().equals("evidencia-plano-varredura-sequencial")).findFirst().orElseThrow().getSeverity());
    }

    @Test
    void jsonQueNaoEPlanoEIgnorado() {
        assertTrue(PlanChecks.run("{\"a\": 1}", "x.json", RUN).isEmpty());
        assertTrue(PlanChecks.run("isto não é json", "x.json", RUN).isEmpty());
    }

    // ------------------------------------------------------------------ cobertura

    @Test
    void jacocoXmlCsvELcov() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="x"><package name="com/acme/app">
                  <class name="com/acme/app/ContaService" sourcefilename="ContaService.java">
                    <counter type="LINE" missed="180" covered="0"/></class>
                  <class name="com/acme/app/ContaService$Interna" sourcefilename="ContaService.java">
                    <counter type="LINE" missed="10" covered="0"/></class>
                  <class name="com/acme/app/Util" sourcefilename="Util.java"><counter type="LINE" missed="5" covered="95"/></class>
                </package></report>
                """;
        List<Finding> f = CoverageChecks.run(CoverageChecks.jacocoXml(xml), "jacoco.xml", 60, RUN);

        assertEquals(1, count(f, "evidencia-cobertura-baixa"));          // 95/280 = 34% < 60 (e < 30? não: > 30 => MEDIUM)
        assertEquals(1, count(f, "evidencia-cobertura-classe-sem-teste"));
        assertTrue(f.stream().anyMatch(x -> "src/main/java/com/acme/app/ContaService.java".equals(x.getFilePath())));

        List<Csv.Row> csv = Csv.parse("GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,LINE_MISSED,LINE_COVERED\nx,com.acme,A,1,1,90,10\n");
        assertEquals(1, count(CoverageChecks.run(CoverageChecks.jacocoCsv(csv), "jacoco.csv", 60, RUN), "evidencia-cobertura-baixa"));

        String lcov = "TN:\nSF:src/app/a.ts\nLF:100\nLH:0\nend_of_record\nSF:src/app/b.ts\nLF:10\nLH:10\nend_of_record\n";
        List<Finding> l = CoverageChecks.run(CoverageChecks.lcov(lcov), "lcov.info", 60, RUN);
        assertEquals(1, count(l, "evidencia-cobertura-baixa"));
        assertEquals(1, count(l, "evidencia-cobertura-classe-sem-teste"));
    }

    @Test
    void xmlMaliciosoComDoctypeEIgnorado() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><report>&x;</report>";

        assertTrue(CoverageChecks.jacocoXml(xxe).isEmpty());
    }

    // ------------------------------------------------------------------ memória

    @Test
    void histogramaDeMemoria() {
        String histo = """
                 num     #instances         #bytes  class name (module)
                -------------------------------------------------------
                   1:       3000000      240000000  [B (java.base@21)
                   2:       2500000      160000000  com.acme.app.Sessao
                   3:      6000000      192000000  java.util.concurrent.ConcurrentHashMap$Node (java.base@21)
                   4:          1000          80000  com.acme.app.Pequena
                Total      12000000      700000000
                """;
        List<HeapChecks.Entry> e = HeapChecks.parse(histo);
        assertEquals(4, e.size());

        List<Finding> f = HeapChecks.run(e, Set.of("com.acme.app"), "class_histogram.txt", RUN);

        assertEquals(1, count(f, "evidencia-memoria-maiores-consumidores"));
        assertEquals(1, count(f, "evidencia-memoria-classe-acumulada"));
        assertEquals(1, count(f, "evidencia-memoria-colecao-gigante"));
    }

    // ------------------------------------------------------------------ analisador

    @Test
    void analisadorLePastaDeEvidenciasEIgnoraArquivosDesconhecidos() throws Exception {
        Path ev = Files.createDirectories(dir.resolve(".sentinel/evidence"));
        Files.writeString(ev.resolve("schema.sql"), DUMP);
        Files.writeString(ev.resolve("pg_stat_statements.csv"), STATEMENTS);
        Files.writeString(ev.resolve("notas.md"), "nada a ver");
        Files.writeString(ev.resolve("outro.json"), "{\"x\":1}");
        EvidenceAnalyzer a = new EvidenceAnalyzer();

        assertTrue(a.hasEvidence(dir));
        AnalyzerResult r = a.analyze(dir, RUN);

        assertEquals("Evidências", r.analyzerName());
        assertTrue(ids(r.findings()).contains("evidencia-schema-tabela-sem-pk"));
        assertTrue(ids(r.findings()).contains("evidencia-consulta-mais-cara"));
        assertEquals("Evidências", r.findings().get(0).getToolSource());
        assertTrue(r.findings().stream().anyMatch(x -> ".sentinel/evidence/schema.sql".equals(x.getFilePath())));
    }

    @Test
    void semPastaDeEvidenciasNaoHaNada() throws Exception {
        Path vazio = Files.createDirectories(dir.resolve("vazio"));
        EvidenceAnalyzer a = new EvidenceAnalyzer();

        assertFalse(a.hasEvidence(vazio));
        assertTrue(a.analyze(vazio, RUN).findings().isEmpty());
    }

    @Test
    void pacotesDaAplicacaoVemDosArquivosJava() throws Exception {
        Files.createDirectories(dir.resolve("src/main/java/br/com/acme/app"));
        Files.writeString(dir.resolve("src/main/java/br/com/acme/app/A.java"), "package br.com.acme.app;\nclass A {}\n");

        assertEquals(Set.of("br.com.acme"), EvidenceAnalyzer.applicationPackages(dir));
    }
}
