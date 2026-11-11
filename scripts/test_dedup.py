"""Testes de audit-rules/packs/*/dedup.yml: os pares que sabidamente apontam o mesmo defeito precisam cair no mesmo tópico.

Reproduz a regra de casamento do FindingDeduplicator (Java): tópico `order` casa por id igual ou terminado em
".<id>"; tópico `rules` casa por trecho do id em minúsculas.
"""
import glob
import os
import unittest

import yaml

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _topicos():
    saida = []
    for f in sorted(glob.glob(os.path.join(RAIZ, "audit-rules/packs/*/dedup.yml"))):
        saida += yaml.safe_load(open(f, encoding="utf-8")) or []
    return saida


def _topico_de(rule_id, topicos):
    rid = rule_id.lower()
    for t in topicos:
        if t.get("order"):
            if any(rid == i or rid.endswith("." + i) for i in t["order"]):
                return t["name"]
        elif any(m in rid for m in t.get("rules", [])):
            return t["name"]
    return None


class TopicosDeDeduplicacao(unittest.TestCase):
    PARES_MESMO_DEFEITO = [
        ("sentinel-sql-injection", "sentinel-taint-sql-parametro-http"),
        ("sentinel-sql-injection", "sentinel-quarkus-panache-consulta-concatenada"),
        ("sentinel-api-path-traversal", "sentinel-servlet-path-traversal"),
        ("sentinel-api-cors-wildcard", "sentinel-crossorigin-aberto"),
        ("sentinel-cert-throw-generico", "sentinel-ee-lanca-excecao-generica"),
        ("sentinel-codigo-comentado", "sentinel-cct-codigo-comentado"),
        ("sentinel-jpa-n-mais-um", "sentinel-perf-consulta-em-laco"),
        ("sentinel-java-thread-manual", "sentinel-mem-new-thread-solto"),
        ("sentinel-taint-html-parametro-url", "sentinel-xss-inner-html"),
        # Checkstyle devolve a chave da mensagem ("magic.number"), não o nome da classe.
        ("checkstyle-magic.number", "arch-magic-number"),
        ("checkstyle-magicnumbercheck", "arch-magic-number"),
    ]

    def test_pares_do_mesmo_defeito_caem_no_mesmo_topico(self):
        topicos = _topicos()
        for a, b in self.PARES_MESMO_DEFEITO:
            with self.subTest(par=(a, b)):
                ta, tb = _topico_de(a, topicos), _topico_de(b, topicos)
                self.assertIsNotNone(ta, f"{a} não está em nenhum tópico")
                self.assertEqual(ta, tb, f"{a} e {b} deveriam estar no mesmo tópico")

    def test_regras_distintas_nao_sao_juntadas(self):
        topicos = _topicos()
        distintos = [
            ("sentinel-sql-injection", "sentinel-sql-select-estrela"),
            ("sentinel-cert-thread-run-direto", "sentinel-java-thread-manual"),
            ("sentinel-api-path-traversal", "sentinel-mem-leitura-total-de-arquivo"),
        ]
        for a, b in distintos:
            with self.subTest(par=(a, b)):
                ta, tb = _topico_de(a, topicos), _topico_de(b, topicos)
                self.assertTrue(ta is None or ta != tb, f"{a} e {b} não são o mesmo defeito (ambos em {ta})")

    def test_regra_especifica_vem_antes_da_generica_em_cada_topico_order(self):
        por_nome = {t["name"]: t["order"] for t in _topicos() if t.get("order")}
        self.assertLess(por_nome["sql-injection-java"].index("sentinel-taint-sql-parametro-http"),
                        por_nome["sql-injection-java"].index("sentinel-sql-injection"))
        self.assertLess(por_nome["path-traversal-java"].index("sentinel-servlet-path-traversal"),
                        por_nome["path-traversal-java"].index("sentinel-api-path-traversal"))


if __name__ == "__main__":
    unittest.main()
