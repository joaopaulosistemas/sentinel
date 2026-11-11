"""Testes de scripts/testar-pacote.py com pacotes sintéticos (precisam do Semgrep; sem ele são pulados)."""
import importlib.util
import os
import shutil
import tempfile
import textwrap
import unittest

AQUI = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("testar_pacote", os.path.join(AQUI, "testar-pacote.py"))
testar_pacote = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(testar_pacote)

REGRA_JAVA = """\
rules:
  - id: sentinel-x-exit
    pattern: System.exit(...)
    message: m
    severity: WARNING
    languages: [java]
"""
REGRA_YAML = """\
rules:
  - id: sentinel-x-privileged
    pattern-regex: '(?m)^[ \\t]*privileged:[ \\t]*true\\b'
    message: m
    severity: ERROR
    languages: [regex]
    paths:
      include: ["*.yml", "*.yaml"]
"""
TESTE_JAVA = """\
class A {
    void m() {
        // ruleid: sentinel-x-exit
        System.exit(1);
        // ok: sentinel-x-exit
        run();
    }
}
"""


def _pacote(raiz, regras, testes):
    pacote = os.path.join(raiz, "pacote")
    for pasta, arquivos in (("rules", regras), ("tests", testes)):
        os.makedirs(os.path.join(pacote, pasta), exist_ok=True)
        for nome, texto in arquivos.items():
            with open(os.path.join(pacote, pasta, nome), "w", encoding="utf-8") as f:
                f.write(textwrap.dedent(texto))
    return pacote


@unittest.skipUnless(shutil.which("semgrep"), "semgrep não instalado")
class RunnerDePacote(unittest.TestCase):
    def verifica(self, regras, testes):
        with tempfile.TemporaryDirectory() as raiz:
            return testar_pacote.verifica(_pacote(raiz, regras, testes))

    def test_pacote_completo_passa(self):
        self.assertEqual([], self.verifica({"a.yml": REGRA_JAVA}, {"a.java": TESTE_JAVA}))

    def test_teste_orfao_sem_arquivo_de_regras_de_mesmo_nome_e_executado(self):
        # `orfao.java` não tem `orfao.yml`: o `semgrep --test` puro ignorava o arquivo em silêncio
        self.assertEqual([], self.verifica({"a.yml": REGRA_JAVA}, {"orfao.java": TESTE_JAVA}))

    def test_teste_orfao_que_falha_e_apontado(self):
        errado = TESTE_JAVA.replace("System.exit(1);", "run();")      # `ruleid:` numa linha que a regra não marca
        problemas = self.verifica({"a.yml": REGRA_JAVA}, {"orfao.java": errado})
        self.assertTrue(any("teste falhou" in p for p in problemas), problemas)

    def test_regra_sem_nenhum_teste_e_apontada(self):
        problemas = self.verifica({"a.yml": REGRA_JAVA}, {"outra.java": "class B {}\n"})
        self.assertTrue(any("sentinel-x-exit" in p and "não foi executada" in p for p in problemas), problemas)

    def test_regra_sem_caso_ok_e_apontada(self):
        so_positivo = "class A {\n    void m() {\n        // ruleid: sentinel-x-exit\n        System.exit(1);\n    }\n}\n"
        problemas = self.verifica({"a.yml": REGRA_JAVA}, {"a.java": so_positivo})
        self.assertTrue(any("sentinel-x-exit" in p and "`ok:`" in p for p in problemas), problemas)

    def test_alvo_yaml_e_executado_como_test_yaml(self):
        positivo = "services:\n  app:\n    # ruleid: sentinel-x-privileged\n    privileged: true\n"
        negativo = "services:\n  app:\n    # ok: sentinel-x-privileged\n    privileged: false\n"
        self.assertEqual([], self.verifica({"c.yml": REGRA_YAML}, {"c.yml": positivo, "c.negativos.yml": negativo}))

    def test_arquivo_so_com_ok_que_casa_a_regra_e_apontado(self):
        positivo = "services:\n  app:\n    # ruleid: sentinel-x-privileged\n    privileged: true\n"
        errado = "services:\n  app:\n    # ok: sentinel-x-privileged\n    privileged: true\n"
        problemas = self.verifica({"c.yml": REGRA_YAML}, {"c.yml": positivo, "c.negativos.yml": errado})
        self.assertTrue(any("só tem casos `ok:`" in p for p in problemas), problemas)

    def test_arquivo_so_com_ok_correto_conta_como_caso_negativo(self):
        so_ok = "class A {\n    void m() {\n        // ok: sentinel-x-exit\n        run();\n    }\n}\n"
        positivo = "class A {\n    void m() {\n        // ruleid: sentinel-x-exit\n        System.exit(1);\n    }\n}\n"
        self.assertEqual([], self.verifica({"a.yml": REGRA_JAVA}, {"a.java": positivo, "a.negativos.java": so_ok}))


if __name__ == "__main__":
    unittest.main()
