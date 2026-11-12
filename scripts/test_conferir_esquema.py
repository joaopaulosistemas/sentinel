"""Testes de scripts/conferir-esquema.py: a coluna acrescentada por uma migração depois do V1 conta como parte do esquema."""
import importlib.util
import os
import unittest

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_spec = importlib.util.spec_from_file_location("conferir_esquema", os.path.join(RAIZ, "scripts", "conferir-esquema.py"))
esquema = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(esquema)

V1 = {"CREATE TABLE public.audit_run ( id bigint NOT NULL, branch character varying(255) )",
      "CREATE TABLE public.system ( id bigint NOT NULL )"}


class ColunaNovaTest(unittest.TestCase):
    def test_coluna_entra_no_fim_do_create_table_da_tabela_certa(self):
        saida = esquema._aplica_coluna(V1, "ALTER TABLE public.audit_run ADD COLUMN disabled_sides varchar(20)")
        self.assertIn("CREATE TABLE public.audit_run ( id bigint NOT NULL, branch character varying(255), disabled_sides character varying(20) )", saida)
        self.assertIn("CREATE TABLE public.system ( id bigint NOT NULL )", saida)

    def test_comando_que_nao_e_add_column_nao_muda_nada(self):
        self.assertEqual(V1, esquema._aplica_coluna(V1, "DROP INDEX idx_x"))

    def test_migracao_v6_real_bate_com_o_formato_do_pg_dump(self):
        sql = os.path.join(RAIZ, "audit-api/src/main/resources/db/migration/V6__lados_desligados_na_auditoria.sql")
        comandos = esquema.comandos(sql)
        self.assertEqual(1, len(comandos))
        saida = esquema._aplica_coluna(V1, next(iter(comandos)))
        self.assertTrue(any("disabled_sides character varying(20)" in c for c in saida))


if __name__ == "__main__":
    unittest.main()
