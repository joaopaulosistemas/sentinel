package com.sentinel.audit.analyzer.evidence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaParserTest {

    private static final String DUMP = """
            -- PostgreSQL database dump
            SET statement_timeout = 0;

            CREATE TABLE public.cliente (
                id bigint NOT NULL,
                nome character varying(120) NOT NULL,
                email text,
                CONSTRAINT cliente_pkey PRIMARY KEY (id)
            );

            CREATE TABLE public.pedido (
                id bigint NOT NULL PRIMARY KEY,
                cliente_id bigint NOT NULL REFERENCES public.cliente(id),
                total numeric(10,2) DEFAULT 0
            );

            ALTER TABLE ONLY public.cliente ADD CONSTRAINT cliente_email_key UNIQUE (email);
            CREATE UNIQUE INDEX idx_cliente_nome ON public.cliente USING btree (nome);
            CREATE INDEX idx_pedido_cliente ON public.pedido (cliente_id);
            """;

    @Test
    void leTabelasColunasEChavesDeUmDump() {
        SchemaParser.Schema s = SchemaParser.parse(DUMP);

        assertEquals(List.of("public.cliente", "public.pedido"), List.copyOf(s.tables().keySet()));
        SchemaParser.Table cliente = s.tables().get("public.cliente");
        assertEquals(List.of("id", "nome", "email"), cliente.columns.stream().map(SchemaParser.Column::name).toList());
        assertEquals(List.of("id"), cliente.primaryKey);
        assertTrue(cliente.column("id").notNull());
        assertFalse(cliente.column("email").notNull());
        assertEquals(List.of(List.of("email")), cliente.uniques);
        assertNull(cliente.column("inexistente"));
    }

    @Test
    void chaveEstrangeiraEPrimariaEmLinhaSaoReconhecidas() {
        SchemaParser.Table pedido = SchemaParser.parse(DUMP).tables().get("public.pedido");

        assertEquals(List.of("id"), pedido.primaryKey);
        assertEquals(1, pedido.foreignKeys.size());
        assertEquals(List.of("cliente_id"), pedido.foreignKeys.get(0).columns());
        assertEquals("public.cliente", pedido.foreignKeys.get(0).refTable());
    }

    @Test
    void indicesTrazemColunasUnicidadeEMetodo() {
        List<SchemaParser.Index> idx = SchemaParser.parse(DUMP).indexes();

        assertEquals(2, idx.size());
        SchemaParser.Index nome = idx.get(0);
        assertEquals("idx_cliente_nome", nome.name());
        assertTrue(nome.unique());
        assertEquals("btree", nome.method());
        assertEquals(List.of("nome"), nome.columns());
        assertFalse(idx.get(1).unique());
        assertEquals(List.of("cliente_id"), idx.get(1).columns());
    }

    @Test
    void expressaoNoIndiceViraNuloEIndiceParcialEMarcado() {
        SchemaParser.Schema s = SchemaParser.parse("""
                CREATE TABLE t (a text, b text);
                CREATE INDEX i_expr ON t (lower(a));
                CREATE INDEX i_parcial ON t (b) WHERE b IS NOT NULL;
                """);

        assertEquals(2, s.indexes().size());
        assertNull(s.indexes().get(0).columns().get(0), "expressão não é coluna simples");
        assertTrue(s.indexes().get(1).partial());
    }

    @Test
    void pontoEVirgulaDentroDeTextoEDeCorpoDeFuncaoNaoQuebraOComando() {
        SchemaParser.Schema s = SchemaParser.parse("""
                CREATE FUNCTION f() RETURNS void AS $$ BEGIN PERFORM 1; PERFORM 2; END; $$ LANGUAGE plpgsql;
                CREATE TABLE a (x text DEFAULT 'a;b', y int);
                COMMENT ON TABLE a IS 'tem ; dentro';
                """);

        assertEquals(List.of("public.a"), List.copyOf(s.tables().keySet()));
        assertEquals(2, s.tables().get("public.a").columns.size());
    }

    @Test
    void dadosDeCopyNaoViramComandos() {
        SchemaParser.Schema s = SchemaParser.parse("CREATE TABLE a (x int);\n"
                + "COPY a (x) FROM stdin;\n1\n2\nCREATE TABLE fantasma (y int);\n\\.\n"
                + "CREATE TABLE b (z int);\n");

        assertEquals(List.of("public.a", "public.b"), List.copyOf(s.tables().keySet()));
    }

    @Test
    void identificadoresComAspasEEsquemaSaoNormalizados() {
        assertEquals("public.cliente", SchemaParser.tableKey("cliente"), "sem esquema vale public");
        assertEquals("vendas.pedido", SchemaParser.tableKey("vendas.pedido"));
        assertEquals("public.Cliente", SchemaParser.tableKey("public.\"Cliente\""));
        SchemaParser.Schema s = SchemaParser.parse("CREATE TABLE \"public\".\"Pedido\" (\"Id\" int NOT NULL);");
        SchemaParser.Table t = s.tables().get("public.Pedido");
        assertNotNull(t);
        assertNotNull(t.column("Id"));
    }

    @Test
    void entradaVaziaOuSemDdlDaEsquemaVazio() {
        assertTrue(SchemaParser.parse("").tables().isEmpty());
        assertTrue(SchemaParser.parse("-- só comentário\nSET x = 1;").tables().isEmpty());
    }
}
