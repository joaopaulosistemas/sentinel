"""O checks.xml fixo do Checkstyle não pode repetir o que o CleanCode já mede com os limiares do perfil.

Com limiares próprios (60 linhas, 7 parâmetros, complexidade 12) o Checkstyle apontava a mesma função grande que o
CleanCode (50/5/10) já apontava, e um perfil que afrouxa o CleanCode (ex.: legado) continuava recebendo o aviso.
"""
import os
import unittest
import xml.etree.ElementTree as ET

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHECKS = os.path.join(RAIZ, "audit-rules/lint/checkstyle/checks.xml")
MEDIDOS_PELO_CLEANCODE = {"MethodLength", "ParameterNumber", "CyclomaticComplexity"}


def _modulos():
    return {m.get("name") for m in ET.parse(CHECKS).getroot().iter("module")}


class ChecksXml(unittest.TestCase):
    def test_xml_valido_e_com_modulos(self):
        self.assertIn("TreeWalker", _modulos())

    def test_nao_repete_metricas_do_cleancode(self):
        repetidos = _modulos() & MEDIDOS_PELO_CLEANCODE
        self.assertEqual(set(), repetidos,
                         f"o CleanCode já mede {sorted(repetidos)} com os limiares do perfil; tire do checks.xml")

    def test_mantem_as_verificacoes_que_o_cleancode_nao_faz(self):
        self.assertTrue({"EqualsHashCode", "NestedIfDepth", "MagicNumber"} <= _modulos())


if __name__ == "__main__":
    unittest.main()
