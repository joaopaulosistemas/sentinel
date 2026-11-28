"""Testes de scripts/conferir-esquema.py: a coluna acrescentada por uma migração depois do V1 conta como parte do esquema."""
import importlib.util
import os
import re
import unittest

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_spec = importlib.util.spec_from_file_location("conferir_esquema", os.path.join(RAIZ, "scripts", "conferir-esquema.py"))
esquema = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(esquema)

V1 = {"CREATE TABLE public.audit_run ( id bigint NOT NULL, branch character varying(255) )",
      "CREATE TABLE public.system ( id bigint NOT NULL )"}


class ColunaNovaTest(unittest.TestCase):
    def test_coluna_entra_no_create_table_da_tabela_certa(self):
        saida = esquema._aplica_coluna(V1, "ALTER TABLE public.audit_run ADD COLUMN disabled_sides varchar(20)")
        self.assertIn("CREATE TABLE public.audit_run ( branch character varying(255), disabled_sides character varying(20), id bigint NOT NULL )", saida)
        self.assertIn("CREATE TABLE public.system ( id bigint NOT NULL )", saida)

    def test_comando_que_nao_e_add_column_nao_muda_nada(self):
        self.assertEqual(V1, esquema._aplica_coluna(V1, "DROP INDEX idx_x"))

    def test_add_column_if_not_exists_dos_bancos_antigos_nao_entra(self):
        for comando in ("ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS x text",
                        "ALTER TABLE public.audit_run ADD COLUMN IF NOT EXISTS x text, ADD COLUMN IF NOT EXISTS y text"):
            self.assertEqual(V1, esquema._aplica_coluna(V1, comando))

    def test_migracoes_reais_v2_a_v8_so_acrescentam_as_colunas_do_v6_ao_v8(self):
        v1 = esquema.comandos(os.path.join(RAIZ, "audit-api/src/main/resources/db/migration/V1__baseline.sql"))
        depois = v1
        for nome in sorted(os.listdir(os.path.join(RAIZ, "audit-api/src/main/resources/db/migration")))[1:]:
            for c in esquema.comandos(os.path.join(RAIZ, "audit-api/src/main/resources/db/migration", nome)):
                depois = esquema._aplica_coluna(depois, c)
        novos = depois - v1
        # audit_run (V6), finding (V7), review_suggestion, rule_proposal e pending_action (V8)
        self.assertEqual(5, len(novos))
        self.assertTrue(any("disabled_sides character varying(20)" in c for c in novos))
        self.assertTrue(any("code_context character varying(4000)" in c and "code_context_start integer" in c for c in novos))
        for coluna in ("decided_at timestamp(6) with time zone", "previous_stage character varying(20)",
                       "result_suppression_id bigint", "undone_at timestamp(6) with time zone"):
            self.assertTrue(any(coluna in c for c in novos), coluna)

    def test_comandos_em_ordem_mantem_a_ordem_do_arquivo(self):
        sql = os.path.join(RAIZ, "audit-api/src/main/resources/db/migration/V7__contexto_do_codigo_no_achado.sql")
        ordem = esquema.comandos_em_ordem(sql)
        self.assertEqual(["code_context", "code_context_start"], [re.search(r"ADD COLUMN (\w+)", c).group(1) for c in ordem])

    def test_ordem_das_colunas_nao_conta(self):
        a = esquema._normaliza("CREATE TABLE public.t ( id bigint NOT NULL, b text, a character varying(5) )")
        b = esquema._normaliza("CREATE TABLE public.t ( id bigint NOT NULL, a character varying(5), b text )")
        self.assertEqual(a, b)
        c = esquema._normaliza("CREATE TABLE public.t ( id bigint NOT NULL, a character varying(5) )")
        self.assertEqual(a, esquema._aplica_coluna({c}, "ALTER TABLE public.t ADD COLUMN b text").pop())

    def test_migracao_v6_real_bate_com_o_formato_do_pg_dump(self):
        sql = os.path.join(RAIZ, "audit-api/src/main/resources/db/migration/V6__lados_desligados_na_auditoria.sql")
        comandos = esquema.comandos(sql)
        self.assertEqual(1, len(comandos))
        saida = esquema._aplica_coluna(V1, next(iter(comandos)))
        self.assertTrue(any("disabled_sides character varying(20)" in c for c in saida))


if __name__ == "__main__":
    unittest.main()
