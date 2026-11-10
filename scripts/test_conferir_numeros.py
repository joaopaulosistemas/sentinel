import importlib.util
import tempfile
import unittest
from pathlib import Path

_spec = importlib.util.spec_from_file_location("conferir_numeros", Path(__file__).with_name("conferir-numeros.py"))
conferir = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(conferir)


def monta(raiz: Path, regras: int, readme: str, pacotes_doc: str = "") -> None:
    p = raiz / "audit-rules/packs"
    (p / "core-a/rules").mkdir(parents=True)
    (p / "core-a/pack.yml").write_text("id: core-a\n")
    (p / "core-a/rules/r.yml").write_text("rules:\n" + "".join(f"  - id: r{i}\n" for i in range(regras)))
    (p / "market-tools/tools").mkdir(parents=True)
    (p / "market-tools/pack.yml").write_text("id: market-tools\n")
    (p / "market-tools/tools/x.yml").write_text("- id: jscpd\n  name: jscpd\n- id: joern\n  name: Joern\n")
    (p / "profiles").mkdir()
    (p / "profiles/p.yml").write_text("id: p\n")
    (raiz / "docs/referencia").mkdir(parents=True)
    (raiz / "README.md").write_text(readme)
    (raiz / "docs/IMPACTO.md").write_text(readme)
    (raiz / "docs/referencia/pacotes-de-regras.md").write_text(pacotes_doc)


RESUMO = ("2 pacotes declarativos em `audit-rules/packs` (3 regras próprias), "
          "1 ferramentas de mercado via SARIF (mais o Joern, opcional) e 1 perfis")


class ConferirNumerosTest(unittest.TestCase):
    def test_contagens_vem_do_codigo(self):
        with tempfile.TemporaryDirectory() as d:
            raiz = Path(d)
            monta(raiz, 3, RESUMO)
            self.assertEqual({"core-a": 3, "market-tools": 0}, conferir.regras_por_pacote(raiz))
            self.assertEqual(["jscpd", "joern"], conferir.ferramentas_de_mercado(raiz))
            self.assertEqual(1, conferir.perfis(raiz))
            self.assertEqual([], conferir.divergencias(raiz))

    def test_resumo_desatualizado_e_apontado(self):
        with tempfile.TemporaryDirectory() as d:
            raiz = Path(d)
            monta(raiz, 4, RESUMO)
            achados = conferir.divergencias(raiz)
            self.assertEqual(2, len(achados))
            self.assertTrue(all("4 regras próprias" in a for a in achados))

    def test_checkov_nao_cobre_terraform(self):
        with tempfile.TemporaryDirectory() as d:
            raiz = Path(d)
            monta(raiz, 3, RESUMO + "\nTerraform (Checkov)")
            self.assertEqual(2, len(conferir.divergencias(raiz)))

    def test_numero_por_pacote_na_documentacao(self):
        with tempfile.TemporaryDirectory() as d:
            raiz = Path(d)
            monta(raiz, 3, RESUMO, "`core-a` (60 regras)")
            achados = conferir.divergencias(raiz)
            self.assertEqual(1, len(achados))
            self.assertIn("diz 60 regras, o código tem 3", achados[0])


if __name__ == "__main__":
    unittest.main()
