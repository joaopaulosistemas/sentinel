"""Testes de scripts/verificar-regras.py: consistência de regras, perfis, CWEs e dedup."""
import importlib.util
import os
import tempfile
import textwrap
import unittest

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(AQUI)

_spec = importlib.util.spec_from_file_location("verificar_regras", os.path.join(AQUI, "verificar-regras.py"))
verificar_regras = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(verificar_regras)


def _escreve(raiz, caminho, texto):
    destino = os.path.join(raiz, caminho)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    with open(destino, "w", encoding="utf-8") as f:
        f.write(textwrap.dedent(texto))


def _regra(rid, cwe="CWE-89: x", categoria="security"):
    return f"""\
    rules:
      - id: {rid}
        message: m
        severity: WARNING
        languages: [java]
        metadata:
          category: {categoria}
          cwe: "{cwe}"
    """


class RepositorioReal(unittest.TestCase):
    def test_repositorio_sem_problemas(self):
        _, erros = verificar_regras.verificar(RAIZ)
        self.assertEqual([], erros)


class PerfisComOverrideMorto(unittest.TestCase):
    def test_override_sentinel_sem_regra_correspondente_e_erro(self):
        with tempfile.TemporaryDirectory() as raiz:
            _escreve(raiz, "audit-rules/packs/p/rules/a.yml", _regra("sentinel-existe"))
            _escreve(raiz, "audit-rules/packs/profiles/x.yml", """\
                id: x
                rules:
                  sentinel-nao-existe: {severity: low}
                """)
            _, erros = verificar_regras.verificar(raiz)
            self.assertTrue(any("sentinel-nao-existe" in e and "perfil" in e for e in erros), erros)

    def test_override_com_curinga_ou_id_existente_passa(self):
        with tempfile.TemporaryDirectory() as raiz:
            _escreve(raiz, "audit-rules/packs/p/rules/a.yml", _regra("sentinel-java-um"))
            _escreve(raiz, "audit-rules/packs/profiles/x.yml", """\
                id: x
                rules:
                  "sentinel-java-*": {shadow: true}
                  sentinel-java-um: {severity: low}
                  jscpd-codigo-duplicado: {shadow: true}
                """)
            _, erros = verificar_regras.verificar(raiz)
            self.assertEqual([], erros)

    def test_override_de_analisador_java_precisa_existir_no_codigo(self):
        with tempfile.TemporaryDirectory() as raiz:
            _escreve(raiz, "audit-rules/packs/p/rules/a.yml", _regra("sentinel-java-um"))
            _escreve(raiz, "audit-api/src/main/java/com/sentinel/audit/analyzer/A.java",
                     'class A { String id = "arch-god-object"; }\n')
            _escreve(raiz, "audit-rules/packs/profiles/x.yml", """\
                id: x
                rules:
                  arch-god-object: {severity: low}
                  arch-fantasma: {severity: low}
                """)
            _, erros = verificar_regras.verificar(raiz)
            self.assertEqual(1, len(erros), erros)
            self.assertIn("arch-fantasma", erros[0])


class CwesIncoerentes(unittest.TestCase):
    def test_cwe_sem_relacao_com_a_regra_e_erro(self):
        for cwe in ("CWE-1327: Binding to an Unrestricted IP Address",
                    "CWE-1060: Excessive Number of Inefficient Server-Side Data Accesses"):
            with self.subTest(cwe=cwe), tempfile.TemporaryDirectory() as raiz:
                _escreve(raiz, "audit-rules/packs/p/rules/a.yml", _regra("sentinel-qualquer", cwe, "quality"))
                _, erros = verificar_regras.verificar(raiz)
                self.assertTrue(any("sentinel-qualquer" in e and "CWE" in e for e in erros), erros)

    def test_cwe_1025_so_vale_para_comparacao(self):
        with tempfile.TemporaryDirectory() as raiz:
            _escreve(raiz, "audit-rules/packs/p/rules/a.yml",
                     _regra("sentinel-sql-delete-sem-where", "CWE-1025: Comparison Using Wrong Factors", "quality"))
            _, erros = verificar_regras.verificar(raiz)
            self.assertTrue(any("CWE-1025" in e for e in erros), erros)

    def test_mesma_imagem_latest_usa_o_mesmo_cwe_em_docker_e_k8s(self):
        cwes = {}
        import glob, yaml
        for f in glob.glob(os.path.join(RAIZ, "audit-rules/packs/core-iac/rules/*.yml")):
            for r in (yaml.safe_load(open(f, encoding="utf-8")) or {}).get("rules", []):
                if r["id"] in ("sentinel-docker-imagem-latest", "sentinel-k8s-imagem-latest"):
                    cwes[r["id"]] = r["metadata"]["cwe"].split(":")[0]
        self.assertEqual(2, len(cwes))
        self.assertEqual(1, len(set(cwes.values())), cwes)


if __name__ == "__main__":
    unittest.main()
